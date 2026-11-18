package lk.hms.modules.staff.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.security.HmsUserPrincipal;
import lk.hms.common.sequence.SequenceService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.departments.entity.Department;
import lk.hms.modules.departments.repository.DepartmentRepository;
import lk.hms.modules.notifications.NotificationService;
import lk.hms.modules.staff.dto.StaffDtos.*;
import lk.hms.modules.staff.entity.Attendance;
import lk.hms.modules.staff.entity.Employee;
import lk.hms.modules.staff.entity.LeaveRequest;
import lk.hms.modules.staff.entity.LeaveType;
import lk.hms.modules.staff.repository.AttendanceRepository;
import lk.hms.modules.staff.repository.EmployeeRepository;
import lk.hms.modules.staff.repository.LeaveRequestRepository;
import lk.hms.modules.staff.repository.LeaveTypeRepository;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Staff records for every category, a daily attendance register and leave with yearly balances
 * (docs/01 §2J). Doctors are listed here but created and edited on the Doctors page.
 */
@Service
public class StaffService {

    private static final Map<String, String> SORTS = Map.of("name", "lastName", "employeeNo", "employeeNo", "joinDate", "joinDate");
    private static final EnumSet<LeaveRequest.Status> LIVE = EnumSet.of(LeaveRequest.Status.PENDING, LeaveRequest.Status.APPROVED);

    private final EmployeeRepository employees;
    private final AttendanceRepository attendance;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveRequestRepository leaves;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final SequenceService sequences;
    private final NotificationService notifications;
    private final AuditService audit;
    private final HospitalTime time;

    public StaffService(EmployeeRepository employees, AttendanceRepository attendance, LeaveTypeRepository leaveTypes,
                        LeaveRequestRepository leaves, DepartmentRepository departments, UserRepository users,
                        SequenceService sequences, NotificationService notifications, AuditService audit, HospitalTime time) {
        this.employees = employees;
        this.attendance = attendance;
        this.leaveTypes = leaveTypes;
        this.leaves = leaves;
        this.departments = departments;
        this.users = users;
        this.sequences = sequences;
        this.notifications = notifications;
        this.audit = audit;
        this.time = time;
    }

    // ================= employees =================

    @Transactional(readOnly = true)
    public PageResponse<EmployeeView> search(String q, String category, Long departmentId, String status, int page, int size, String sort) {
        Employee.Category cat;
        Employee.Status st;
        try {
            cat = category == null || category.isBlank() ? null : Employee.Category.valueOf(category.toUpperCase(Locale.ROOT));
            st = status == null || status.isBlank() ? null : Employee.Status.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown category or status.");
        }
        String term = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.of(employees.search(cat, departmentId, st, term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%",
                term == null ? null : term.toUpperCase(Locale.ROOT), Paging.of(page, size, sort, SORTS, Sort.by("lastName", "firstName"))),
                EmployeeView::from);
    }

    @Transactional(readOnly = true)
    public EmployeeView get(Long id) {
        return EmployeeView.from(load(id));
    }

    @Transactional
    public EmployeeView create(EmployeeRequest r) {
        if (r.staffCategory() == Employee.Category.DOCTOR) {
            throw BusinessException.badRequest("USE_DOCTORS_PAGE", "Add doctors from the Doctors page so their clinical profile is created too.");
        }
        Employee e = new Employee();
        e.setEmployeeNo(sequences.nextPlain("EMP", 6));
        apply(e, r, null);
        employees.saveAndFlush(e);
        audit.recordChange("CREATE", "EMPLOYEE", e.getId(), "Employee " + e.getEmployeeNo() + " " + e.getFullName(), null, null);
        return EmployeeView.from(e);
    }

    @Transactional
    public EmployeeView update(Long id, EmployeeRequest r) {
        Employee e = load(id);
        if (e.getStaffCategory() == Employee.Category.DOCTOR || r.staffCategory() == Employee.Category.DOCTOR) {
            throw BusinessException.badRequest("USE_DOCTORS_PAGE", "Doctors are edited from the Doctors page.");
        }
        if (r.version() != null && r.version() != e.getVersion()) {
            throw BusinessException.conflict("STALE_VERSION", "Someone else changed this record. Reload and try again.");
        }
        String before = e.getStatus() + " " + e.getDesignation() + " " + e.getDepartment().getName();
        apply(e, r, id);
        employees.saveAndFlush(e);
        audit.recordChange("UPDATE", "EMPLOYEE", id, "Employee " + e.getEmployeeNo() + " updated", before,
                e.getStatus() + " " + e.getDesignation() + " " + e.getDepartment().getName());
        return EmployeeView.from(e);
    }

