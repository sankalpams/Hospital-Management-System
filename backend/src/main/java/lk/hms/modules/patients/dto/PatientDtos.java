package lk.hms.modules.patients.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.hms.modules.patients.entity.EmergencyContact;
import lk.hms.modules.patients.entity.Patient;
import lk.hms.modules.patients.entity.PatientDocument;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PatientDtos {

    /** Sri Lankan NIC: old format 9 digits + V/X, new format 12 digits. */
    public static final String NIC_REGEX = "^([0-9]{9}[VvXx]|[0-9]{12})$";
    /** Local 0XXXXXXXXX or international E.164 (+94771234567). */
    public static final String PHONE_REGEX = "^(0[0-9]{9}|\\+[1-9][0-9]{7,14})$";
    public static final String PHONE_MESSAGE = "Use 0XXXXXXXXX or international format like +94771234567.";

    private PatientDtos() {
    }

    public record EmergencyContactDto(
            Long id,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 40) String relationship,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String altPhone,
            boolean primary) {

        public static EmergencyContactDto from(EmergencyContact c) {
            return new EmergencyContactDto(c.getId(), c.getName(), c.getRelationship(), c.getPhone(), c.getAltPhone(),
                    c.isPrimaryContact());
        }
    }

    public record PatientRequest(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @Pattern(regexp = NIC_REGEX, message = "Enter a valid NIC (e.g. 851234567V or 198512345678).") String nic,
            @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9]*$", message = "Letters and numbers only.") String passportNo,
            @NotNull Patient.Gender gender,
            @NotNull @Past(message = "Date of birth must be in the past.") LocalDate dateOfBirth,
            @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Choose a valid blood group.") String bloodGroup,
            @Pattern(regexp = "^(SINGLE|MARRIED|DIVORCED|WIDOWED)$", message = "Choose a valid marital status.") String maritalStatus,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 150) String addressLine1,
            @Size(max = 150) String addressLine2,
            @Size(max = 60) String city,
            @Size(max = 40) String district,
            @Size(max = 80) String occupation,
            @Size(max = 3, message = "Up to 3 emergency contacts.") List<@Valid EmergencyContactDto> emergencyContacts,
            boolean confirmNotDuplicate,
            Long version) {
    }

    public record DuplicateCheckRequest(@NotBlank String firstName, @NotBlank String lastName,
                                        @NotNull LocalDate dateOfBirth, String phone, String nic) {
    }

    public record DuplicateCandidate(Long id, String mrn, String fullName, LocalDate dateOfBirth, String phone,
                                     String nic, String reason) {
    }

    public record StatusRequest(@NotNull Patient.Status status,
                                @PastOrPresent LocalDate deceasedAt,
                                @NotBlank @Size(min = 3, max = 255) String reason) {
    }

    public record PatientSummary(Long id, String mrn, String fullName, String gender, LocalDate dateOfBirth, int age,
                                 String phone, String nic, String status, Instant createdAt) {
        public static PatientSummary from(Patient p, LocalDate today) {
            return new PatientSummary(p.getId(), p.getMrn(), p.getFullName(), p.getGender().name(), p.getDateOfBirth(),
                    p.ageOn(today), p.getPhone(), p.getNic(), p.getStatus().name(), p.getCreatedAt());
        }
    }

    public record PatientDetail(Long id, String mrn, String firstName, String lastName, String fullName, String nic,
                                String passportNo, String gender, LocalDate dateOfBirth, int age, String bloodGroup,
                                String maritalStatus, String phone, String email, String addressLine1,
                                String addressLine2, String city, String district, String occupation, String status,
                                LocalDate deceasedAt, List<EmergencyContactDto> emergencyContacts,
                                Instant createdAt, Instant updatedAt, long version) {
        public static PatientDetail from(Patient p, LocalDate today) {
            return new PatientDetail(p.getId(), p.getMrn(), p.getFirstName(), p.getLastName(), p.getFullName(),
                    p.getNic(), p.getPassportNo(), p.getGender().name(), p.getDateOfBirth(), p.ageOn(today),
                    p.getBloodGroup(), p.getMaritalStatus(), p.getPhone(), p.getEmail(), p.getAddressLine1(),
                    p.getAddressLine2(), p.getCity(), p.getDistrict(), p.getOccupation(), p.getStatus().name(),
                    p.getDeceasedAt(), p.getEmergencyContacts().stream().map(EmergencyContactDto::from).toList(),
                    p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
        }
    }

    public record DocumentResponse(Long id, String category, String title, String originalName, String contentType,
                                   long sizeBytes, Instant createdAt) {
        public static DocumentResponse from(PatientDocument d) {
            return new DocumentResponse(d.getId(), d.getCategory().name(), d.getTitle(), d.getOriginalName(),
                    d.getContentType(), d.getSizeBytes(), d.getCreatedAt());
        }
    }
}
