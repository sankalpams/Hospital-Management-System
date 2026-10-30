package com.hms.doctor;

import com.hms.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {
    private final DoctorService doctorService;

    @GetMapping
    public PageResponse<DoctorDtos.DoctorResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "user.lastName,asc") String sort
    ) {
        return doctorService.list(q, departmentId, active, page, size, sort);
    }

    @GetMapping("/active")
    public List<DoctorDtos.DoctorResponse> listActive() {
        return doctorService.listActive();
    }

    @GetMapping("/{id}")
    public DoctorDtos.DoctorResponse get(@PathVariable UUID id) {
        return doctorService.get(id);
    }

    @GetMapping("/by-user/{userId}")
    public DoctorDtos.DoctorResponse getByUserId(@PathVariable UUID userId) {
        return doctorService.getByUserId(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public DoctorDtos.DoctorResponse create(@Valid @RequestBody DoctorDtos.CreateDoctorRequest request) {
        return doctorService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public DoctorDtos.DoctorResponse update(@PathVariable UUID id, @Valid @RequestBody DoctorDtos.UpdateDoctorRequest request) {
        return doctorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public void delete(@PathVariable UUID id) {
        doctorService.delete(id);
    }
}

