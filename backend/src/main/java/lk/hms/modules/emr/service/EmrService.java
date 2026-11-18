package lk.hms.modules.emr.service;

import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.sequence.SequenceService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.appointments.entity.Appointment;
import lk.hms.modules.departments.entity.Department;
import lk.hms.modules.departments.repository.DepartmentRepository;
import lk.hms.modules.doctors.entity.Doctor;
import lk.hms.modules.doctors.repository.DoctorRepository;
import lk.hms.modules.emr.dto.EmrDtos.*;
import lk.hms.modules.emr.entity.*;
import lk.hms.modules.emr.repository.*;
import lk.hms.modules.patients.entity.Patient;
import lk.hms.modules.patients.repository.PatientRepository;
import lk.hms.modules.pharmacy.entity.Medicine;
import lk.hms.modules.pharmacy.repository.MedicineRepository;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Electronic medical record (docs/01 §2E). Writes need an OPEN encounter and, for doctor
 * entries, the treating doctor. Signed notes are immutable. Prescribing a medicine that matches
 * an active drug allergy is refused unless the doctor records an override reason.
 */
@Service
public class EmrService {

    private final EncounterRepository encounters;
    private final DiagnosisRepository diagnoses;
    private final ClinicalNoteRepository notes;
    private final VitalSignRepository vitals;
    private final AllergyRepository allergies;
    private final PrescriptionRepository prescriptions;
    private final MedicineRepository medicines;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final EmrAccess access;
    private final SequenceService sequences;
    private final AuditService audit;
    private final HospitalTime time;

    public EmrService(EncounterRepository encounters, DiagnosisRepository diagnoses, ClinicalNoteRepository notes,
                      VitalSignRepository vitals, AllergyRepository allergies, PrescriptionRepository prescriptions,
                      MedicineRepository medicines, PatientRepository patients, DoctorRepository doctors,
                      DepartmentRepository departments, UserRepository users, EmrAccess access,
                      SequenceService sequences, AuditService audit, HospitalTime time) {
        this.encounters = encounters;
        this.diagnoses = diagnoses;
        this.notes = notes;
        this.vitals = vitals;
        this.allergies = allergies;
        this.prescriptions = prescriptions;
        this.medicines = medicines;
        this.patients = patients;
        this.doctors = doctors;
        this.departments = departments;
        this.users = users;
        this.access = access;
        this.sequences = sequences;
        this.audit = audit;
        this.time = time;
    }

    // ================= encounter lifecycle (called by appointments) =================

    @Transactional
    public Encounter openForAppointment(Appointment a) {
        return encounters.findByAppointmentId(a.getId()).orElseGet(() -> {
            Encounter e = new Encounter();
            e.setEncounterNo(sequences.nextFormatted("ENC", time.today().getYear(), 6));
            e.setPatientId(a.getPatient().getId());
            e.setDoctorId(a.getDoctor().getId());
            e.setDepartmentId(a.getDepartment().getId());
            e.setType(Encounter.Type.OPD);
            e.setAppointmentId(a.getId());
            e.setChiefComplaint(a.getReason());
            e.setStartedAt(time.now());
            return encounters.saveAndFlush(e);
        });
    }

    /** A consultation cannot be completed without at least one diagnosis on record. */
    @Transactional
    public void closeForAppointment(Appointment a) {
        Encounter e = encounters.findByAppointmentId(a.getId())
                .orElseThrow(() -> BusinessException.conflict("NO_ENCOUNTER", "This consultation has no open medical record."));
        if (diagnoses.countByEncounterId(e.getId()) == 0) {
            throw BusinessException.conflict("DIAGNOSIS_REQUIRED", "Record at least one diagnosis before completing the consultation.");
        }
        e.setStatus(Encounter.Status.CLOSED);
        e.setClosedAt(time.now());
    }

    /** Inpatient stays get one encounter for the whole admission, under the attending doctor. */
    @Transactional
    public Encounter openForAdmission(Long admissionId, Long patientId, Long doctorId, Long departmentId, String reason) {
        Encounter e = new Encounter();
        e.setEncounterNo(sequences.nextFormatted("ENC", time.today().getYear(), 6));
        e.setPatientId(patientId);
        e.setDoctorId(doctorId);
        e.setDepartmentId(departmentId);
        e.setType(Encounter.Type.INPATIENT);
        e.setAdmissionId(admissionId);
        e.setChiefComplaint(reason.length() > 500 ? reason.substring(0, 500) : reason);
        e.setStartedAt(time.now());
        return encounters.saveAndFlush(e);
    }

