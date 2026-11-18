package lk.hms.modules.staff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lk.hms.modules.patients.dto.PatientDtos;
import lk.hms.modules.staff.entity.Attendance;
import lk.hms.modules.staff.entity.Employee;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class StaffDtos {

    private StaffDtos() {
    }

    /** Non-doctor staff. Doctors are created on the Doctors page, which adds their clinical profile. */
    public record EmployeeRequest(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Choose a valid value.") String gender,
            @Pattern(regexp = PatientDtos.NIC_REGEX, message = "Enter a valid NIC.") String nic,
            @Past LocalDate dateOfBirth,
            @NotBlank @Pattern(regexp = PatientDtos.PHONE_REGEX, message = PatientDtos.PHONE_MESSAGE) String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 255) String address,
            @NotNull Long departmentId,
            @NotBlank @Size(max = 80) String designation,
            @NotNull Employee.Category staffCategory,
            @NotNull Employee.EmploymentType employmentType,
            @NotNull @PastOrPresent LocalDate joinDate,
            Employee.Status status,
            Long userId,
            Long version) {
    }

    public record EmployeeView(Long id, String employeeNo, String fullName, String firstName, String lastName, String gender,
                               String nic, LocalDate dateOfBirth, String phone, String email, String address,
                               Long departmentId, String departmentName, String designation, String staffCategory,
                               String employmentType, LocalDate joinDate, LocalDate leaveDate, String status,
                               Long userId, long version) {
        public static EmployeeView from(Employee e) {
            return new EmployeeView(e.getId(), e.getEmployeeNo(), e.getFullName(), e.getFirstName(), e.getLastName(), e.getGender(),
                    e.getNic(), e.getDateOfBirth(), e.getPhone(), e.getEmail(), e.getAddress(), e.getDepartment().getId(),
                    e.getDepartment().getName(), e.getDesignation(), e.getStaffCategory().name(), e.getEmploymentType().name(),
                    e.getJoinDate(), e.getLeaveDate(), e.getStatus().name(), e.getUserId(), e.getVersion());
        }
    }

    // ---- attendance

    public record AttendanceEntry(@NotNull Long employeeId, @NotNull Attendance.Status status,
                                  LocalTime clockIn, LocalTime clockOut, @Size(max = 255) String remarks) {
    }

    public record AttendanceSave(@NotNull @Size(max = 2000) List<@Valid AttendanceEntry> entries) {
    }

    /** One register row; {@code status} is null when nothing is recorded yet. */
    public record AttendanceRow(Long employeeId, String employeeNo, String fullName, String departmentName, String designation,
                                String status, LocalTime clockIn, LocalTime clockOut, String remarks, String onLeave) {
    }

    public record AttendanceDay(LocalDate workDate, String status, LocalTime clockIn, LocalTime clockOut, String remarks) {
    }

    // ---- leave

    public record LeaveTypeView(Long id, String code, String name, Integer daysPerYear, boolean paid) {
    }

    public record LeaveApply(Long employeeId, @NotNull Long leaveTypeId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                             boolean halfDay, @NotBlank @Size(min = 3, max = 255) String reason) {
    }

    public record DecisionRequest(@Size(max = 255) String note) {
    }

    public record LeaveView(Long id, Long employeeId, String employeeNo, String employeeName, String departmentName,
                            Long leaveTypeId, String leaveType, LocalDate startDate, LocalDate endDate, BigDecimal days,
                            String reason, String status, String decidedByName, Instant decidedAt, String decisionNote,
                            Instant createdAt) {
    }

    public record LeaveBalance(Long leaveTypeId, String leaveType, Integer entitlement, BigDecimal approved,
                               BigDecimal pending, BigDecimal remaining) {
    }

    public record MyLeave(EmployeeView employee, List<LeaveBalance> balances, List<LeaveView> requests) {
    }
}
