package lk.hms.modules.doctors.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.sequence.SequenceService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.departments.entity.Department;
import lk.hms.modules.departments.repository.DepartmentRepository;
import lk.hms.modules.doctors.dto.DoctorDtos.DoctorOption;
import lk.hms.modules.doctors.dto.DoctorDtos.DoctorRequest;
import lk.hms.modules.doctors.dto.DoctorDtos.DoctorResponse;
import lk.hms.modules.doctors.dto.DoctorDtos.ExceptionRequest;
import lk.hms.modules.doctors.dto.DoctorDtos.ExceptionResponse;
import lk.hms.modules.doctors.dto.DoctorDtos.ScheduleRequest;
import lk.hms.modules.doctors.dto.DoctorDtos.ScheduleResponse;
import lk.hms.modules.doctors.entity.Doctor;
import lk.hms.modules.doctors.entity.DoctorSchedule;
import lk.hms.modules.doctors.entity.ScheduleException;
import lk.hms.modules.doctors.repository.DoctorRepository;
import lk.hms.modules.doctors.repository.ScheduleExceptionRepository;
import lk.hms.modules.doctors.repository.ScheduleRepository;
import lk.hms.modules.staff.entity.Employee;
import lk.hms.modules.staff.repository.EmployeeRepository;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Doctor = employee (staff_category DOCTOR) + doctor profile, created and updated together.
 * Schedules are weekly sessions in hospital-local time; overlapping sessions for the same doctor
 * are rejected so availability is never ambiguous.
 */
@Service
public class DoctorService {

    private static final Map<String, String> SORTS = Map.of(
            "name", "employee.lastName", "specialization", "specialization", "registrationNo", "registrationNo");

    private final DoctorRepository doctors;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final ScheduleRepository schedules;
    private final ScheduleExceptionRepository exceptions;
    private final UserRepository users;
    private final SequenceService sequences;
    private final AuditService audit;
    private final HospitalTime time;

    public DoctorService(DoctorRepository doctors, EmployeeRepository employees, DepartmentRepository departments,
                         ScheduleRepository schedules, ScheduleExceptionRepository exceptions, UserRepository users,
                         SequenceService sequences, AuditService audit, HospitalTime time) {
        this.doctors = doctors;
        this.employees = employees;
        this.departments = departments;
        this.schedules = schedules;
        this.exceptions = exceptions;
        this.users = users;
        this.sequences = sequences;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorResponse> search(String q, Long departmentId, String status, int page, int size, String sort) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Employee.Status st;
        try {
            st = status == null || status.isBlank() ? null : Employee.Status.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown status: " + status);
        }
        return PageResponse.of(doctors.search(pattern, departmentId, st,
                Paging.of(page, size, sort, SORTS, Sort.by("employee.lastName"))), DoctorResponse::from);
    }

    @Transactional(readOnly = true)
    public List<DoctorOption> bookable(Long departmentId) {
        return doctors.findBookable(departmentId).stream().map(DoctorOption::from).toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse get(Long id) {
        return DoctorResponse.from(load(id));
    }

    @Transactional
    public DoctorResponse create(DoctorRequest req) {
        String regNo = req.registrationNo().trim().toUpperCase(Locale.ROOT);
        if (doctors.existsByRegistrationNo(regNo)) {
            throw BusinessException.conflict("REGISTRATION_TAKEN", "SLMC registration " + regNo + " is already recorded.");
        }
        Employee e = new Employee();
        e.setEmployeeNo(sequences.nextPlain("EMP", 6));
        e.setStaffCategory(Employee.Category.DOCTOR);
        e.setDesignation("Medical Officer");
        applyEmployee(e, req, null);
        employees.saveAndFlush(e);

        Doctor d = new Doctor();
        d.setEmployee(e);
        d.setRegistrationNo(regNo);
        applyDoctor(d, req);
        doctors.saveAndFlush(d);
        audit.recordChange("CREATE", "DOCTOR", d.getId(), "Registered " + d.getDisplayName(), null, snapshot(d));
        return DoctorResponse.from(d);
    }

    @Transactional
    public DoctorResponse update(Long id, DoctorRequest req) {
        Doctor d = load(id);
        Employee e = d.getEmployee();
        if (req.version() == null || req.version() != e.getVersion()) {
            throw BusinessException.conflict("CONCURRENT_MODIFICATION", "This doctor was changed by someone else. Reload and try again.");
        }
        String regNo = req.registrationNo().trim().toUpperCase(Locale.ROOT);
        if (doctors.existsByRegistrationNoAndIdNot(regNo, id)) {
            throw BusinessException.conflict("REGISTRATION_TAKEN", "SLMC registration " + regNo + " is already recorded.");
        }
        Map<String, Object> before = snapshot(d);
        applyEmployee(e, req, e.getId());
        d.setRegistrationNo(regNo);
        applyDoctor(d, req);
        employees.saveAndFlush(e);
        doctors.saveAndFlush(d);
        audit.recordChange("UPDATE", "DOCTOR", id, "Updated " + d.getDisplayName(), before, snapshot(d));
        return DoctorResponse.from(d);
    }

    // ---------------- schedules ----------------

    @Transactional(readOnly = true)
    public List<ScheduleResponse> schedules(Long doctorId) {
        load(doctorId);
        return schedules.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).stream().map(ScheduleResponse::from).toList();
    }