    private void apply(Employee e, EmployeeRequest r, Long selfId) {
        Department dept = departments.findById(r.departmentId())
                .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_DEPARTMENT", "Choose a valid department."));
        if (!dept.isActive() && (selfId == null || !Objects.equals(e.getDepartment().getId(), dept.getId()))) {
            throw BusinessException.badRequest("DEPARTMENT_INACTIVE", dept.getName() + " is inactive.");
        }
        String nic = blankToNull(r.nic());
        if (nic != null) {
            nic = nic.toUpperCase(Locale.ROOT);
            employees.findByNic(nic).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
                throw BusinessException.conflict("NIC_EXISTS", "This NIC belongs to employee " + o.getEmployeeNo() + ".");
            });
        }
        if (r.userId() != null) {
            if (!users.existsById(r.userId())) {
                throw BusinessException.badRequest("UNKNOWN_USER", "Choose a valid user account.");
            }
            employees.findByUserId(r.userId()).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
                throw BusinessException.conflict("USER_ALREADY_LINKED", "This account is already linked to " + o.getFullName() + ".");
            });
        }
        e.setDepartment(dept);
        e.setFirstName(r.firstName().trim());
        e.setLastName(r.lastName().trim());
        e.setGender(r.gender());
        e.setNic(nic);
        e.setDateOfBirth(r.dateOfBirth());
        e.setPhone(r.phone().trim());
        String email = blankToNull(r.email());
        e.setEmail(email == null ? null : email.toLowerCase(Locale.ROOT));
        e.setAddress(blankToNull(r.address()));
        e.setDesignation(r.designation().trim());
        e.setStaffCategory(r.staffCategory());
        e.setEmploymentType(r.employmentType());
        e.setJoinDate(r.joinDate());
        e.setUserId(r.userId());
        if (r.status() != null) {
            boolean leaving = r.status() == Employee.Status.RESIGNED || r.status() == Employee.Status.TERMINATED;
            if (leaving && e.getLeaveDate() == null) {
                e.setLeaveDate(time.today());
            } else if (!leaving) {
                e.setLeaveDate(null);
            }
            e.setStatus(r.status());
        }
    }

    // ================= attendance =================

    @Transactional(readOnly = true)
    public List<AttendanceRow> register(LocalDate day) {
        Map<Long, Attendance> recorded = attendance.findByWorkDate(day).stream()
                .collect(Collectors.toMap(Attendance::getEmployeeId, Function.identity()));
        Map<Long, String> onLeave = new HashMap<>();
        Map<Long, String> typeNames = leaveTypes.findAll().stream().collect(Collectors.toMap(LeaveType::getId, LeaveType::getName));
        for (LeaveRequest l : leaves.approvedOn(day)) {
            onLeave.put(l.getEmployeeId(), typeNames.get(l.getLeaveTypeId()));
        }
        return employees.onRoll(day).stream().map(e -> {
            Attendance a = recorded.get(e.getId());
            return new AttendanceRow(e.getId(), e.getEmployeeNo(), e.getFullName(), e.getDepartment().getName(), e.getDesignation(),
                    a == null ? null : a.getStatus().name(), a == null ? null : localTime(a.getClockIn()),
                    a == null ? null : localTime(a.getClockOut()), a == null ? null : a.getRemarks(), onLeave.get(e.getId()));
        }).toList();
    }

    /** Upserts the given rows for one day. Future days are refused; times must make sense. */
    @Transactional
    public List<AttendanceRow> saveRegister(LocalDate day, AttendanceSave body) {
        if (day.isAfter(time.today())) {
            throw BusinessException.badRequest("FUTURE_DATE", "Attendance can't be recorded for a future date.");
        }
        Map<Long, Attendance> recorded = attendance.findByWorkDate(day).stream()
                .collect(Collectors.toMap(Attendance::getEmployeeId, Function.identity()));
        Set<Long> roll = employees.onRoll(day).stream().map(Employee::getId).collect(Collectors.toSet());
        Long me = CurrentUser.require().getId();
        int changed = 0;
        for (AttendanceEntry en : body.entries()) {
            if (!roll.contains(en.employeeId())) {
                throw BusinessException.badRequest("NOT_ON_ROLL", "Employee #" + en.employeeId() + " is not on the roll for " + day + ".");
            }
            boolean worked = en.status() == Attendance.Status.PRESENT || en.status() == Attendance.Status.HALF_DAY;
            if (!worked && (en.clockIn() != null || en.clockOut() != null)) {
                throw BusinessException.badRequest("TIMES_NOT_ALLOWED", "Clock times are only recorded for staff who worked.");
            }
            if (en.clockIn() != null && en.clockOut() != null && !en.clockOut().isAfter(en.clockIn())) {
                throw BusinessException.badRequest("INVALID_TIME", "Clock-out must be after clock-in.");
            }
            Attendance a = recorded.get(en.employeeId());
            if (a == null) {
                a = new Attendance();
                a.setEmployeeId(en.employeeId());
                a.setWorkDate(day);
            }
            a.setStatus(en.status());
            a.setClockIn(instant(day, en.clockIn()));
            a.setClockOut(instant(day, en.clockOut()));
            a.setRemarks(blankToNull(en.remarks()));
            a.setRecordedBy(me);
            attendance.save(a);
            changed++;
        }
        audit.recordChange("UPDATE", "ATTENDANCE", day.toString(), "Attendance for " + day + ": " + changed + " row(s) saved", null, null);
        return register(day);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDay> history(Long employeeId, LocalDate from, LocalDate to) {
        load(employeeId);
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 366) {
            throw BusinessException.badRequest("INVALID_RANGE", "Choose a range of up to one year.");
        }
        return attendance.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to).stream()
                .map(a -> new AttendanceDay(a.getWorkDate(), a.getStatus().name(), localTime(a.getClockIn()), localTime(a.getClockOut()), a.getRemarks()))
                .toList();
    }

    // ================= leave =================

    @Transactional(readOnly = true)
    public List<LeaveTypeView> leaveTypes() {
        return leaveTypes.findAll(Sort.by("id")).stream()
                .map(t -> new LeaveTypeView(t.getId(), t.getCode(), t.getName(), t.getDaysPerYear(), t.isPaid())).toList();
    }

    /** HR (employee:manage) may apply for anyone; everyone else only for their own linked employee record. */
    @Transactional
    public LeaveView apply(LeaveApply r) {
        HmsUserPrincipal user = CurrentUser.require();
        Employee e = r.employeeId() == null ? me() : load(r.employeeId());
        if (!Objects.equals(e.getUserId(), user.getId()) && !user.getPermissions().contains("employee:manage")) {
            throw BusinessException.forbidden("NOT_YOUR_RECORD", "You can only apply for your own leave.");
        }
        if (e.getStatus() != Employee.Status.ACTIVE && e.getStatus() != Employee.Status.ON_LEAVE) {
            throw BusinessException.badRequest("EMPLOYEE_INACTIVE", e.getFullName() + " is not an active employee.");
        }
        LeaveType type = leaveTypes.findById(r.leaveTypeId())
                .orElseThrow(() -> BusinessException.badRequest("UNKNOWN_LEAVE_TYPE", "Choose a valid leave type."));
        if (r.endDate().isBefore(r.startDate())) {
            throw BusinessException.badRequest("INVALID_RANGE", "The leave must end on or after its first day.");
        }
        if (r.halfDay() && !r.endDate().equals(r.startDate())) {
            throw BusinessException.badRequest("INVALID_RANGE", "A half day must start and end on the same date.");
        }
        if (r.startDate().getYear() != r.endDate().getYear()) {
            throw BusinessException.badRequest("SPANS_YEARS", "Split leave that crosses the new year into two requests.");
        }
        // shortcut: counts calendar days, not working days; add a holiday calendar if the hospital needs it
        BigDecimal days = r.halfDay() ? new BigDecimal("0.5") : BigDecimal.valueOf(ChronoUnit.DAYS.between(r.startDate(), r.endDate()) + 1);
        LeaveRequest l = new LeaveRequest();
        l.setEmployeeId(e.getId());
        l.setLeaveTypeId(type.getId());
        l.setStartDate(r.startDate());
        l.setEndDate(r.endDate());
        l.setDays(days);
        l.setReason(r.reason().trim());
        checkFits(l, type, 0L);
        leaves.saveAndFlush(l);
        audit.recordChange("CREATE", "LEAVE_REQUEST", l.getId(), e.getFullName() + ": " + type.getName() + " " + l.getStartDate() + " to " + l.getEndDate(), null, null);
        notifications.notifyPermission("leave:approve", "STAFF", "INFO", "Leave request from " + e.getFullName(),
                type.getName() + ", " + days.stripTrailingZeros().toPlainString() + " day(s) from " + l.getStartDate(), "/staff?tab=leave");
        return views(List.of(l)).get(0);
    }

    @Transactional
    public LeaveView approve(Long id, String note) {
        LeaveRequest l = lockPending(id);
        checkFits(l, leaveTypes.findById(l.getLeaveTypeId()).orElseThrow(), l.getId());
        return decide(l, LeaveRequest.Status.APPROVED, note);
    }

    @Transactional
    public LeaveView reject(Long id, String note) {
        if (note == null || note.isBlank()) {
            throw BusinessException.badRequest("NOTE_REQUIRED", "Tell the employee why the leave was not approved.");
        }
        return decide(lockPending(id), LeaveRequest.Status.REJECTED, note);
    }

    /** The applicant or HR can withdraw a request that has not started yet. */
    @Transactional
    public LeaveView cancel(Long id) {
        HmsUserPrincipal user = CurrentUser.require();
        LeaveRequest l = leaves.lockById(id).orElseThrow(() -> BusinessException.notFound("Leave request", id));
        Employee e = load(l.getEmployeeId());
        if (!Objects.equals(e.getUserId(), user.getId()) && !user.getPermissions().contains("employee:manage")) {
            throw BusinessException.forbidden("NOT_YOUR_RECORD", "You can only cancel your own leave.");
        }
        if (!LIVE.contains(l.getStatus()) || !l.getStartDate().isAfter(time.today())) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "Only pending or approved leave that has not started can be cancelled.");
        }
        l.setStatus(LeaveRequest.Status.CANCELLED);
        audit.recordChange("STATUS_CHANGE", "LEAVE_REQUEST", id, "Leave cancelled for " + e.getFullName(), null, null);
        return views(List.of(l)).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveView> searchLeave(String status, Long employeeId, int page, int size) {
        LeaveRequest.Status st;
        try {
            st = status == null || status.isBlank() ? null : LeaveRequest.Status.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown leave status.");
        }
        var result = leaves.search(st, employeeId, Paging.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "startDate", "id")));
        return new PageResponse<>(views(result.getContent()), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<LeaveBalance> balances(Long employeeId, int year) {
        load(employeeId);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        return leaveTypes.findAll(Sort.by("id")).stream().map(t -> {
            BigDecimal approved = leaves.daysTaken(employeeId, t.getId(), EnumSet.of(LeaveRequest.Status.APPROVED), from, to, 0L);
            BigDecimal pending = leaves.daysTaken(employeeId, t.getId(), EnumSet.of(LeaveRequest.Status.PENDING), from, to, 0L);
            BigDecimal remaining = t.getDaysPerYear() == null ? null : BigDecimal.valueOf(t.getDaysPerYear()).subtract(approved).subtract(pending);
            return new LeaveBalance(t.getId(), t.getName(), t.getDaysPerYear(), approved, pending, remaining);
        }).toList();
    }

    /** The signed-in user's own employee record, balances and requests (self-service). */
    @Transactional(readOnly = true)
    public MyLeave myLeave() {
        Employee e = me();
        return new MyLeave(EmployeeView.from(e), balances(e.getId(), time.today().getYear()),
                searchLeave(null, e.getId(), 0, 50).content());
    }

    // ================= helpers =================

    private Employee load(Long id) {
        return employees.findById(id).orElseThrow(() -> BusinessException.notFound("Employee", id));
    }

    private Employee me() {
        return employees.findByUserId(CurrentUser.require().getId())
                .orElseThrow(() -> BusinessException.badRequest("NO_EMPLOYEE_RECORD", "Your account is not linked to an employee record. Ask HR to link it."));
    }

    private LeaveRequest lockPending(Long id) {
        LeaveRequest l = leaves.lockById(id).orElseThrow(() -> BusinessException.notFound("Leave request", id));
        if (l.getStatus() != LeaveRequest.Status.PENDING) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "This request has already been decided.");
        }
        Employee e = load(l.getEmployeeId());
        if (Objects.equals(e.getUserId(), CurrentUser.require().getId())) {
            throw BusinessException.forbidden("SELF_APPROVAL", "Someone else must decide your own leave.");
        }
        return l;
    }

    /** No overlap with other live leave, and within the yearly entitlement for limited types. */
    private void checkFits(LeaveRequest l, LeaveType type, Long selfId) {
        if (leaves.overlaps(l.getEmployeeId(), LIVE, l.getStartDate(), l.getEndDate(), selfId)) {
            throw BusinessException.conflict("LEAVE_OVERLAP", "These dates overlap leave that is already pending or approved.");
        }
        if (type.getDaysPerYear() != null) {
            int year = l.getStartDate().getYear();
            BigDecimal taken = leaves.daysTaken(l.getEmployeeId(), type.getId(), LIVE, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), selfId);
            BigDecimal left = BigDecimal.valueOf(type.getDaysPerYear()).subtract(taken);
            if (l.getDays().compareTo(left) > 0) {
                throw BusinessException.conflict("LEAVE_BALANCE", "Only " + left.stripTrailingZeros().toPlainString() + " day(s) of "
                        + type.getName().toLowerCase(Locale.ROOT) + " left in " + year + ".");
            }
        }
    }

    private LeaveView decide(LeaveRequest l, LeaveRequest.Status status, String note) {
        l.setStatus(status);
        l.setDecidedBy(CurrentUser.require().getId());
        l.setDecidedAt(time.now());
        l.setDecisionNote(blankToNull(note));
        leaves.saveAndFlush(l);
        Employee e = load(l.getEmployeeId());
        audit.recordChange("STATUS_CHANGE", "LEAVE_REQUEST", l.getId(), "Leave " + status.name().toLowerCase(Locale.ROOT) + " for " + e.getFullName(), null, null);
        if (e.getUserId() != null) {
            notifications.notifyUsers(List.of(e.getUserId()), "STAFF", status == LeaveRequest.Status.APPROVED ? "INFO" : "WARNING",
                    "Leave " + status.name().toLowerCase(Locale.ROOT), l.getStartDate() + " to " + l.getEndDate()
                            + (l.getDecisionNote() == null ? "" : ": " + l.getDecisionNote()), "/my-leave");
        }
        return views(List.of(l)).get(0);
    }

    private List<LeaveView> views(List<LeaveRequest> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Employee> emps = employees.findAllById(list.stream().map(LeaveRequest::getEmployeeId).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, String> types = leaveTypes.findAll().stream().collect(Collectors.toMap(LeaveType::getId, LeaveType::getName));
        Map<Long, String> deciders = users.findAllById(list.stream().map(LeaveRequest::getDecidedBy).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return list.stream().map(l -> {
            Employee e = emps.get(l.getEmployeeId());
            return new LeaveView(l.getId(), e.getId(), e.getEmployeeNo(), e.getFullName(), e.getDepartment().getName(), l.getLeaveTypeId(),
                    types.get(l.getLeaveTypeId()), l.getStartDate(), l.getEndDate(), l.getDays(), l.getReason(), l.getStatus().name(),
                    l.getDecidedBy() == null ? null : deciders.get(l.getDecidedBy()), l.getDecidedAt(), l.getDecisionNote(), l.getCreatedAt());
        }).toList();
    }

    private java.time.Instant instant(LocalDate day, LocalTime t) {
        return t == null ? null : LocalDateTime.of(day, t).atZone(time.zone()).toInstant();
    }

    private LocalTime localTime(java.time.Instant i) {
        return i == null ? null : i.atZone(time.zone()).toLocalTime().withNano(0);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
