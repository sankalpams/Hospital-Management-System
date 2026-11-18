package lk.hms.modules.doctors.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lk.hms.modules.doctors.entity.Doctor;
import lk.hms.modules.doctors.entity.DoctorSchedule;
import lk.hms.modules.doctors.entity.ScheduleException;
import lk.hms.modules.patients.dto.PatientDtos;
import lk.hms.modules.staff.entity.Employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public final class DoctorDtos {

    private DoctorDtos() {
    }

    public record DoctorRequest(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Choose a valid value.") String gender,
            @Pattern(regexp = PatientDtos.NIC_REGEX, message = "Enter a valid NIC.") String nic,
            @NotBlank @Pattern(regexp = PatientDtos.PHONE_REGEX, message = PatientDtos.PHONE_MESSAGE) String phone,
            @Email @Size(max = 120) String email,
            @NotNull Long departmentId,
            @NotNull Employee.EmploymentType employmentType,
            @NotNull @PastOrPresent LocalDate joinDate,
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9/-]+$", message = "Letters, numbers, / and - only.") String registrationNo,
            @NotBlank @Size(max = 100) String specialization,
            @Size(max = 255) String qualifications,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer = 10, fraction = 2) BigDecimal consultationFee,
            boolean acceptingPatients,
            Employee.Status status,
            Long userId,
            Long version) {
    }

    public record DoctorResponse(Long id, Long employeeId, String employeeNo, String displayName, String firstName,
                                 String lastName, String gender, String nic, String phone, String email,
                                 Long departmentId, String departmentName, String employmentType, LocalDate joinDate,
                                 String registrationNo, String specialization, String qualifications,
                                 BigDecimal consultationFee, boolean acceptingPatients, String status, boolean bookable,
                                 Long userId, long version) {
        public static DoctorResponse from(Doctor d) {
            Employee e = d.getEmployee();
            return new DoctorResponse(d.getId(), e.getId(), e.getEmployeeNo(), d.getDisplayName(), e.getFirstName(),
                    e.getLastName(), e.getGender(), e.getNic(), e.getPhone(), e.getEmail(), e.getDepartment().getId(),
                    e.getDepartment().getName(), e.getEmploymentType().name(), e.getJoinDate(), d.getRegistrationNo(),
                    d.getSpecialization(), d.getQualifications(), d.getConsultationFee(), d.isAcceptingPatients(),
                    e.getStatus().name(), d.isBookable(), e.getUserId(), e.getVersion());
        }
    }

    public record DoctorOption(Long id, String displayName, String specialization, Long departmentId,
                               String departmentName, BigDecimal consultationFee) {
        public static DoctorOption from(Doctor d) {
            return new DoctorOption(d.getId(), d.getDisplayName(), d.getSpecialization(),
                    d.getEmployee().getDepartment().getId(), d.getEmployee().getDepartment().getName(), d.getConsultationFee());
        }
    }

    public record ScheduleRequest(
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @Min(5) @Max(120) Integer slotMinutes,
            @NotNull @Min(1) @Max(200) Integer maxPatients,
            @Size(max = 40) String roomLabel,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Boolean active) {
    }

    public record ScheduleResponse(Long id, int dayOfWeek, LocalTime startTime, LocalTime endTime, int slotMinutes,
                                   int maxPatients, int slotsPerSession, String roomLabel, LocalDate effectiveFrom,
                                   LocalDate effectiveTo, boolean active) {
        public static ScheduleResponse from(DoctorSchedule s) {
            int slots = (int) (java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / s.getSlotMinutes());
            return new ScheduleResponse(s.getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getSlotMinutes(),
                    s.getMaxPatients(), Math.min(slots, s.getMaxPatients()), s.getRoomLabel(), s.getEffectiveFrom(),
                    s.getEffectiveTo(), s.isActive());
        }
    }

    public record ExceptionRequest(@NotNull LocalDate dateFrom, @NotNull LocalDate dateTo,
                                   @NotBlank @Size(max = 200) String reason) {
    }

    public record ExceptionResponse(Long id, LocalDate dateFrom, LocalDate dateTo, String reason) {
        public static ExceptionResponse from(ScheduleException x) {
            return new ExceptionResponse(x.getId(), x.getDateFrom(), x.getDateTo(), x.getReason());
        }
    }
}
