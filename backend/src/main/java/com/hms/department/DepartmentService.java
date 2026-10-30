package com.hms.department;

import com.hms.audit.AuditAction;
import com.hms.audit.Audited;
import com.hms.common.AppException;
import com.hms.common.PageResponse;
import com.hms.common.PageableFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departments;
    private final DepartmentMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<DepartmentDtos.DepartmentResponse> list(String q, Boolean active, int page, int size, String sort) {
        Page<DepartmentDtos.DepartmentResponse> result = departments.search(
                blank(q), active, PageableFactory.of(page, size, sort)
        ).map(mapper::toResponse);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public List<DepartmentDtos.DepartmentResponse> listActive() {
        return departments.findByActiveTrueOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentDtos.DepartmentResponse get(Long id) {
        return mapper.toResponse(required(id));
    }

    @Audited(action = AuditAction.CREATE, entityType = "Department")
    @Transactional
    public DepartmentDtos.DepartmentResponse create(DepartmentDtos.CreateDepartmentRequest request) {
        String code = request.code().trim().toUpperCase();
        if (departments.existsByCodeIgnoreCase(code)) {
            throw AppException.conflict("A department with code " + code + " already exists.");
        }
        if (departments.existsByNameIgnoreCase(request.name().trim())) {
            throw AppException.conflict("A department with name '" + request.name().trim() + "' already exists.");
        }
        Department dept = new Department(
                code,
                request.name().trim(),
                blank(request.description()),
                blank(request.phone()),
                blank(request.location())
        );
        return mapper.toResponse(departments.save(dept));
    }

    @Audited(action = AuditAction.UPDATE, entityType = "Department", entityIdArgument = 0)
    @Transactional
    public DepartmentDtos.DepartmentResponse update(Long id, DepartmentDtos.UpdateDepartmentRequest request) {
        Department dept = required(id);
        String name = request.name().trim();
        if (!name.equalsIgnoreCase(dept.getName()) && departments.existsByNameIgnoreCase(name)) {
            throw AppException.conflict("A department with name '" + name + "' already exists.");
        }
        dept.setName(name);
        dept.setDescription(blank(request.description()));
        dept.setPhone(blank(request.phone()));
        dept.setLocation(blank(request.location()));
        if (request.active() != null) {
            dept.setActive(request.active());
        }
        return mapper.toResponse(dept);
    }

    @Audited(action = AuditAction.DELETE, entityType = "Department", entityIdArgument = 0)
    @Transactional
    public void delete(Long id) {
        Department dept = required(id);
        dept.setActive(false);
    }

    public Department required(Long id) {
        return departments.findById(id).orElseThrow(() -> AppException.notFound("Department not found."));
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

