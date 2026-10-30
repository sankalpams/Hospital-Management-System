package com.hms.doctor;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DoctorMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "doctorName", expression = "java(doctor.getUser() != null ? \"Dr. \" + doctor.getUser().getFirstName() + \" \" + doctor.getUser().getLastName() : null)")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    DoctorDtos.DoctorResponse toResponse(Doctor doctor);
}