    @Transactional
    public void closeForAdmission(Long admissionId) {
        encounters.findFirstByAdmissionIdAndStatus(admissionId, Encounter.Status.OPEN).ifPresent(e -> {
            e.setStatus(Encounter.Status.CLOSED);
            e.setClosedAt(time.now());
        });
    }

    // ================= reads =================

    @Transactional
    public EmrView patientRecord(Long patientId, String reason) {
        Patient p = patients.findById(patientId).orElseThrow(() -> BusinessException.notFound("Patient", patientId));
        access.checkRead(patientId, reason);
        List<Encounter> list = encounters.findByPatientIdOrderByStartedAtDesc(patientId).stream().limit(50).toList();
        return new EmrView(p.getId(), p.getMrn(), p.getFullName(),
                allergies.findByPatientIdOrderByStatusAscIdDesc(patientId).stream().map(EmrService::allergyView).toList(),
                vitalsViews(vitals.findTop20ByPatientIdOrderByRecordedAtDesc(patientId)),
                encounterViews(list));
    }

    @Transactional
    public EncounterView encounter(Long encounterId, String reason) {
        Encounter e = load(encounterId);
        access.checkRead(e.getPatientId(), reason);
        return encounterViews(List.of(e)).get(0);
    }

    @Transactional(readOnly = true)
    public List<MedicineOption> medicineOptions(String q) {
        String like = "%" + (q == null ? "" : q.trim().toLowerCase(Locale.ROOT)) + "%";
        return medicines.searchActive(like, PageRequest.of(0, 20)).stream()
                .map(m -> new MedicineOption(m.getId(), m.getCode(), m.getDisplayName(), m.getDosageForm(), m.getUnit(), m.getUnitPrice()))
                .toList();
    }

    // ================= doctor entries =================

    @Transactional
    public DiagnosisView addDiagnosis(Long encounterId, DiagnosisRequest r) {
        Encounter e = writableByTreatingDoctor(encounterId);
        Diagnosis d = new Diagnosis();
        d.setEncounterId(e.getId());
        d.setPatientId(e.getPatientId());
        d.setIcd10Code(r.icd10Code() == null || r.icd10Code().isBlank() ? null : r.icd10Code().trim());
        d.setDescription(r.description().trim());
        d.setType(r.type());
        d.setCertainty(r.certainty());
        d.setDiagnosedBy(e.getDoctorId());
        diagnoses.saveAndFlush(d);
        audit.recordChange("CREATE", "ENCOUNTER", e.getId(), "Diagnosis: " + d.getDescription(), null, null);
        return new DiagnosisView(d.getId(), d.getIcd10Code(), d.getDescription(), d.getType(), d.getCertainty(), d.getCreatedAt());
    }

    @Transactional
    public NoteView addDoctorNote(Long encounterId, NoteRequest r) {
        Encounter e = writableByTreatingDoctor(encounterId);
        return noteView(saveNote(e, r.noteType(), r.content(), null));
    }

    @Transactional
    public NoteView addNursingNote(Long encounterId, TextRequest r) {
        Encounter e = load(encounterId);
        requireOpen(e);
        if (!access.hasCareRelationship(e.getPatientId())) {
            throw BusinessException.forbidden("NOT_YOUR_PATIENT", "Nursing notes can only be added for patients currently in your care.");
        }
        return noteView(saveNote(e, "NURSING_NOTE", r.content(), null));
    }

    @Transactional
    public NoteView editNote(Long noteId, TextRequest r) {
        ClinicalNote n = ownUnsignedNote(noteId);
        n.setContent(r.content().trim());
        notes.saveAndFlush(n);
        audit.recordChange("UPDATE", "ENCOUNTER", n.getEncounterId(), "Edited unsigned note #" + n.getId(), null, null);
        return noteView(n);
    }

