package lk.hms.modules.patients.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.sequence.SequenceService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.patients.dto.PatientDtos.DuplicateCandidate;
import lk.hms.modules.patients.dto.PatientDtos.DuplicateCheckRequest;
import lk.hms.modules.patients.dto.PatientDtos.EmergencyContactDto;
import lk.hms.modules.patients.dto.PatientDtos.PatientDetail;
import lk.hms.modules.patients.dto.PatientDtos.PatientRequest;
import lk.hms.modules.patients.dto.PatientDtos.PatientSummary;
import lk.hms.modules.patients.dto.PatientDtos.StatusRequest;
import lk.hms.modules.patients.entity.EmergencyContact;
import lk.hms.modules.patients.entity.Patient;
import lk.hms.modules.patients.repository.PatientRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Patient registration rules (docs/01 §2B):
 * <ul>
 *   <li>MRN HMS-YYYY-NNNNNN from a row-locked sequence (gap-free, never reused).</li>
 *   <li>NIC and passport are unique: a match is a hard stop (409) naming the existing MRN.</li>
 *   <li>Same name + date of birth, or same name + phone, is a <em>possible</em> duplicate: the
 *       receptionist must confirm "not a duplicate" before saving.</li>
 *   <li>Records are never deleted; status changes (inactive, deceased) need a reason and are audited.</li>
 * </ul>
 */
@Service
public class PatientService {

    private static final Map<String, String> SORTS = Map.of(
            "mrn", "mrn", "name", "lastName", "dateOfBirth", "dateOfBirth", "createdAt", "createdAt");

    private final PatientRepository patients;
    private final SequenceService sequences;
    private final AuditService audit;
    private final HospitalTime time;

