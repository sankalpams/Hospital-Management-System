package com.hms.appointment;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorName", expression = "java(appointment.getDoctor().getUser().getFullName())")
    @Mapping(target = "doctorSpecialization", source = "doctor.specialization")
    @Mapping(target = "consultationFee", source = "doctor.consultationFee")
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "patientName", expression = "java(appointment.getPatient().getFullName())")
    @Mapping(target = "patientMrn", source = "patient.mrn")
    @Mapping(target = "patientPhone", source = "patient.phone")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    AppointmentDtos.AppointmentResponse toResponse(Appointment appointment);

    AppointmentDtos.AppointmentStatusHistoryResponse toHistoryResponse(AppointmentStatusHistory history);

    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorName", expression = "java(schedule.getDoctor().getUser().getFullName())")
    AppointmentDtos.DoctorScheduleDto toScheduleDto(DoctorSchedule schedule);

    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorName", expression = "java(exception.getDoctor().getUser().getFullName())")
    AppointmentDtos.ScheduleExceptionDto toExceptionDto(ScheduleException exception);
}

