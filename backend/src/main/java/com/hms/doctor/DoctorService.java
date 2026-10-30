package com.hms.doctor;

import com.hms.audit.AuditAction;
import com.hms.audit.Audited;
import com.hms.common.AppException;
import com.hms.common.PageResponse;
import com.hms.common.PageableFactory;
import com.hms.department.Department;
import com.hms.department.DepartmentService;
import com.hms.user.User;
import com.hms.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorService {
    private final DoctorRepository doctors;
    private final DepartmentService departmentService;
    private final UserRepository users;
    private final DoctorMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<DoctorDtos.DoctorResponse> list(String q, Long departmentId, Boolean active, int page, int size, String sort) {
        Page<DoctorDtos.DoctorResponse> result = doctors.search(
                blank(q), departmentId, active, PageableFactory.of(page, size, sort)
        ).map(mapper::toResponse);
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public List<DoctorDtos.DoctorResponse> listActive() {
        return doctors.findAllActive().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DoctorDtos.DoctorResponse get(UUID id) {
        return mapper.toResponse(required(id));
    }

    @Transactional(readOnly = true)
    public DoctorDtos.DoctorResponse getByUserId(UUID userId) {
        return doctors.findByUserId(userId).map(mapper::toResponse)
                .orElseThrow(() -> AppException.notFound("Doctor profile not found for user."));
    }

    @Audited(action = AuditAction.CREATE, entityType = "Doctor")
    @Transactional
    public DoctorDtos.DoctorResponse create(DoctorDtos.CreateDoctorRequest request) {
        User user = users.findByIdAndDeletedAtIsNull(request.userId())
                .orElseThrow(() -> AppException.notFound("User not found."));

        if (doctors.existsByUserId(request.userId())) {
            throw AppException.conflict("A doctor profile already exists for this user account.");
        }

        String slmc = request.slmcRegNumber().trim().toUpperCase();
        if (doctors.existsBySlmcRegNumberIgnoreCase(slmc)) {
            throw AppException.conflict("A doctor with SLMC registration " + slmc + " already exists.");
        }

        Department department = departmentService.required(request.departmentId());

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setDepartment(department);
        doctor.setSpecialization(request.specialization().trim());
        doctor.setQualification(request.qualification().trim());
        doctor.setSlmcRegNumber(slmc);
        doctor.setConsultationFee(request.consultationFee());
        doctor.setActive(true);

        return mapper.toResponse(doctors.save(doctor));
    }

    @Audited(action = AuditAction.UPDATE, entityType = "Doctor", entityIdArgument = 0)
    @Transactional
    public DoctorDtos.DoctorResponse update(UUID id, DoctorDtos.UpdateDoctorRequest request) {
        Doctor doctor = required(id);
        Department department = departmentService.required(request.departmentId());

        doctor.setDepartment(department);
        doctor.setSpecialization(request.specialization().trim());
        doctor.setQualification(request.qualification().trim());
        doctor.setConsultationFee(request.consultationFee());
        if (request.active() != null) {
            doctor.setActive(request.active());
        }

        return mapper.toResponse(doctor);
    }

    @Audited(action = AuditAction.DELETE, entityType = "Doctor", entityIdArgument = 0)
    @Transactional
    public void delete(UUID id) {
        Doctor doctor = required(id);
        doctor.setActive(false);
    }

    public Doctor required(UUID id) {
        return doctors.findById(id).orElseThrow(() -> AppException.notFound("Doctor not found."));
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

