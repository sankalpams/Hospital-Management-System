package lk.hms.modules.inpatient.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.sequence.SequenceService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.billing.service.BillingService;
import lk.hms.modules.departments.entity.Department;
import lk.hms.modules.departments.repository.DepartmentRepository;
import lk.hms.modules.doctors.entity.Doctor;
import lk.hms.modules.doctors.repository.DoctorRepository;
import lk.hms.modules.emr.service.EmrService;
import lk.hms.modules.inpatient.dto.InpatientDtos.*;
import lk.hms.modules.inpatient.entity.*;
import lk.hms.modules.inpatient.repository.*;
import lk.hms.modules.patients.entity.Patient;
import lk.hms.modules.patients.repository.PatientRepository;
import lk.hms.modules.patients.service.PatientService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inpatient management (docs/01 §2F). Admission and transfer lock the target bed row and check it
 * is AVAILABLE; DB unique keys on the live bed assignment are the final guard. Discharge needs a
 * summary, frees the bed for cleaning and charges bed-days per stay at the room's daily rate.
 */
@Service
public class InpatientService {

    private static final EnumSet<Admission.Status> LIVE = EnumSet.of(Admission.Status.REQUESTED, Admission.Status.ADMITTED);

    private final WardRepository wards;
    private final RoomRepository rooms;
    private final BedRepository beds;
    private final AdmissionRepository admissions;
    private final BedAssignmentRepository assignments;
    private final DepartmentRepository departments;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final PatientService patientService;
    private final EmrService emr;
    private final BillingService billing;
    private final SequenceService sequences;
    private final AuditService audit;
    private final HospitalTime time;

    public InpatientService(WardRepository wards, RoomRepository rooms, BedRepository beds, AdmissionRepository admissions,
                            BedAssignmentRepository assignments, DepartmentRepository departments, DoctorRepository doctors,
                            PatientRepository patients, PatientService patientService, EmrService emr, BillingService billing,
                            SequenceService sequences, AuditService audit, HospitalTime time) {
        this.wards = wards;
        this.rooms = rooms;
        this.beds = beds;
        this.admissions = admissions;
        this.assignments = assignments;
        this.departments = departments;
        this.doctors = doctors;
        this.patients = patients;
        this.patientService = patientService;
        this.emr = emr;
        this.billing = billing;
        this.sequences = sequences;
        this.audit = audit;
        this.time = time;
    }

    // ================= ward setup =================