    @Transactional
    public NoteView signNote(Long noteId) {
        ClinicalNote n = ownUnsignedNote(noteId);
        n.setSignedAt(time.now());
        notes.saveAndFlush(n);
        audit.recordChange("SIGN", "ENCOUNTER", n.getEncounterId(), "Signed note #" + n.getId(), null, null);
        return noteView(n);
    }

    /** Corrections to signed notes; allowed after the encounter is closed, by any clinician with write access. */
    @Transactional
    public NoteView addAddendum(Long noteId, TextRequest r) {
        ClinicalNote parent = notes.findById(noteId).orElseThrow(() -> BusinessException.notFound("Note", noteId));
        if (parent.getSignedAt() == null) {
            throw BusinessException.conflict("NOTE_NOT_SIGNED", "Edit the note directly; addenda are for signed notes.");
        }
        if (!access.hasCareRelationship(parent.getPatientId())) {
            throw BusinessException.forbidden("NOT_YOUR_PATIENT", "Only clinicians caring for this patient can add an addendum.");
        }
        ClinicalNote a = saveNote(load(parent.getEncounterId()), "ADDENDUM", r.content(), parent.getId());
        a.setSignedAt(time.now());
        return noteView(a);
    }

    // ================= vitals & allergies =================

    @Transactional
    public VitalsView recordVitals(Long patientId, VitalsRequest r) {
        requirePatient(patientId);
        if (Arrays.asList(r.temperatureC(), r.pulseBpm(), r.systolicMmhg(), r.diastolicMmhg(), r.respiratoryRate(),
                r.spo2Percent(), r.weightKg(), r.heightCm(), r.bloodGlucoseMmol()).stream().allMatch(Objects::isNull)) {
            throw BusinessException.badRequest("EMPTY_VITALS", "Enter at least one measurement.");
        }
        if ((r.systolicMmhg() == null) != (r.diastolicMmhg() == null)) {
            throw BusinessException.badRequest("INCOMPLETE_BP", "Enter both systolic and diastolic blood pressure.");
        }
        if (r.systolicMmhg() != null && r.systolicMmhg() <= r.diastolicMmhg()) {
            throw BusinessException.badRequest("INVALID_BP", "Systolic pressure must be higher than diastolic.");
        }
        if (r.encounterId() != null) {
            Encounter e = load(r.encounterId());
            if (!e.getPatientId().equals(patientId)) {
                throw BusinessException.badRequest("ENCOUNTER_MISMATCH", "That encounter belongs to another patient.");
            }
        }
        VitalSign v = new VitalSign();
        v.setPatientId(patientId);
        v.setEncounterId(r.encounterId());
        v.setRecordedBy(CurrentUser.require().getId());
        v.setRecordedAt(time.now());
        v.setTemperatureC(r.temperatureC());
        v.setPulseBpm(r.pulseBpm());
        v.setSystolicMmhg(r.systolicMmhg());
        v.setDiastolicMmhg(r.diastolicMmhg());
        v.setRespiratoryRate(r.respiratoryRate());
        v.setSpo2Percent(r.spo2Percent());
        v.setWeightKg(r.weightKg());
        v.setHeightCm(r.heightCm());
        v.setBloodGlucoseMmol(r.bloodGlucoseMmol());
        v.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
        vitals.saveAndFlush(v);
        audit.recordChange("CREATE", "PATIENT", patientId, "Vital signs recorded", null, null);
        return vitalsViews(List.of(v)).get(0);
    }

    @Transactional
    public AllergyView addAllergy(Long patientId, AllergyRequest r) {
        requirePatient(patientId);
        String allergen = r.allergen().trim();
        if (allergies.existsByPatientIdAndAllergenTypeAndAllergenIgnoreCase(patientId, r.allergenType(), allergen)) {
            throw BusinessException.conflict("ALLERGY_EXISTS", allergen + " is already recorded for this patient.");
        }
        Allergy a = new Allergy();
        a.setPatientId(patientId);
        a.setAllergenType(r.allergenType());
        a.setAllergen(allergen);
        a.setReaction(r.reaction() == null || r.reaction().isBlank() ? null : r.reaction().trim());
        a.setSeverity(r.severity());
        a.setRecordedBy(CurrentUser.require().getId());
        allergies.saveAndFlush(a);
        audit.recordChange("CREATE", "PATIENT", patientId, "Allergy recorded: " + allergen + " (" + r.severity() + ")", null, null);
        return allergyView(a);
    }

