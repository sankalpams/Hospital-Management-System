package lk.hms.modules.departments.controller;

import jakarta.validation.Valid;
import lk.hms.common.api.PageResponse;
import lk.hms.modules.departments.dto.DepartmentDtos.DepartmentOption;
import lk.hms.modules.departments.dto.DepartmentDtos.DepartmentRequest;
import lk.hms.modules.departments.dto.DepartmentDtos.DepartmentResponse;
import lk.hms.modules.departments.service.DepartmentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('department:read')")
    public PageResponse<DepartmentResponse> list(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(required = false) Boolean active,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String sort) {
        return service.search(q, type, active, page, size, sort);
    }

    /** Active departments for dropdowns (appointments, staff, doctors). */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('department:read')")
    public List<DepartmentOption> options() {
        return service.options();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department:read')")
    public DepartmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('department:manage')")
    public DepartmentResponse create(@Valid @RequestBody DepartmentRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    public DepartmentResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentRequest body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('department:manage')")
    public DepartmentResponse activate(@PathVariable Long id) {
        return service.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('department:manage')")
    public DepartmentResponse deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }
}