    @Transactional
    public ScheduleResponse addSchedule(Long doctorId, ScheduleRequest req) {
        Doctor d = load(doctorId);
        DoctorSchedule s = new DoctorSchedule();
        s.setDoctorId(doctorId);
        applySchedule(s, req);
        guardOverlap(s);
        schedules.saveAndFlush(s);
        audit.recordChange("CREATE", "DOCTOR", doctorId, "Added session for " + d.getDisplayName(), null, scheduleSnapshot(s));
        return ScheduleResponse.from(s);
    }

    @Transactional
    public ScheduleResponse updateSchedule(Long doctorId, Long scheduleId, ScheduleRequest req) {
        load(doctorId);
        DoctorSchedule s = schedules.findById(scheduleId).filter(x -> x.getDoctorId().equals(doctorId))
                .orElseThrow(() -> BusinessException.notFound("Schedule", scheduleId));
        Map<String, Object> before = scheduleSnapshot(s);
        applySchedule(s, req);
        guardOverlap(s);
        schedules.saveAndFlush(s);
        audit.recordChange("UPDATE", "DOCTOR", doctorId, "Changed session #" + scheduleId, before, scheduleSnapshot(s));
        return ScheduleResponse.from(s);
    }

    @Transactional(readOnly = true)
    public List<ExceptionResponse> exceptions(Long doctorId) {
        load(doctorId);
        return exceptions.findByDoctorIdAndDateToGreaterThanEqualOrderByDateFrom(doctorId, time.today().minusDays(30))
                .stream().map(ExceptionResponse::from).toList();
    }

    @Transactional
    public ExceptionResponse addException(Long doctorId, ExceptionRequest req) {
        Doctor d = load(doctorId);
        if (req.dateTo().isBefore(req.dateFrom())) {
            throw BusinessException.badRequest("INVALID_RANGE", "'To' date must not be before 'from' date.");
        }
        if (req.dateTo().isBefore(time.today())) {
            throw BusinessException.badRequest("INVALID_RANGE", "Unavailability must include today or a future date.");
        }
        ScheduleException x = new ScheduleException();
        x.setDoctorId(doctorId);
        x.setDateFrom(req.dateFrom());
        x.setDateTo(req.dateTo());
        x.setReason(req.reason().trim());
        exceptions.saveAndFlush(x);
        audit.recordChange("CREATE", "DOCTOR", doctorId, "Unavailable " + x.getDateFrom() + " to " + x.getDateTo()
                + " for " + d.getDisplayName() + ": " + x.getReason(), null, null);
        return ExceptionResponse.from(x);
    }

    @Transactional
    public void removeException(Long doctorId, Long exceptionId) {
        ScheduleException x = exceptions.findById(exceptionId).filter(e -> e.getDoctorId().equals(doctorId))
                .orElseThrow(() -> BusinessException.notFound("Unavailability", exceptionId));
        exceptions.delete(x);
        audit.recordChange("DELETE", "DOCTOR", doctorId, "Removed unavailability " + x.getDateFrom() + " to " + x.getDateTo(), null, null);
    }

    /** Used by the appointments module. */
    @Transactional(readOnly = true)
    public Doctor requireBookable(Long doctorId) {
        Doctor d = load(doctorId);
        if (!d.isBookable()) {
            throw BusinessException.conflict("DOCTOR_NOT_AVAILABLE", d.getDisplayName() + " is not accepting appointments.");
        }
        return d;
    }

    // ---------------------------------------------------------------------

    private Doctor load(Long id) {
        return doctors.findDetailed(id).orElseThrow(() -> BusinessException.notFound("Doctor", id));
    }