    @Transactional
    public AllergyView resolveAllergy(Long allergyId) {
        Allergy a = allergies.findById(allergyId).orElseThrow(() -> BusinessException.notFound("Allergy", allergyId));
        a.setStatus("RESOLVED");
        audit.recordChange("STATUS_CHANGE", "PATIENT", a.getPatientId(), "Allergy marked resolved: " + a.getAllergen(), null, null);
        return allergyView(a);
    }

    // ================= prescriptions =================

    @Transactional
    public PrescriptionView prescribe(Long encounterId, PrescriptionRequest r) {
        Encounter e = writableByTreatingDoctor(encounterId);
        Map<Long, Medicine> meds = medicines.findAllById(r.items().stream().map(PrescriptionItemRequest::medicineId).toList())
                .stream().collect(Collectors.toMap(Medicine::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (PrescriptionItemRequest i : r.items()) {
            Medicine m = meds.get(i.medicineId());
            if (m == null || !m.isActive()) {
                throw BusinessException.badRequest("UNKNOWN_MEDICINE", "Medicine #" + i.medicineId() + " is not available for prescribing.");
            }
            if (!seen.add(i.medicineId())) {
                throw BusinessException.badRequest("DUPLICATE_MEDICINE", m.getDisplayName() + " is listed twice.");
            }
        }
        List<String> conflicts = allergyConflicts(e.getPatientId(), meds.values());
        String override = r.allergyOverrideReason() == null ? null : r.allergyOverrideReason().trim();
        if (!conflicts.isEmpty() && (override == null || override.length() < 10)) {
            throw BusinessException.conflict("ALLERGY_CONFLICT", "Recorded drug allergy: " + String.join("; ", conflicts)
                    + ". To prescribe anyway, give a clinical reason (at least 10 characters).");
        }

        Prescription p = new Prescription();
        p.setPrescriptionNo(sequences.nextFormatted("RX", time.today().getYear(), 6));
        p.setEncounterId(e.getId());
        p.setPatientId(e.getPatientId());
        p.setDoctorId(e.getDoctorId());
        p.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
        p.setAllergyOverrideReason(conflicts.isEmpty() ? null : override);
        for (PrescriptionItemRequest i : r.items()) {
            PrescriptionItem item = new PrescriptionItem();
            item.setPrescription(p);
            item.setMedicineId(i.medicineId());
            item.setDose(i.dose().trim());
            item.setFrequency(i.frequency().trim());
            item.setRoute(i.route());
            item.setDurationDays(i.durationDays());
            item.setQuantityPrescribed(i.quantity());
            item.setInstructions(i.instructions() == null || i.instructions().isBlank() ? null : i.instructions().trim());
            p.getItems().add(item);
        }
        prescriptions.saveAndFlush(p);
        audit.recordChange("CREATE", "ENCOUNTER", e.getId(), "Prescription " + p.getPrescriptionNo()
                + (conflicts.isEmpty() ? "" : " (allergy override: " + override + ")"), null, null);
        return prescriptionViews(List.of(p)).get(0);
    }

    @Transactional
    public PrescriptionView cancelPrescription(Long prescriptionId) {
        Prescription p = prescriptions.findById(prescriptionId).orElseThrow(() -> BusinessException.notFound("Prescription", prescriptionId));
        Doctor me = access.callerDoctor().orElseThrow(() -> BusinessException.forbidden("NOT_A_DOCTOR", "Your account is not linked to a doctor."));
        if (!p.getDoctorId().equals(me.getId())) {
            throw BusinessException.forbidden("NOT_YOUR_PRESCRIPTION", "Only the prescribing doctor can cancel this prescription.");
        }
        if (p.getStatus() != Prescription.Status.ACTIVE) {
            throw BusinessException.conflict("PRESCRIPTION_NOT_ACTIVE", "Only an undispensed prescription can be cancelled.");
        }
        p.setStatus(Prescription.Status.CANCELLED);
        audit.recordChange("STATUS_CHANGE", "ENCOUNTER", p.getEncounterId(), "Cancelled prescription " + p.getPrescriptionNo(), null, null);
        return prescriptionViews(List.of(p)).get(0);
    }

    /** Factual match of drug allergen against the generic name (either contains the other), not a clinical judgement. */
    private List<String> allergyConflicts(Long patientId, Collection<Medicine> meds) {
        List<String> out = new ArrayList<>();
        for (Allergy a : allergies.findByPatientIdAndAllergenTypeAndStatus(patientId, "DRUG", "ACTIVE")) {
            String allergen = a.getAllergen().toLowerCase(Locale.ROOT);
            for (Medicine m : meds) {
                String generic = m.getGenericName().toLowerCase(Locale.ROOT);
                if (generic.contains(allergen) || allergen.contains(generic)) {
                    out.add(a.getAllergen() + " (" + a.getSeverity().toLowerCase(Locale.ROOT) + ") matches " + m.getGenericName());
                }
            }
        }
        return out;
    }

    // ================= helpers =================

    private Encounter load(Long id) {
        return encounters.findById(id).orElseThrow(() -> BusinessException.notFound("Encounter", id));
    }

    private void requirePatient(Long id) {
        if (!patients.existsById(id)) {
            throw BusinessException.notFound("Patient", id);
        }
    }

    private static void requireOpen(Encounter e) {
        if (e.getStatus() != Encounter.Status.OPEN) {
            throw BusinessException.conflict("ENCOUNTER_CLOSED", "This encounter is closed. Use an addendum to correct a signed note.");
        }
    }

    private Encounter writableByTreatingDoctor(Long encounterId) {
        Encounter e = load(encounterId);
        requireOpen(e);
        boolean treating = access.callerDoctor().map(d -> d.getId().equals(e.getDoctorId())).orElse(false);
        if (!treating) {
            throw BusinessException.forbidden("NOT_TREATING_DOCTOR", "Only the treating doctor can write to this encounter.");
        }
        return e;
    }

    private ClinicalNote ownUnsignedNote(Long noteId) {
        ClinicalNote n = notes.findById(noteId).orElseThrow(() -> BusinessException.notFound("Note", noteId));
        if (!n.getAuthorUserId().equals(CurrentUser.require().getId())) {
            throw BusinessException.forbidden("NOT_AUTHOR", "Only the author can change this note.");
        }
        if (n.getSignedAt() != null) {
            throw BusinessException.conflict("NOTE_SIGNED", "Signed notes cannot be changed. Add an addendum instead.");
        }
        return n;
    }

    private ClinicalNote saveNote(Encounter e, String type, String content, Long parentId) {
        ClinicalNote n = new ClinicalNote();
        n.setEncounterId(e.getId());
        n.setPatientId(e.getPatientId());
        n.setNoteType(type);
        n.setContent(content.trim());
        n.setAuthorUserId(CurrentUser.require().getId());
        n.setParentNoteId(parentId);
        notes.saveAndFlush(n);
        audit.recordChange("CREATE", "ENCOUNTER", e.getId(), type.replace('_', ' ').toLowerCase(Locale.ROOT) + " added", null, null);
        return n;
    }

    // ================= view mapping (batched lookups, no N+1) =================

    private List<EncounterView> encounterViews(List<Encounter> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Encounter::getId).toList();
        Map<Long, List<DiagnosisView>> dx = diagnoses.findByEncounterIdInOrderByIdAsc(ids).stream().collect(Collectors.groupingBy(
                Diagnosis::getEncounterId, Collectors.mapping(d -> new DiagnosisView(d.getId(), d.getIcd10Code(), d.getDescription(),
                        d.getType(), d.getCertainty(), d.getCreatedAt()), Collectors.toList())));
        List<ClinicalNote> allNotes = notes.findByEncounterIdInOrderByIdAsc(ids);
        Map<Long, String> names = userNames(allNotes.stream().map(ClinicalNote::getAuthorUserId).toList());
        Map<Long, List<NoteView>> nv = allNotes.stream().collect(Collectors.groupingBy(ClinicalNote::getEncounterId,
                Collectors.mapping(n -> noteView(n, names), Collectors.toList())));
        Map<Long, List<PrescriptionView>> rx = prescriptionViews(prescriptions.findByEncounterIdInOrderByIdAsc(ids)).stream()
                .collect(Collectors.groupingBy(PrescriptionView::encounterId));
        Map<Long, String> doctorNames = doctorNames(list.stream().map(Encounter::getDoctorId).toList());
        Map<Long, String> deptNames = departments.findAllById(list.stream().map(Encounter::getDepartmentId).distinct().toList())
                .stream().collect(Collectors.toMap(Department::getId, Department::getName));
        Long myDoctorId = access.callerDoctor().map(Doctor::getId).orElse(null);
        return list.stream().map(e -> new EncounterView(e.getId(), e.getEncounterNo(), e.getType().name(), e.getStatus().name(),
                e.getAppointmentId(), e.getDoctorId(), doctorNames.get(e.getDoctorId()), deptNames.get(e.getDepartmentId()),
                e.getChiefComplaint(), e.getStartedAt(), e.getClosedAt(),
                e.getStatus() == Encounter.Status.OPEN && e.getDoctorId().equals(myDoctorId),
                dx.getOrDefault(e.getId(), List.of()), nv.getOrDefault(e.getId(), List.of()), rx.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    private List<PrescriptionView> prescriptionViews(List<Prescription> list) {
        Map<Long, Medicine> meds = medicines.findAllById(list.stream().flatMap(p -> p.getItems().stream())
                .map(PrescriptionItem::getMedicineId).distinct().toList()).stream()
                .collect(Collectors.toMap(Medicine::getId, Function.identity()));
        Map<Long, String> doctorNames = doctorNames(list.stream().map(Prescription::getDoctorId).toList());
        return list.stream().map(p -> new PrescriptionView(p.getId(), p.getEncounterId(), p.getPrescriptionNo(), p.getStatus().name(), p.getNotes(),
                    p.getAllergyOverrideReason(), doctorNames.get(p.getDoctorId()), p.getCreatedAt(),
                    p.getItems().stream().map(i -> new PrescriptionItemView(i.getId(), i.getMedicineId(),
                            Optional.ofNullable(meds.get(i.getMedicineId())).map(Medicine::getDisplayName).orElse("Medicine #" + i.getMedicineId()),
                            i.getDose(), i.getFrequency(), i.getRoute(), i.getDurationDays(), i.getQuantityPrescribed(),
                            i.getQuantityDispensed(), i.getInstructions())).toList())).toList();
    }

    private List<VitalsView> vitalsViews(List<VitalSign> list) {
        Map<Long, String> names = userNames(list.stream().map(VitalSign::getRecordedBy).toList());
        return list.stream().map(v -> new VitalsView(v.getId(), v.getEncounterId(), v.getRecordedAt(), names.get(v.getRecordedBy()),
                v.getTemperatureC(), v.getPulseBpm(), v.getSystolicMmhg(), v.getDiastolicMmhg(), v.getRespiratoryRate(),
                v.getSpo2Percent(), v.getWeightKg(), v.getHeightCm(), bmi(v.getWeightKg(), v.getHeightCm()),
                v.getBloodGlucoseMmol(), v.getNotes())).toList();
    }

    static BigDecimal bmi(BigDecimal kg, BigDecimal cm) {
        if (kg == null || cm == null || cm.signum() == 0) {
            return null;
        }
        BigDecimal m = cm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return kg.divide(m.multiply(m), 1, RoundingMode.HALF_UP);
    }

    private NoteView noteView(ClinicalNote n) {
        return noteView(n, userNames(List.of(n.getAuthorUserId())));
    }

    private static NoteView noteView(ClinicalNote n, Map<Long, String> names) {
        boolean editable = n.getSignedAt() == null && n.getAuthorUserId().equals(CurrentUser.require().getId());
        return new NoteView(n.getId(), n.getNoteType(), n.getContent(), n.getAuthorUserId(), names.get(n.getAuthorUserId()),
                n.getParentNoteId(), n.getSignedAt(), n.getCreatedAt(), editable);
    }

    private static AllergyView allergyView(Allergy a) {
        return new AllergyView(a.getId(), a.getAllergenType(), a.getAllergen(), a.getReaction(), a.getSeverity(), a.getStatus());
    }

    private Map<Long, String> userNames(List<Long> ids) {
        return users.findAllById(ids.stream().distinct().toList()).stream().collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private Map<Long, String> doctorNames(List<Long> ids) {
        return doctors.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Doctor::getId, Doctor::getDisplayName));
    }

}