    @Transactional
    public WardView createWard(WardRequest r) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        if (wards.existsByCode(code)) {
            throw BusinessException.conflict("WARD_CODE_TAKEN", "Ward code " + code + " already exists.");
        }
        if (!departments.existsById(r.departmentId())) {
            throw BusinessException.badRequest("UNKNOWN_DEPARTMENT", "Choose a valid department.");
        }
        Ward w = new Ward();
        w.setCode(code);
        w.setName(r.name().trim());
        w.setDepartmentId(r.departmentId());
        w.setWardType(r.wardType());
        w.setGenderPolicy(r.genderPolicy());
        w.setFloor(r.floor() == null || r.floor().isBlank() ? null : r.floor().trim());
        wards.saveAndFlush(w);
        audit.recordChange("CREATE", "WARD", w.getId(), "Ward " + w.getName(), null, null);
        return board(List.of(w)).get(0);
    }

    /** Adds a room with {@code beds} beds numbered A, B, C ... */
    @Transactional
    public WardView addRoom(Long wardId, RoomRequest r) {
        Ward w = wards.findById(wardId).orElseThrow(() -> BusinessException.notFound("Ward", wardId));
        String roomNo = r.roomNo().trim().toUpperCase(Locale.ROOT);
        if (rooms.existsByWardIdAndRoomNo(wardId, roomNo)) {
            throw BusinessException.conflict("ROOM_EXISTS", "Room " + roomNo + " already exists in " + w.getName() + ".");
        }
        Room room = new Room();
        room.setWardId(wardId);
        room.setRoomNo(roomNo);
        room.setRoomType(r.roomType());
        room.setDailyRate(r.dailyRate());
        rooms.saveAndFlush(room);
        for (int i = 0; i < r.beds(); i++) {
            Bed b = new Bed();
            b.setRoomId(room.getId());
            b.setBedNo(String.valueOf((char) ('A' + i)));
            beds.save(b);
        }
        audit.recordChange("CREATE", "WARD", wardId, "Room " + roomNo + " with " + r.beds() + " bed(s)", null, null);
        return board(List.of(w)).get(0);
    }

    @Transactional
    public void setBedStatus(Long bedId, BedStatusRequest r) {
        Bed b = beds.lockById(bedId).orElseThrow(() -> BusinessException.notFound("Bed", bedId));
        if (b.getStatus() == Bed.Status.OCCUPIED) {
            throw BusinessException.conflict("BED_OCCUPIED", "An occupied bed is freed by transferring or discharging the patient.");
        }
        b.setStatus(Bed.Status.valueOf(r.status()));
        audit.recordChange("STATUS_CHANGE", "BED", bedId, "Bed set to " + r.status(), null, null);
    }

    @Transactional(readOnly = true)
    public List<WardView> bedBoard() {
        return board(wards.findAllByOrderByNameAsc().stream().filter(Ward::isActive).toList());
    }

    // ================= admissions =================

    @Transactional
    public AdmissionView request(AdmissionRequest r) {
        Patient p = patientService.requireActive(r.patientId());
        Doctor d = doctors.findDetailed(r.attendingDoctorId()).orElseThrow(() -> BusinessException.badRequest("UNKNOWN_DOCTOR", "Choose a valid doctor."));
        if (admissions.existsByPatientIdAndStatusIn(p.getId(), LIVE)) {
            throw BusinessException.conflict("ALREADY_ADMITTED", p.getFullName() + " already has an open admission.");
        }
        Admission a = new Admission();
        a.setAdmissionNo(sequences.nextFormatted("ADM", time.today().getYear(), 6));
        a.setPatientId(p.getId());
        a.setAttendingDoctorId(d.getId());
        a.setDepartmentId(d.getEmployee().getDepartment().getId());
        a.setAdmissionType(r.admissionType());
        a.setReason(r.reason().trim());
        a.setRequestedBy(CurrentUser.require().getId());
        a.setRequestedAt(time.now());
        try {
            admissions.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("ALREADY_ADMITTED", p.getFullName() + " already has an open admission.");
        }
        audit.recordChange("CREATE", "ADMISSION", a.getId(), "Admission requested " + a.getAdmissionNo() + " for " + p.getMrn(), null, null);
        return views(List.of(a)).get(0);
    }

    @Transactional
    public AdmissionView admit(Long id, Long bedId) {
        Admission a = lock(id);
        if (a.getStatus() != Admission.Status.REQUESTED) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "Only a requested admission can be admitted.");
        }
        Bed bed = takeBed(bedId);
        a.setStatus(Admission.Status.ADMITTED);
        a.setAdmittedAt(time.now());
        assign(a, bed);
        admissions.saveAndFlush(a);
        emr.openForAdmission(a.getId(), a.getPatientId(), a.getAttendingDoctorId(), a.getDepartmentId(), a.getReason());
        audit.recordChange("STATUS_CHANGE", "ADMISSION", id, a.getAdmissionNo() + " admitted to bed #" + bedId, null, null);
        return views(List.of(a)).get(0);
    }

    @Transactional
    public AdmissionView transfer(Long id, Long bedId) {
        Admission a = lock(id);
        if (a.getStatus() != Admission.Status.ADMITTED) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "Only an admitted patient can be transferred.");
        }
        BedAssignment current = assignments.findByAdmissionIdAndReleasedAtIsNull(id).orElseThrow();
        if (current.getBedId().equals(bedId)) {
            throw BusinessException.badRequest("SAME_BED", "The patient is already in this bed.");
        }
        Bed target = takeBed(bedId);
        release(current, "TRANSFER");
        assignments.flush();   // free the unique "one live bed per admission" slot before taking the new one
        assign(a, target);
        audit.recordChange("STATUS_CHANGE", "ADMISSION", id, a.getAdmissionNo() + " transferred to bed #" + bedId, null, null);
        return views(List.of(a)).get(0);
    }

    @Transactional
    public AdmissionView discharge(Long id, DischargeRequest r) {
        Admission a = lock(id);
        if (a.getStatus() != Admission.Status.ADMITTED) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "Only an admitted patient can be discharged.");
        }
        assignments.findByAdmissionIdAndReleasedAtIsNull(id).ifPresent(current -> release(current, "DISCHARGE"));
        a.setStatus(Admission.Status.DISCHARGED);
        a.setDischargedAt(time.now());
        a.setDischargeType(r.dischargeType());
        a.setDischargeSummary(r.summary().trim());
        admissions.saveAndFlush(a);
        emr.closeForAdmission(id);
        chargeBedDays(a);
        audit.recordChange("STATUS_CHANGE", "ADMISSION", id, a.getAdmissionNo() + " discharged (" + r.dischargeType() + ")", null, null);
        return views(List.of(a)).get(0);
    }

    @Transactional
    public AdmissionView cancel(Long id, String reason) {
        Admission a = lock(id);
        if (a.getStatus() != Admission.Status.REQUESTED) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "Only a request that has not been admitted can be cancelled.");
        }
        a.setStatus(Admission.Status.CANCELLED);
        audit.recordChange("STATUS_CHANGE", "ADMISSION", id, a.getAdmissionNo() + " cancelled: " + reason.trim(), null, null);
        return views(List.of(a)).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdmissionView> search(String status, Long patientId, String q, int page, int size) {
        Set<Admission.Status> statuses;
        try {
            statuses = status == null || status.isBlank() ? EnumSet.allOf(Admission.Status.class)
                    : Arrays.stream(status.split(",")).map(s -> Admission.Status.valueOf(s.trim().toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Admission.Status.class)));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown admission status.");
        }
        String term = q == null || q.isBlank() ? null : q.trim();
        var result = admissions.search(statuses, patientId, term == null ? null : term.toUpperCase(Locale.ROOT),
                term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%",
                Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "id")));
        return new PageResponse<>(views(result.getContent()), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdmissionView get(Long id) {
        return views(List.of(admissions.findById(id).orElseThrow(() -> BusinessException.notFound("Admission", id)))).get(0);
    }

    // ================= helpers =================

    private Admission lock(Long id) {
        return admissions.lockById(id).orElseThrow(() -> BusinessException.notFound("Admission", id));
    }

    /** Locks the bed and requires it to be AVAILABLE; it becomes OCCUPIED in the same transaction. */
    private Bed takeBed(Long bedId) {
        Bed bed = beds.lockById(bedId).orElseThrow(() -> BusinessException.notFound("Bed", bedId));
        if (bed.getStatus() != Bed.Status.AVAILABLE) {
            throw BusinessException.conflict("BED_NOT_AVAILABLE", "Bed " + bed.getBedNo() + " is " + bed.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        bed.setStatus(Bed.Status.OCCUPIED);
        return bed;
    }

    private void assign(Admission a, Bed bed) {
        BedAssignment ba = new BedAssignment();
        ba.setAdmissionId(a.getId());
        ba.setBedId(bed.getId());
        ba.setAssignedAt(time.now());
        ba.setAssignedBy(CurrentUser.require().getId());
        try {
            assignments.saveAndFlush(ba);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("BED_NOT_AVAILABLE", "That bed has just been taken. Choose another.");
        }
    }

    private void release(BedAssignment current, String reason) {
        current.setReleasedAt(time.now());
        current.setReleaseReason(reason);
        beds.lockById(current.getBedId()).ifPresent(b -> b.setStatus(Bed.Status.CLEANING));
    }

    /** One charge per stay (bed assignment): whole days, minimum one, at that room's daily rate. */
    private void chargeBedDays(Admission a) {
        for (BedAssignment stay : assignments.findByAdmissionIdOrderByAssignedAtAsc(a.getId())) {
            Bed bed = beds.findById(stay.getBedId()).orElseThrow();
            Room room = rooms.findById(bed.getRoomId()).orElseThrow();
            Instant end = stay.getReleasedAt() == null ? time.now() : stay.getReleasedAt();
            long days = Math.max(1, (Duration.between(stay.getAssignedAt(), end).toHours() + 23) / 24);
            billing.raiseCharge(a.getPatientId(), "ADMISSION", "Bed " + room.getRoomNo() + "-" + bed.getBedNo() + " (" + days + " day" + (days == 1 ? "" : "s") + ")",
                    "BED_ASSIGNMENT", stay.getId(), a.getDepartmentId(), BigDecimal.valueOf(days), room.getDailyRate());
        }
    }

    private List<WardView> board(List<Ward> list) {
        List<Long> wardIds = list.stream().map(Ward::getId).toList();
        List<Room> roomList = wardIds.isEmpty() ? List.of() : rooms.findByWardIdInOrderByRoomNoAsc(wardIds);
        List<Bed> bedList = roomList.isEmpty() ? List.of() : beds.findByRoomIdInOrderByBedNoAsc(roomList.stream().map(Room::getId).toList());
        Map<Long, BedAssignment> liveByBed = bedList.isEmpty() ? Map.of() : assignments.findByBedIdInAndReleasedAtIsNull(bedList.stream().map(Bed::getId).toList())
                .stream().collect(Collectors.toMap(BedAssignment::getBedId, Function.identity()));
        Map<Long, Admission> adm = admissions.findAllById(liveByBed.values().stream().map(BedAssignment::getAdmissionId).toList())
                .stream().collect(Collectors.toMap(Admission::getId, Function.identity()));
        Map<Long, Patient> pts = patients.findAllById(adm.values().stream().map(Admission::getPatientId).toList())
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, String> deptNames = departments.findAllById(list.stream().map(Ward::getDepartmentId).distinct().toList())
                .stream().collect(Collectors.toMap(Department::getId, Department::getName));
        Map<Long, List<Bed>> bedsByRoom = bedList.stream().collect(Collectors.groupingBy(Bed::getRoomId));
        Map<Long, List<Room>> roomsByWard = roomList.stream().collect(Collectors.groupingBy(Room::getWardId));

        return list.stream().map(w -> {
            List<RoomView> rv = roomsByWard.getOrDefault(w.getId(), List.of()).stream().map(r -> new RoomView(r.getId(), r.getRoomNo(),
                    r.getRoomType(), r.getDailyRate(), bedsByRoom.getOrDefault(r.getId(), List.of()).stream().map(b -> {
                        BedAssignment live = liveByBed.get(b.getId());
                        Admission a = live == null ? null : adm.get(live.getAdmissionId());
                        Patient p = a == null ? null : pts.get(a.getPatientId());
                        return new BedView(b.getId(), b.getBedNo(), b.getStatus().name(), a == null ? null : a.getId(),
                                p == null ? null : p.getId(), p == null ? null : p.getFullName(), p == null ? null : p.getMrn());
                    }).toList())).toList();
            int total = rv.stream().mapToInt(r -> r.beds().size()).sum();
            int occupied = (int) rv.stream().flatMap(r -> r.beds().stream()).filter(b -> "OCCUPIED".equals(b.status())).count();
            int available = (int) rv.stream().flatMap(r -> r.beds().stream()).filter(b -> "AVAILABLE".equals(b.status())).count();
            return new WardView(w.getId(), w.getCode(), w.getName(), w.getDepartmentId(), deptNames.get(w.getDepartmentId()), w.getWardType(),
                    w.getGenderPolicy(), w.getFloor(), w.isActive(), total, occupied, available, rv);
        }).toList();
    }

    private List<AdmissionView> views(List<Admission> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Patient> pts = patients.findAllById(list.stream().map(Admission::getPatientId).distinct().toList())
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, String> docs = doctors.findAllById(list.stream().map(Admission::getAttendingDoctorId).distinct().toList())
                .stream().collect(Collectors.toMap(Doctor::getId, Doctor::getDisplayName));
        Map<Long, String> depts = departments.findAllById(list.stream().map(Admission::getDepartmentId).distinct().toList())
                .stream().collect(Collectors.toMap(Department::getId, Department::getName));
        Map<Long, List<BedAssignment>> stays = assignments.findByAdmissionIdIn(list.stream().map(Admission::getId).toList())
                .stream().sorted(Comparator.comparing(BedAssignment::getAssignedAt)).collect(Collectors.groupingBy(BedAssignment::getAdmissionId));
        Map<Long, Bed> bedMap = beds.findAllById(stays.values().stream().flatMap(List::stream).map(BedAssignment::getBedId).distinct().toList())
                .stream().collect(Collectors.toMap(Bed::getId, Function.identity()));
        Map<Long, Room> roomMap = rooms.findAllById(bedMap.values().stream().map(Bed::getRoomId).distinct().toList())
                .stream().collect(Collectors.toMap(Room::getId, Function.identity()));
        Map<Long, Ward> wardMap = wards.findAllById(roomMap.values().stream().map(Room::getWardId).distinct().toList())
                .stream().collect(Collectors.toMap(Ward::getId, Function.identity()));
        return list.stream().map(a -> {
            Patient p = pts.get(a.getPatientId());
            List<StayView> sv = stays.getOrDefault(a.getId(), List.of()).stream().map(s -> {
                Bed b = bedMap.get(s.getBedId());
                Room r = roomMap.get(b.getRoomId());
                return new StayView(wardMap.get(r.getWardId()).getName(), r.getRoomNo(), b.getBedNo(), s.getAssignedAt(), s.getReleasedAt(), s.getReleaseReason());
            }).toList();
            String current = sv.stream().filter(s -> s.releasedAt() == null).findFirst()
                    .map(s -> s.wardName() + " · " + s.roomNo() + "-" + s.bedNo()).orElse(null);
            return new AdmissionView(a.getId(), a.getAdmissionNo(), p.getId(), p.getFullName(), p.getMrn(), p.getGender().name(),
                    a.getAttendingDoctorId(), docs.get(a.getAttendingDoctorId()), depts.get(a.getDepartmentId()), a.getAdmissionType(),
                    a.getStatus().name(), a.getReason(), a.getRequestedAt(), a.getAdmittedAt(), a.getDischargedAt(), a.getDischargeType(),
                    a.getDischargeSummary(), current, sv);
        }).toList();
    }
}
