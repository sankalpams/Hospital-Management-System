package com.hms.appointment;

import com.hms.common.AppException;
import com.hms.common.PageResponse;
import com.hms.department.Department;
import com.hms.department.DepartmentRepository;
import com.hms.doctor.Doctor;
import com.hms.doctor.DoctorRepository;
import com.hms.patient.Patient;
import com.hms.patient.PatientRepository;
import com.hms.role.RoleCode;
import com.hms.security.HmsPrincipal;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import com.hms.billing.BillingDtos;
import com.hms.billing.ChargePostingService;
import com.hms.billing.ChargeSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final PatientRepository patientRepository;
    private final AppointmentStateMachine stateMachine;
    private final AppointmentNumberGeneratorService numberGenerator;
    private final AppointmentMapper mapper;
    private final DoctorScheduleRepository scheduleRepository;
    private final ChargePostingService chargePostingService;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentStatusHistoryRepository historyRepository,
            DoctorRepository doctorRepository,
            DepartmentRepository departmentRepository,
            PatientRepository patientRepository,
            AppointmentStateMachine stateMachine,
            AppointmentNumberGeneratorService numberGenerator,
            AppointmentMapper mapper,
            DoctorScheduleRepository scheduleRepository
    ) {
        this(appointmentRepository, historyRepository, doctorRepository, departmentRepository, patientRepository,
                stateMachine, numberGenerator, mapper, scheduleRepository, null);
    }

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentStatusHistoryRepository historyRepository,
            DoctorRepository doctorRepository,
            DepartmentRepository departmentRepository,
            PatientRepository patientRepository,
            AppointmentStateMachine stateMachine,
            AppointmentNumberGeneratorService numberGenerator,
            AppointmentMapper mapper,
            DoctorScheduleRepository scheduleRepository,
            @Lazy ChargePostingService chargePostingService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.patientRepository = patientRepository;
        this.stateMachine = stateMachine;
        this.numberGenerator = numberGenerator;
        this.mapper = mapper;
        this.scheduleRepository = scheduleRepository;
        this.chargePostingService = chargePostingService;
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse bookAppointment(AppointmentDtos.CreateAppointmentRequest req, HmsPrincipal principal) {
        Doctor doctor = doctorRepository.findById(req.doctorId())
                .orElseThrow(() -> AppException.notFound("Doctor not found: " + req.doctorId()));

        Patient patient = patientRepository.findById(req.patientId())
                .orElseThrow(() -> AppException.notFound("Patient not found: " + req.patientId()));

        Department department;
        if (req.departmentId() != null) {
            department = departmentRepository.findById(req.departmentId())
                    .orElseThrow(() -> AppException.notFound("Department not found: " + req.departmentId()));
        } else {
            department = doctor.getDepartment();
        }

        // Determine slot duration
        int slotDuration = 15;
        Optional<DoctorSchedule> schedule = scheduleRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(
                doctor.getId(), req.appointmentDate().getDayOfWeek());
        if (schedule.isPresent() && schedule.get().getSlotDurationMinutes() != null) {
            slotDuration = schedule.get().getSlotDurationMinutes();
        }
        LocalTime endTime = req.startTime().plusMinutes(slotDuration);

        // Layer 2: Service-level check for double-booking
        boolean alreadyBooked = appointmentRepository.existsByDoctorIdAndAppointmentDateAndStartTimeAndStatusNotIn(
                doctor.getId(),
                req.appointmentDate(),
                req.startTime(),
                List.of(AppointmentStatus.CANCELLED, AppointmentStatus.RESCHEDULED)
        );
        if (alreadyBooked) {
            throw AppException.conflict("This time slot has already been booked. Please choose another time.");
        }

        int tokenNumber = appointmentRepository.findMaxTokenNumber(doctor.getId(), req.appointmentDate()) + 1;
        String appointmentNumber = numberGenerator.generateNext(req.appointmentDate());

        Appointment appointment = new Appointment();
        appointment.setAppointmentNumber(appointmentNumber);
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setDepartment(department);
        appointment.setAppointmentDate(req.appointmentDate());
        appointment.setStartTime(req.startTime());
        appointment.setEndTime(endTime);
        appointment.setTokenNumber(tokenNumber);
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setType(req.type() != null ? req.type() : AppointmentType.STANDARD);
        appointment.setReason(req.reason());
        appointment.setReminderStatus(ReminderStatus.PENDING);

        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            // Layer 1 DB unique index violation translated to 409 Conflict
            log.warn("Database unique constraint prevented concurrent double booking: {}", e.getMessage());
            throw AppException.conflict("This time slot has already been booked. Please choose another time.");
        }

        AppointmentStatusHistory history = new AppointmentStatusHistory(
                appointment, null, AppointmentStatus.SCHEDULED,
                "Initial booking confirmed",
                principal != null ? principal.getUsername() : "system"
        );
        historyRepository.save(history);

        log.info("Booked appointment {} for patient {} with doctor {} on {} at {} (Token #{})",
                appointment.getAppointmentNumber(), patient.getMrn(), doctor.getUser().getUsername(),
                appointment.getAppointmentDate(), appointment.getStartTime(), appointment.getTokenNumber());

        return mapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse bookWalkIn(AppointmentDtos.WalkInAppointmentRequest req, HmsPrincipal principal) {
        Doctor doctor = doctorRepository.findById(req.doctorId())
                .orElseThrow(() -> AppException.notFound("Doctor not found: " + req.doctorId()));

        Patient patient = patientRepository.findById(req.patientId())
                .orElseThrow(() -> AppException.notFound("Patient not found: " + req.patientId()));

        Department department = req.departmentId() != null
                ? departmentRepository.findById(req.departmentId()).orElse(doctor.getDepartment())
                : doctor.getDepartment();

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime startTime = LocalTime.of(now.getHour(), now.getMinute());
        LocalTime endTime = startTime.plusMinutes(15);

        int tokenNumber = appointmentRepository.findMaxTokenNumber(doctor.getId(), today) + 1;
        String appointmentNumber = numberGenerator.generateNext(today);

        Appointment appointment = new Appointment();
        appointment.setAppointmentNumber(appointmentNumber);
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setDepartment(department);
        appointment.setAppointmentDate(today);
        appointment.setStartTime(startTime);
        appointment.setEndTime(endTime);
        appointment.setTokenNumber(tokenNumber);
        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment.setType(AppointmentType.WALK_IN);
        appointment.setReason(req.reason() != null && !req.reason().isBlank() ? req.reason() : "Walk-in consultation");
        appointment.setReminderStatus(ReminderStatus.SKIPPED);

        appointment = appointmentRepository.saveAndFlush(appointment);

        AppointmentStatusHistory history = new AppointmentStatusHistory(
                appointment, null, AppointmentStatus.CHECKED_IN,
                "Walk-in registered and automatically checked-in",
                principal != null ? principal.getUsername() : "receptionist"
        );
        historyRepository.save(history);

        return mapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse checkIn(UUID appointmentId, HmsPrincipal principal) {
        Appointment appointment = findOrThrow(appointmentId);
        stateMachine.validateTransition(appointment.getStatus(), AppointmentStatus.CHECKED_IN);

        AppointmentStatus previous = appointment.getStatus();
        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment = appointmentRepository.save(appointment);

        historyRepository.save(new AppointmentStatusHistory(
                appointment, previous, AppointmentStatus.CHECKED_IN,
                "Patient arrived at clinic and checked-in",
                principal != null ? principal.getUsername() : "system"
        ));

        return mapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse startConsultation(UUID appointmentId, HmsPrincipal principal) {
        Appointment appointment = findOrThrow(appointmentId);
        stateMachine.validateTransition(appointment.getStatus(), AppointmentStatus.IN_CONSULTATION);

        AppointmentStatus previous = appointment.getStatus();
        appointment.setStatus(AppointmentStatus.IN_CONSULTATION);
        appointment.setConsultationStartTime(OffsetDateTime.now());
        appointment = appointmentRepository.save(appointment);

        historyRepository.save(new AppointmentStatusHistory(
                appointment, previous, AppointmentStatus.IN_CONSULTATION,
                "Doctor initiated clinical consultation",
                principal != null ? principal.getUsername() : "doctor"
        ));

        return mapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse completeConsultation(UUID appointmentId) {
        return completeConsultation(appointmentId, null);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse completeConsultation(UUID appointmentId, HmsPrincipal principal) {
        Appointment appointment = findOrThrow(appointmentId);
        stateMachine.validateTransition(appointment.getStatus(), AppointmentStatus.COMPLETED);

        AppointmentStatus previous = appointment.getStatus();
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setConsultationEndTime(OffsetDateTime.now());
        appointment = appointmentRepository.save(appointment);

        historyRepository.save(new AppointmentStatusHistory(
                appointment, previous, AppointmentStatus.COMPLETED,
                "Consultation completed successfully",
                principal != null ? principal.getUsername() : "doctor"
        ));

        // Automatically post doctor's consultation fee as a charge in the central billing ledger
        if (appointment.getDoctor() != null && appointment.getDoctor().getConsultationFee() != null && chargePostingService != null) {
            try {
                chargePostingService.postCharge(new BillingDtos.PostChargeCommand(
                        appointment.getPatient().getId(),
                        ChargeSource.CONSULTATION,
                        appointment.getId(),
                        "Consultation Fee - " + appointment.getDoctor().getDoctorName() + " (" + appointment.getDoctor().getSpecialization() + ")",
                        BigDecimal.valueOf(appointment.getDoctor().getConsultationFee()),
                        1
                ));
            } catch (Exception ex) {
                log.warn("Could not post consultation charge: {}", ex.getMessage());
            }
        }

        return mapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse cancelAppointment(UUID appointmentId, AppointmentDtos.CancelAppointmentRequest req, HmsPrincipal principal) {
        Appointment appointment = findOrThrow(appointmentId);
        stateMachine.validateTransition(appointment.getStatus(), AppointmentStatus.CANCELLED);

        AppointmentStatus previous = appointment.getStatus();
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(req.reason());
        appointment = appointmentRepository.save(appointment);

        historyRepository.save(new AppointmentStatusHistory(
                appointment, previous, AppointmentStatus.CANCELLED,
                req.reason(),
                principal != null ? principal.getUsername() : "system"
        ));

        return mapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentDtos.AppointmentResponse rescheduleAppointment(UUID appointmentId, AppointmentDtos.RescheduleAppointmentRequest req, HmsPrincipal principal) {
        Appointment oldAppointment = findOrThrow(appointmentId);
        stateMachine.validateTransition(oldAppointment.getStatus(), AppointmentStatus.RESCHEDULED);

        // Check if new target slot is available
        boolean slotBooked = appointmentRepository.existsByDoctorIdAndAppointmentDateAndStartTimeAndStatusNotIn(
                oldAppointment.getDoctor().getId(),
                req.appointmentDate(),
                req.startTime(),
                List.of(AppointmentStatus.CANCELLED, AppointmentStatus.RESCHEDULED)
        );
        if (slotBooked) {
            throw AppException.conflict("The requested rescheduled time slot is already booked. Please choose another time.");
        }

        // Mark old appointment as RESCHEDULED
        AppointmentStatus previous = oldAppointment.getStatus();
        oldAppointment.setStatus(AppointmentStatus.RESCHEDULED);
        oldAppointment.setCancellationReason("Rescheduled to " + req.appointmentDate() + " " + req.startTime() + " - Reason: " + req.reason());
        appointmentRepository.save(oldAppointment);

        historyRepository.save(new AppointmentStatusHistory(
                oldAppointment, previous, AppointmentStatus.RESCHEDULED,
                "Rescheduled to " + req.appointmentDate() + " " + req.startTime() + ": " + req.reason(),
                principal != null ? principal.getUsername() : "system"
        ));

        // Create new appointment
        int slotDuration = 15;
        Optional<DoctorSchedule> schedule = scheduleRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(
                oldAppointment.getDoctor().getId(), req.appointmentDate().getDayOfWeek());
        if (schedule.isPresent() && schedule.get().getSlotDurationMinutes() != null) {
            slotDuration = schedule.get().getSlotDurationMinutes();
        }
        LocalTime endTime = req.startTime().plusMinutes(slotDuration);

        int tokenNumber = appointmentRepository.findMaxTokenNumber(oldAppointment.getDoctor().getId(), req.appointmentDate()) + 1;
        String newAppointmentNumber = numberGenerator.generateNext(req.appointmentDate());

        Appointment newAppointment = new Appointment();
        newAppointment.setAppointmentNumber(newAppointmentNumber);
        newAppointment.setDoctor(oldAppointment.getDoctor());
        newAppointment.setPatient(oldAppointment.getPatient());
        newAppointment.setDepartment(oldAppointment.getDepartment());
        newAppointment.setAppointmentDate(req.appointmentDate());
        newAppointment.setStartTime(req.startTime());
        newAppointment.setEndTime(endTime);
        newAppointment.setTokenNumber(tokenNumber);
        newAppointment.setStatus(AppointmentStatus.SCHEDULED);
        newAppointment.setType(oldAppointment.getType());
        newAppointment.setReason("Rescheduled from " + oldAppointment.getAppointmentNumber() + ": " + req.reason());
        newAppointment.setRescheduledFrom(oldAppointment);
        newAppointment.setReminderStatus(ReminderStatus.PENDING);

        try {
            newAppointment = appointmentRepository.saveAndFlush(newAppointment);
        } catch (DataIntegrityViolationException e) {
            throw AppException.conflict("The requested rescheduled time slot is already booked. Please choose another time.");
        }

        historyRepository.save(new AppointmentStatusHistory(
                newAppointment, null, AppointmentStatus.SCHEDULED,
                "Created via reschedule from " + oldAppointment.getAppointmentNumber(),
                principal != null ? principal.getUsername() : "system"
        ));

        return mapper.toResponse(newAppointment);
    }

    @Transactional(readOnly = true)
    public AppointmentDtos.AppointmentDetailResponse getAppointment(UUID id, HmsPrincipal principal) {
        Appointment appointment = findOrThrow(id);
        enforceDoctorVisibility(appointment.getDoctor().getId(), principal);

        List<AppointmentDtos.AppointmentStatusHistoryResponse> history = historyRepository
                .findByAppointmentIdOrderByChangedAtDesc(id)
                .stream()
                .map(mapper::toHistoryResponse)
                .toList();

        return new AppointmentDtos.AppointmentDetailResponse(
                mapper.toResponse(appointment),
                history
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentDtos.AppointmentResponse> searchAppointments(
            UUID doctorId,
            UUID departmentId,
            UUID patientId,
            LocalDate startDate,
            LocalDate endDate,
            AppointmentStatus status,
            String q,
            Pageable pageable,
            HmsPrincipal principal
    ) {
        UUID effectiveDoctorId = doctorId;
        if (principal != null && principal.getRole() == RoleCode.DOCTOR) {
            // Doctors only see their own appointments by default
            Optional<Doctor> currentDoctor = doctorRepository.findByUserId(principal.getUserId());
            if (currentDoctor.isPresent()) {
                effectiveDoctorId = currentDoctor.get().getId();
            }
        }

        final UUID queryDocId = effectiveDoctorId;

        Specification<Appointment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (queryDocId != null) {
                predicates.add(cb.equal(root.get("doctor").get("id"), queryDocId));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (patientId != null) {
                predicates.add(cb.equal(root.get("patient").get("id"), patientId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("appointmentDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("appointmentDate"), endDate));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                Predicate matchNum = cb.like(cb.lower(root.get("appointmentNumber")), pattern);
                Predicate matchMrn = cb.like(cb.lower(root.get("patient").get("mrn")), pattern);
                Predicate matchFirst = cb.like(cb.lower(root.get("patient").get("firstName")), pattern);
                Predicate matchLast = cb.like(cb.lower(root.get("patient").get("lastName")), pattern);
                Predicate matchPhone = cb.like(root.get("patient").get("phone"), "%" + q.trim() + "%");
                predicates.add(cb.or(matchNum, matchMrn, matchFirst, matchLast, matchPhone));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Appointment> page = appointmentRepository.findAll(spec, pageable);
        return PageResponse.from(page, mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.CalendarEventResponse> getCalendarEvents(
            UUID doctorId,
            UUID departmentId,
            LocalDate start,
            LocalDate end,
            HmsPrincipal principal
    ) {
        UUID effectiveDoctorId = doctorId;
        if (principal != null && principal.getRole() == RoleCode.DOCTOR) {
            Optional<Doctor> currentDoctor = doctorRepository.findByUserId(principal.getUserId());
            if (currentDoctor.isPresent()) {
                effectiveDoctorId = currentDoctor.get().getId();
            }
        }

        final UUID queryDocId = effectiveDoctorId;

        Specification<Appointment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (queryDocId != null) {
                predicates.add(cb.equal(root.get("doctor").get("id"), queryDocId));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("appointmentDate"), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("appointmentDate"), end));
            }
            // Exclude cancelled and rescheduled from standard calendar grid view
            predicates.add(root.get("status").in(List.of(
                    AppointmentStatus.SCHEDULED,
                    AppointmentStatus.CONFIRMED,
                    AppointmentStatus.CHECKED_IN,
                    AppointmentStatus.IN_CONSULTATION,
                    AppointmentStatus.COMPLETED,
                    AppointmentStatus.NO_SHOW
            )));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return appointmentRepository.findAll(spec).stream()
                .map(apt -> new AppointmentDtos.CalendarEventResponse(
                        apt.getId().toString(),
                        "#" + apt.getTokenNumber() + " " + apt.getPatient().getFullName() + " (" + apt.getStatus() + ")",
                        apt.getAppointmentDate().toString() + "T" + apt.getStartTime().toString(),
                        apt.getAppointmentDate().toString() + "T" + apt.getEndTime().toString(),
                        apt.getDoctor().getId(),
                        apt.getDoctor().getUser().getFullName(),
                        apt.getPatient().getId(),
                        apt.getPatient().getFullName(),
                        apt.getPatient().getMrn(),
                        apt.getStatus(),
                        apt.getTokenNumber()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentDtos.QueueItemResponse> getTodayQueue(UUID doctorId, HmsPrincipal principal) {
        UUID targetDoctorId = doctorId;
        if (targetDoctorId == null && principal != null && principal.getRole() == RoleCode.DOCTOR) {
            Optional<Doctor> currentDoc = doctorRepository.findByUserId(principal.getUserId());
            if (currentDoc.isPresent()) {
                targetDoctorId = currentDoc.get().getId();
            }
        }

        if (targetDoctorId == null) {
            return Collections.emptyList();
        }

        return appointmentRepository.findByDoctorIdAndAppointmentDateOrderByTokenNumberAsc(targetDoctorId, LocalDate.now())
                .stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED && a.getStatus() != AppointmentStatus.RESCHEDULED)
                .map(a -> new AppointmentDtos.QueueItemResponse(
                        a.getId(),
                        a.getAppointmentNumber(),
                        a.getTokenNumber(),
                        a.getPatient().getId(),
                        a.getPatient().getFullName(),
                        a.getPatient().getMrn(),
                        a.getStatus(),
                        a.getType(),
                        a.getStartTime(),
                        a.getEndTime(),
                        a.getReason()
                ))
                .toList();
    }

    private Appointment findOrThrow(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Appointment not found: " + id));
    }

    private void enforceDoctorVisibility(UUID doctorId, HmsPrincipal principal) {
        if (principal == null) return;
        if (principal.getRole() == RoleCode.DOCTOR && principal.getRole() != RoleCode.ADMIN && principal.getRole() != RoleCode.RECEPTIONIST) {
            Optional<Doctor> currentDoctor = doctorRepository.findByUserId(principal.getUserId());
            if (currentDoctor.isPresent() && !currentDoctor.get().getId().equals(doctorId)) {
                throw AppException.forbidden("Doctors can only access appointments in their personal clinical schedule.");
            }
        }
    }
}