    public PatientService(PatientRepository patients, SequenceService sequences, AuditService audit, HospitalTime time) {
        this.patients = patients;
        this.sequences = sequences;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientSummary> search(String q, String status, int page, int size, String sort) {
        String term = q == null || q.isBlank() ? null : q.trim();
        String exact = term == null ? null : normaliseNic(term);
        String like = term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
        Patient.Status st = status == null || status.isBlank() ? null : parseStatus(status);
        LocalDate today = time.today();
        return PageResponse.of(patients.search(exact, like, st,
                        Paging.of(page, size, sort, SORTS, Sort.by(Sort.Direction.DESC, "createdAt"))),
                p -> PatientSummary.from(p, today));
    }

    @Transactional(readOnly = true)
    public PatientDetail get(Long id) {
        return PatientDetail.from(load(id), time.today());
    }

    @Transactional(readOnly = true)
    public List<DuplicateCandidate> duplicateCheck(DuplicateCheckRequest req) {
        return candidates(req.firstName(), req.lastName(), req.dateOfBirth(), req.phone(), req.nic(), null);
    }

    @Transactional
    public PatientDetail register(PatientRequest req) {
        validateDates(req);
        enforceUniqueIdentifiers(req, null);
        enforceDuplicateConfirmation(req, null);

        Patient p = new Patient();
        p.setMrn(sequences.nextFormatted("HMS", time.today().getYear(), 6));
        apply(p, req);
        p.setRegisteredBy(CurrentUser.require().getId());
        patients.saveAndFlush(p);
        audit.recordChange("CREATE", "PATIENT", p.getId(), "Registered patient " + p.getMrn(), null, snapshot(p));
        return PatientDetail.from(p, time.today());
    }

    @Transactional
    public PatientDetail update(Long id, PatientRequest req) {
        Patient p = load(id);
        if (req.version() == null || req.version() != p.getVersion()) {
            throw BusinessException.conflict("CONCURRENT_MODIFICATION",
                    "This patient was changed by someone else. Reload and try again.");
        }
        validateDates(req);
        enforceUniqueIdentifiers(req, id);
        enforceDuplicateConfirmation(req, id);
        Map<String, Object> before = snapshot(p);
        apply(p, req);
        patients.saveAndFlush(p);
        audit.recordChange("UPDATE", "PATIENT", id, "Updated patient " + p.getMrn(), before, snapshot(p));
        return PatientDetail.from(p, time.today());
    }

    @Transactional
    public PatientDetail changeStatus(Long id, StatusRequest req) {
        Patient p = load(id);
        if (p.getStatus() == Patient.Status.DECEASED) {
            throw BusinessException.conflict("PATIENT_DECEASED", "A deceased patient's status cannot be changed.");
        }
        if (req.status() == Patient.Status.DECEASED) {
            if (req.deceasedAt() == null) {
                throw BusinessException.badRequest("DATE_REQUIRED", "Enter the date of death.");
            }
            if (req.deceasedAt().isBefore(p.getDateOfBirth())) {
                throw BusinessException.badRequest("INVALID_DATE", "Date of death cannot be before date of birth.");
            }
            p.setDeceasedAt(req.deceasedAt());
        } else {
            p.setDeceasedAt(null);
        }
        Patient.Status old = p.getStatus();
        p.setStatus(req.status());
        patients.saveAndFlush(p);
        audit.recordChange("STATUS_CHANGE", "PATIENT", id, old + " -> " + req.status() + ": " + req.reason(),
                Map.of("status", old.name()), Map.of("status", req.status().name()));
        return PatientDetail.from(p, time.today());
    }

    /** Used by other modules (appointments) to ensure a patient may receive care. */
    @Transactional(readOnly = true)
    public Patient requireActive(Long id) {
        Patient p = load(id);
        if (p.getStatus() != Patient.Status.ACTIVE) {
            throw BusinessException.conflict("PATIENT_NOT_ACTIVE",
                    "Patient " + p.getMrn() + " is " + p.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        return p;
    }

    // ---------------------------------------------------------------------

    Patient load(Long id) {
        return patients.findById(id).orElseThrow(() -> BusinessException.notFound("Patient", id));
    }

    private void validateDates(PatientRequest req) {
        if (req.dateOfBirth().isBefore(time.today().minusYears(130))) {
            throw BusinessException.badRequest("INVALID_DATE", "Date of birth is more than 130 years ago.");
        }
    }

    private void enforceUniqueIdentifiers(PatientRequest req, Long selfId) {
        String nic = normaliseNic(req.nic());
        if (nic != null) {
            patients.findByNic(nic).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
                throw BusinessException.conflict("NIC_EXISTS", "NIC " + nic + " is already registered to patient " + o.getMrn() + ".");
            });
        }
        String passport = blankUpper(req.passportNo());
        if (passport != null) {
            patients.findByPassportNo(passport).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
                throw BusinessException.conflict("PASSPORT_EXISTS", "This passport is already registered to patient " + o.getMrn() + ".");
            });
        }
    }

    private void enforceDuplicateConfirmation(PatientRequest req, Long selfId) {
        if (req.confirmNotDuplicate()) {
            return;
        }
        List<DuplicateCandidate> found = candidates(req.firstName(), req.lastName(), req.dateOfBirth(), req.phone(), null, selfId);
        if (!found.isEmpty()) {
            String mrns = found.stream().map(DuplicateCandidate::mrn).collect(Collectors.joining(", "));
            throw BusinessException.conflict("POSSIBLE_DUPLICATE",
                    "Possible existing record(s): " + mrns + ". Check them, then confirm this is a different person.");
        }
    }

    private List<DuplicateCandidate> candidates(String first, String last, LocalDate dob, String phone, String nic, Long excludeId) {
        Map<Long, DuplicateCandidate> out = new LinkedHashMap<>();
        String normalisedNic = normaliseNic(nic);
        if (normalisedNic != null) {
            patients.findByNic(normalisedNic).filter(p -> !p.getId().equals(excludeId))
                    .ifPresent(p -> out.put(p.getId(), candidate(p, "Same NIC")));
        }
        for (Patient p : patients.findPossibleDuplicates(first.trim(), last.trim(), dob, phone == null ? "" : phone.trim(), excludeId)) {
            String reason = p.getDateOfBirth().equals(dob) ? "Same name and date of birth" : "Same name and phone";
            out.putIfAbsent(p.getId(), candidate(p, reason));
        }
        return new ArrayList<>(out.values());
    }

    private static DuplicateCandidate candidate(Patient p, String reason) {
        return new DuplicateCandidate(p.getId(), p.getMrn(), p.getFullName(), p.getDateOfBirth(), p.getPhone(), p.getNic(), reason);
    }

    private static void apply(Patient p, PatientRequest r) {
        p.setFirstName(r.firstName().trim());
        p.setLastName(r.lastName().trim());
        p.setNic(normaliseNic(r.nic()));
        p.setPassportNo(blankUpper(r.passportNo()));
        p.setGender(r.gender());
        p.setDateOfBirth(r.dateOfBirth());
        p.setBloodGroup(blank(r.bloodGroup()));
        p.setMaritalStatus(blank(r.maritalStatus()));
        p.setPhone(r.phone().trim());
        p.setEmail(r.email() == null || r.email().isBlank() ? null : r.email().trim().toLowerCase(Locale.ROOT));
        p.setAddressLine1(blank(r.addressLine1()));
        p.setAddressLine2(blank(r.addressLine2()));
        p.setCity(blank(r.city()));
        p.setDistrict(blank(r.district()));
        p.setOccupation(blank(r.occupation()));
        replaceContacts(p, r.emergencyContacts() == null ? List.of() : r.emergencyContacts());
    }

    /** Replaces the contact list; exactly one primary when any exist. */
    private static void replaceContacts(Patient p, List<EmergencyContactDto> dtos) {
        p.getEmergencyContacts().clear();
        boolean primaryAssigned = false;
        for (EmergencyContactDto d : dtos) {
            EmergencyContact c = new EmergencyContact();
            c.setPatient(p);
            c.setName(d.name().trim());
            c.setRelationship(d.relationship().trim());
            c.setPhone(d.phone().trim());
            c.setAltPhone(blank(d.altPhone()));
            c.setPrimaryContact(d.primary() && !primaryAssigned);
            primaryAssigned |= c.isPrimaryContact();
            p.getEmergencyContacts().add(c);
        }
        if (!primaryAssigned && !p.getEmergencyContacts().isEmpty()) {
            p.getEmergencyContacts().get(0).setPrimaryContact(true);
        }
    }

    static String normaliseNic(String nic) {
        if (nic == null || nic.isBlank()) {
            return null;
        }
        return nic.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> snapshot(Patient p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mrn", p.getMrn());
        m.put("name", p.getFullName());
        m.put("nic", Objects.toString(p.getNic(), ""));
        m.put("dateOfBirth", p.getDateOfBirth().toString());
        m.put("gender", p.getGender().name());
        m.put("phone", p.getPhone());
        m.put("address", String.join(", ", List.of(Objects.toString(p.getAddressLine1(), ""), Objects.toString(p.getCity(), ""))));
        m.put("status", p.getStatus().name());
        m.put("emergencyContacts", p.getEmergencyContacts().size());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String blankUpper(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static Patient.Status parseStatus(String s) {
        try {
            return Patient.Status.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown status: " + s);
        }
    }
}