    private void applyEmployee(Employee e, DoctorRequest r, Long selfId) {
        Department dept = departments.findById(r.departmentId())
                .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_DEPARTMENT", "Choose a valid department."));
        if (!dept.isActive() && (selfId == null || !Objects.equals(e.getDepartment().getId(), dept.getId()))) {
            throw BusinessException.badRequest("DEPARTMENT_INACTIVE", dept.getName() + " is inactive.");
        }
        String nic = r.nic() == null || r.nic().isBlank() ? null : r.nic().trim().toUpperCase(Locale.ROOT);
        if (nic != null) {
            employees.findByNic(nic).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
                throw BusinessException.conflict("NIC_EXISTS", "This NIC belongs to employee " + o.getEmployeeNo() + ".");
            });
        }
        if (r.userId() != null) {
            User u = users.findById(r.userId())
                    .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_USER", "Choose a valid user account."));
            if (u.getRoles().stream().noneMatch(role -> role.getCode().equals("DOCTOR"))) {
                throw BusinessException.badRequest("USER_NOT_DOCTOR", "The linked account must have the Doctor role.");
            }
            employees.findByUserId(u.getId()).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
                throw BusinessException.conflict("USER_ALREADY_LINKED", "This account is already linked to " + o.getFullName() + ".");
            });
        }
        e.setDepartment(dept);
        e.setFirstName(r.firstName().trim());
        e.setLastName(r.lastName().trim());
        e.setGender(r.gender());
        e.setNic(nic);
        e.setPhone(r.phone().trim());
        e.setEmail(r.email() == null || r.email().isBlank() ? null : r.email().trim().toLowerCase(Locale.ROOT));
        e.setEmploymentType(r.employmentType());
        e.setJoinDate(r.joinDate());
        e.setUserId(r.userId());
        if (r.status() != null) {
            e.setStatus(r.status());
            e.setLeaveDate(r.status() == Employee.Status.RESIGNED || r.status() == Employee.Status.TERMINATED ? time.today() : null);
        }
    }

    private static void applyDoctor(Doctor d, DoctorRequest r) {
        d.setSpecialization(r.specialization().trim());
        d.setQualifications(r.qualifications() == null || r.qualifications().isBlank() ? null : r.qualifications().trim());
        d.setConsultationFee(r.consultationFee().setScale(2, RoundingMode.HALF_UP));
        d.setAcceptingPatients(r.acceptingPatients());
    }

    private static void applySchedule(DoctorSchedule s, ScheduleRequest r) {
        if (!r.endTime().isAfter(r.startTime())) {
            throw BusinessException.badRequest("INVALID_TIME", "The session must end after it starts.");
        }
        if (Duration.between(r.startTime(), r.endTime()).toMinutes() < r.slotMinutes()) {
            throw BusinessException.badRequest("INVALID_TIME", "The session is shorter than one appointment slot.");
        }
        if (r.effectiveTo() != null && r.effectiveTo().isBefore(r.effectiveFrom())) {
            throw BusinessException.badRequest("INVALID_RANGE", "'Effective to' must not be before 'effective from'.");
        }
        s.setDayOfWeek(r.dayOfWeek());
        s.setStartTime(r.startTime());
        s.setEndTime(r.endTime());
        s.setSlotMinutes(r.slotMinutes());
        s.setMaxPatients(r.maxPatients());
        s.setRoomLabel(r.roomLabel() == null || r.roomLabel().isBlank() ? null : r.roomLabel().trim());
        s.setEffectiveFrom(r.effectiveFrom());
        s.setEffectiveTo(r.effectiveTo());
        s.setActive(r.active() == null || r.active());
    }

    /** Two active sessions on the same weekday may not overlap in time while their date ranges overlap. */
    private void guardOverlap(DoctorSchedule s) {
        if (!s.isActive()) {
            return;
        }
        for (DoctorSchedule o : schedules.findByDoctorIdAndDayOfWeekAndActiveTrue(s.getDoctorId(), s.getDayOfWeek())) {
            if (Objects.equals(o.getId(), s.getId())) {
                continue;
            }
            boolean timesOverlap = s.getStartTime().isBefore(o.getEndTime()) && o.getStartTime().isBefore(s.getEndTime());
            LocalDate sEnd = s.getEffectiveTo() == null ? LocalDate.MAX : s.getEffectiveTo();
            LocalDate oEnd = o.getEffectiveTo() == null ? LocalDate.MAX : o.getEffectiveTo();
            boolean datesOverlap = !s.getEffectiveFrom().isAfter(oEnd) && !o.getEffectiveFrom().isAfter(sEnd);
            if (timesOverlap && datesOverlap) {
                throw BusinessException.conflict("SCHEDULE_OVERLAP",
                        "This overlaps the existing session " + o.getStartTime() + "–" + o.getEndTime() + " on the same day.");
            }
        }
    }

    private static Map<String, Object> snapshot(Doctor d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", d.getEmployee().getFullName());
        m.put("department", d.getEmployee().getDepartment().getName());
        m.put("registrationNo", d.getRegistrationNo());
        m.put("specialization", d.getSpecialization());
        m.put("consultationFee", d.getConsultationFee().toPlainString());
        m.put("acceptingPatients", d.isAcceptingPatients());
        m.put("status", d.getEmployee().getStatus().name());
        return m;
    }

    private static Map<String, Object> scheduleSnapshot(DoctorSchedule s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dayOfWeek", s.getDayOfWeek());
        m.put("time", s.getStartTime() + "-" + s.getEndTime());
        m.put("slotMinutes", s.getSlotMinutes());
        m.put("maxPatients", s.getMaxPatients());
        m.put("effective", s.getEffectiveFrom() + ".." + (s.getEffectiveTo() == null ? "" : s.getEffectiveTo()));
        m.put("active", s.isActive());
        return m;
    }
}
