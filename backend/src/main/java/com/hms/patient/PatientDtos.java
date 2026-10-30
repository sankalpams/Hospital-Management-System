package com.hms.patient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PatientDtos {
    private PatientDtos() {}

    public record CreateAllergyRequest(
            @NotBlank @Size(max = 100) String allergen,
            @NotBlank @Size(max = 255) String reaction,
            @NotNull AllergySeverity severity,
            @Size(max = 255) String notes
    ) {}

    public record AllergyResponse(
            UUID id,
            String allergen,
            String reaction,
            AllergySeverity severity,
            String notes
    ) {}

    public record CreateConsentRequest(
            @NotBlank @Size(max = 255) String purpose,
            @NotNull LocalDate givenDate,
            @Size(max = 100) String witnessName,
            String notes
    ) {}

    public record ConsentResponse(
            UUID id,
            String purpose,
            LocalDate givenDate,
            LocalDate withdrawnDate,
            String witnessName,
            String notes
    ) {}

    public record DocumentResponse(
            UUID id,
            String fileName,
            String contentType,
            Long fileSize,
            DocumentCategory category,
            String notes,
            Instant createdAt
    ) {}

    public record CreatePatientRequest(
            @Pattern(regexp = "^$|^[0-9]{9}[vVxX]$|^[0-9]{12}$|^[a-zA-Z0-9]{6,12}$",
                    message = "NIC must be in valid Sri Lankan format (e.g. 123456789V or 199012345678) or passport number")
            String nic,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @PastOrPresent LocalDate dateOfBirth,
            @NotBlank @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Sex must be MALE, FEMALE, or OTHER") String sex,
            @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Phone must be in +94XXXXXXXXX format") String phone,
            @Email String email,
            @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 100) String city,
            @NotBlank @Size(max = 50) String district,
            @Size(max = 100) String guardianName,
            @Size(max = 50) String guardianRelationship,
            @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Emergency phone must be in +94XXXXXXXXX format") String guardianPhone,
            @Size(max = 20) String bloodGroup,
            String chronicConditions,
            @Size(max = 100) String insuranceProvider,
            @Size(max = 100) String insurancePolicyNumber,
            List<@Valid CreateAllergyRequest> allergies
    ) {}

    public record UpdatePatientRequest(
            @Pattern(regexp = "^$|^[0-9]{9}[vVxX]$|^[0-9]{12}$|^[a-zA-Z0-9]{6,12}$",
                    message = "NIC must be in valid Sri Lankan format (e.g. 123456789V or 199012345678) or passport number")
            String nic,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @PastOrPresent LocalDate dateOfBirth,
            @NotBlank @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Sex must be MALE, FEMALE, or OTHER") String sex,
            @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Phone must be in +94XXXXXXXXX format") String phone,
            @Email String email,
            @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 100) String city,
            @NotBlank @Size(max = 50) String district,
            @Size(max = 100) String guardianName,
            @Size(max = 50) String guardianRelationship,
            @Pattern(regexp = "^$|^\\+94[0-9]{9}$", message = "Emergency phone must be in +94XXXXXXXXX format") String guardianPhone,
            @Size(max = 20) String bloodGroup,
            String chronicConditions,
            @Size(max = 100) String insuranceProvider,
            @Size(max = 100) String insurancePolicyNumber,
            Boolean active
    ) {}

    public record PatientSummaryResponse(
            UUID id,
            String mrn,
            String nic,
            String firstName,
            String lastName,
            String fullName,
            LocalDate dateOfBirth,
            int age,
            String sex,
            String phone,
            String district,
            String bloodGroup,
            boolean active
    ) {}

    public record PatientResponse(
            UUID id,
            String mrn,
            String nic,
            String firstName,
            String lastName,
            String fullName,
            LocalDate dateOfBirth,
            int age,
            String ageSex,
            String sex,
            String phone,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String district,
            String guardianName,
            String guardianRelationship,
            String guardianPhone,
            String bloodGroup,
            String chronicConditions,
            String insuranceProvider,
            String insurancePolicyNumber,
            boolean active,
            UUID mergedIntoId,
            Instant createdAt,
            long version,
            List<AllergyResponse> allergies,
            List<DocumentResponse> documents,
            List<ConsentResponse> consents
    ) {}

    public record CheckDuplicateRequest(
            String nic,
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String phone,
            UUID excludePatientId
    ) {}

    public record MergeRequest(
            @NotNull UUID targetPatientId,
            String reason
    ) {}
}

