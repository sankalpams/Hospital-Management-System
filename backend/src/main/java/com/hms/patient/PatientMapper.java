package com.hms.patient;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PatientMapper {
    @Mapping(target = "fullName", expression = "java(patient.fullName())")
    @Mapping(target = "age", expression = "java(patient.getAge())")
    @Mapping(target = "ageSex", expression = "java(patient.getAgeSex())")
    PatientDtos.PatientResponse toResponse(Patient patient);

    @Mapping(target = "fullName", expression = "java(patient.fullName())")
    @Mapping(target = "age", expression = "java(patient.getAge())")
    PatientDtos.PatientSummaryResponse toSummary(Patient patient);

    PatientDtos.AllergyResponse toAllergyResponse(PatientAllergy allergy);
    PatientDtos.DocumentResponse toDocumentResponse(PatientDocument document);
    PatientDtos.ConsentResponse toConsentResponse(Consent consent);
}

