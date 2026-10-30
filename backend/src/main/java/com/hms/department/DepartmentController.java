package com.hms.department;

import com.hms.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService departmentService;

    @GetMapping
    public PageResponse<DepartmentDtos.DepartmentResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "name,asc") String sort
    ) {
        return departmentService.list(q, active, page, size, sort);
    }

    @GetMapping("/active")
    public List<DepartmentDtos.DepartmentResponse> listActive() {
        return departmentService.listActive();
    }

    @GetMapping("/{id}")
    public DepartmentDtos.DepartmentResponse get(@PathVariable Long id) {
        return departmentService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public DepartmentDtos.DepartmentResponse create(@Valid @RequestBody DepartmentDtos.CreateDepartmentRequest request) {
        return departmentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public DepartmentDtos.DepartmentResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentDtos.UpdateDepartmentRequest request) {
        return departmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public void delete(@PathVariable Long id) {
        departmentService.delete(id);
    }
}

