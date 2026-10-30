package com.hms.employee;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EmployeeMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "employeeName", expression = "java(employee.getUser() != null ? employee.getUser().getFirstName() + \" \" + employee.getUser().getLastName() : null)")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    EmployeeDtos.EmployeeResponse toResponse(Employee employee);
}

