package lk.hms.modules.appointments.service;

import lk.hms.common.api.PageResponse;
import lk.hms.common.api.Paging;
import lk.hms.common.audit.AuditService;
import lk.hms.common.exception.BusinessException;
import lk.hms.common.security.CurrentUser;
import lk.hms.common.security.HmsUserPrincipal;
import lk.hms.common.sequence.SequenceService;
import lk.hms.common.time.HospitalTime;
import lk.hms.modules.appointments.dto.AppointmentDtos.AppointmentResponse;
import lk.hms.modules.appointments.dto.AppointmentDtos.Availability;
import lk.hms.modules.appointments.dto.AppointmentDtos.BookRequest;
import lk.hms.modules.appointments.dto.AppointmentDtos.RescheduleRequest;
import lk.hms.modules.appointments.dto.AppointmentDtos.Session;
import lk.hms.modules.appointments.dto.AppointmentDtos.Slot;
import lk.hms.modules.appointments.dto.AppointmentDtos.WalkInRequest;
import lk.hms.modules.appointments.entity.Appointment;
import lk.hms.modules.appointments.entity.Appointment.Status;
import lk.hms.modules.appointments.repository.AppointmentRepository;
import lk.hms.modules.doctors.entity.Doctor;
import lk.hms.modules.doctors.entity.DoctorSchedule;
import lk.hms.modules.doctors.entity.ScheduleException;
import lk.hms.modules.doctors.repository.DoctorRepository;
import lk.hms.modules.doctors.repository.ScheduleExceptionRepository;
import lk.hms.modules.doctors.repository.ScheduleRepository;
import lk.hms.modules.doctors.service.DoctorService;
import lk.hms.modules.emr.service.EmrService;
import lk.hms.modules.billing.service.BillingService;
import java.math.BigDecimal;
import lk.hms.modules.patients.entity.Patient;
import lk.hms.modules.patients.service.PatientService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Appointment rules (docs/01 §2D):
 * <ul>
 *   <li>Slots come only from the doctor's weekly sessions on that date, minus unavailability;
 *       a session offers at most {@code maxPatients} slots.</li>
 *   <li>Bookings for one doctor are serialised with a row lock on the doctor, and the database's
 *       unique (doctor, active slot) key is the final guard against double booking.</li>
 *   <li>A patient cannot hold two overlapping live appointments (with any doctor).</li>
 *   <li>Every transition follows {@link Status#canMoveTo}; time rules: check-in only on the day,
 *       no-show only after the slot has started, no booking in the past.</li>
 *   <li>Only the appointment's own doctor may start or complete the consultation.</li>
 * </ul>
 */
@Service
public class AppointmentService {

    private static final Map<String, String> SORTS = Map.of("slotStart", "slotStart", "status", "status", "tokenNo", "tokenNo");

    private final AppointmentRepository appointments;
    private final DoctorRepository doctors;
    private final ScheduleRepository schedules;
    private final ScheduleExceptionRepository exceptions;
    private final DoctorService doctorService;
    private final PatientService patientService;
    private final SequenceService sequences;
    private final AuditService audit;
    private final HospitalTime time;
    private final EmrService emr;
    private final BillingService billing;

    public AppointmentService(AppointmentRepository appointments, DoctorRepository doctors, ScheduleRepository schedules,
                              ScheduleExceptionRepository exceptions, DoctorService doctorService,
                              PatientService patientService, SequenceService sequences, AuditService audit,
                              HospitalTime time, EmrService emr, BillingService billing) {
        this.appointments = appointments;
        this.doctors = doctors;
        this.schedules = schedules;
        this.exceptions = exceptions;
        this.doctorService = doctorService;
        this.patientService = patientService;
        this.sequences = sequences;
        this.audit = audit;
        this.time = time;
        this.emr = emr;
        this.billing = billing;
    }

    // ================= availability =================

    @Transactional(readOnly = true)
    public Availability availability(Long doctorId, LocalDate date) {
        Doctor doctor = doctors.findDetailed(doctorId).orElseThrow(() -> BusinessException.notFound("Doctor", doctorId));
        if (!doctor.isBookable()) {
            return new Availability(doctorId, doctor.getDisplayName(), date, "Not accepting appointments", List.of());
        }
        if (date.isBefore(time.today())) {
            return new Availability(doctorId, doctor.getDisplayName(), date, "Date is in the past", List.of());
        }
        List<ScheduleException> blocks = exceptions.findCovering(doctorId, date);
        if (!blocks.isEmpty()) {
            return new Availability(doctorId, doctor.getDisplayName(), date, "Unavailable: " + blocks.get(0).getReason(), List.of());
        }
        Set<LocalDateTime> taken = appointments.findLiveForDoctor(doctorId, dayStartUtc(date), dayStartUtc(date.plusDays(1)))
                .stream().map(Appointment::getSlotStart).collect(Collectors.toSet());
        LocalDateTime nowLocal = time.toLocal(LocalDateTime.ofInstant(time.now(), java.time.ZoneOffset.UTC));

        List<Session> sessions = new ArrayList<>();
        for (DoctorSchedule s : sessionsOn(doctorId, date)) {
            List<Slot> slots = new ArrayList<>();
            int booked = 0;
            for (LocalTime t : slotTimes(s)) {
                LocalDateTime utc = time.toUtc(date.atTime(t));
                boolean isTaken = taken.contains(utc);
                if (isTaken) {
                    booked++;
                }
                boolean past = date.atTime(t).isBefore(nowLocal);
                String why = isTaken ? "Booked" : past ? "Time has passed" : null;
                slots.add(new Slot(t, time.toInstant(utc), why == null, why));
            }
            sessions.add(new Session(s.getId(), s.getStartTime(), s.getEndTime(), s.getRoomLabel(), slots.size(), booked, slots));
        }
        String blocked = sessions.isEmpty() ? "No consultation session on this day" : null;
        return new Availability(doctorId, doctor.getDisplayName(), date, blocked, sessions);
    }

    // ================= booking =================

    @Transactional
    public AppointmentResponse book(BookRequest req) {
        Appointment a = createBooking(req.patientId(), req.doctorId(), req.date(), req.time(),
                req.type() == null ? Appointment.Type.CONSULTATION : req.type(), req.reason(), null);
        if (req.confirmNow()) {
            transition(a, Status.CONFIRMED);
        }
        audit.recordChange("CREATE", "APPOINTMENT", a.getId(), "Booked " + a.getAppointmentNo() + " for "
                + a.getPatient().getMrn() + " with " + a.getDoctor().getDisplayName(), null, snapshot(a));
        return toResponse(a);
    }

    /**
     * Walk-in: the earliest slot today whose end has not yet passed and which nobody holds,
     * created already checked in.
     */
    @Transactional
    public AppointmentResponse walkIn(WalkInRequest req) {
        LocalDate today = time.today();
        doctors.lockById(req.doctorId()).orElseThrow(() -> BusinessException.notFound("Doctor", req.doctorId()));
        Set<LocalDateTime> taken = appointments.findLiveForDoctor(req.doctorId(), dayStartUtc(today), dayStartUtc(today.plusDays(1)))
                .stream().map(Appointment::getSlotStart).collect(Collectors.toSet());
        Instant now = time.now();
        LocalTime chosen = null;
        for (DoctorSchedule s : sessionsOn(req.doctorId(), today)) {
            for (LocalTime t : slotTimes(s)) {
                LocalDateTime utc = time.toUtc(today.atTime(t));
                boolean stillRunning = time.toInstant(utc.plusMinutes(s.getSlotMinutes())).isAfter(now);
                if (stillRunning && !taken.contains(utc)) {
                    chosen = t;
                    break;
                }
            }
            if (chosen != null) {
                break;
            }
        }
        if (chosen == null) {
            throw BusinessException.conflict("NO_SLOT_AVAILABLE", "No free slot remains in today's sessions for this doctor.");
        }
        Appointment a = createBooking(req.patientId(), req.doctorId(), today, chosen, Appointment.Type.WALK_IN, req.reason(), null);
        transition(a, Status.CONFIRMED);
        transition(a, Status.CHECKED_IN);
        appointments.saveAndFlush(a);
        audit.recordChange("CREATE", "APPOINTMENT", a.getId(), "Walk-in " + a.getAppointmentNo() + " checked in", null, snapshot(a));
        return toResponse(a);
    }

    @Transactional
    public AppointmentResponse reschedule(Long id, RescheduleRequest req) {
        Appointment old = load(id);
        if (!old.getStatus().canMoveTo(Status.CANCELLED)) {
            throw invalidTransition(old, "reschedule");
        }
        LocalDateTime newStart = time.toUtc(req.date().atTime(req.time()));
        if (old.getDoctor().getId().equals(req.doctorId()) && old.getSlotStart().equals(newStart)) {
            throw BusinessException.badRequest("SAME_SLOT", "Choose a different time to reschedule.");
        }
        Status carried = old.getStatus();
        Map<String, Object> before = snapshot(old);
        transition(old, Status.CANCELLED);
        old.setCancelReason("Rescheduled");  // final reason (with the new number) is set below
        appointments.saveAndFlush(old);     // frees the old slot before the new one is taken

        Appointment fresh = createBooking(old.getPatient().getId(), req.doctorId(), req.date(), req.time(), old.getType(),
                req.reason() == null || req.reason().isBlank() ? old.getReason() : req.reason(), old.getId());
        fresh.setRescheduledFromId(old.getId());
        if (carried == Status.CONFIRMED) {
            transition(fresh, Status.CONFIRMED);
        }
        old.setCancelReason("Rescheduled to " + fresh.getAppointmentNo());
        appointments.saveAndFlush(old);
        appointments.saveAndFlush(fresh);
        audit.recordChange("RESCHEDULE", "APPOINTMENT", old.getId(),
                old.getAppointmentNo() + " rescheduled to " + fresh.getAppointmentNo(), before, snapshot(fresh));
        return toResponse(fresh);
    }

    // ================= transitions =================

    @Transactional
    public AppointmentResponse confirm(Long id) {
        return change(id, Status.CONFIRMED, null);
    }

    @Transactional
    public AppointmentResponse checkIn(Long id) {
        Appointment a = load(id);
        if (!localDate(a).equals(time.today())) {
            throw BusinessException.conflict("CHECK_IN_NOT_TODAY", "Patients can only be checked in on the day of the appointment.");
        }
        return change(a, Status.CHECKED_IN, null);
    }

    @Transactional
    public AppointmentResponse start(Long id) {
        Appointment a = load(id);
        requireOwnDoctor(a);
        AppointmentResponse r = change(a, Status.IN_PROGRESS, null);
        emr.openForAppointment(a);
        return r;
    }

    @Transactional
    public AppointmentResponse complete(Long id) {
        Appointment a = load(id);
        requireOwnDoctor(a);
        emr.closeForAppointment(a);
        billing.raiseCharge(a.getPatient().getId(), "CONSULTATION", "Consultation - " + a.getDoctor().getDisplayName(),
                "APPOINTMENT", a.getId(), a.getDepartment().getId(), BigDecimal.ONE, a.getDoctor().getConsultationFee());
        return change(a, Status.COMPLETED, null);
    }

    @Transactional
    public AppointmentResponse cancel(Long id, String reason) {
        return change(id, Status.CANCELLED, reason);
    }

    @Transactional
    public AppointmentResponse noShow(Long id) {
        Appointment a = load(id);
        if (time.now().isBefore(time.toInstant(a.getSlotStart()))) {
            throw BusinessException.conflict("TOO_EARLY_FOR_NO_SHOW", "A no-show can only be recorded after the appointment time.");
        }
        return change(a, Status.NO_SHOW, null);
    }

    // ================= queries =================

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(LocalDate from, LocalDate to, Long doctorId, Long departmentId,
                                                    Long patientId, String statuses, String q, boolean mine,
                                                    int page, int size, String sort) {
        LocalDate start = from;
        LocalDate end = to;
        if (start == null && end == null) {
            if (patientId != null) {
                start = LocalDate.of(2000, 1, 1);
                end = LocalDate.of(2100, 1, 1);
            } else {
                start = time.today();
                end = time.today();
            }
        } else if (start == null) {
            start = end;
        } else if (end == null) {
            end = start;
        }
        if (end.isBefore(start)) {
            throw BusinessException.badRequest("INVALID_RANGE", "'To' date must not be before 'from' date.");
        }
        Long effectiveDoctor = doctorId;
        if (mine) {
            effectiveDoctor = doctors.findByUserId(CurrentUser.require().getId())
                    .map(Doctor::getId)
                    .orElseThrow(() -> BusinessException.forbidden("NOT_A_DOCTOR", "Your account is not linked to a doctor profile."));
        }
        String exact = q == null || q.isBlank() ? null : q.trim().toUpperCase(Locale.ROOT);
        String like = exact == null ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Sort defaultSort = Sort.by("slotStart").and(Sort.by("tokenNo"));
        return PageResponse.of(appointments.search(dayStartUtc(start), dayStartUtc(end.plusDays(1)), effectiveDoctor,
                departmentId, patientId, parseStatuses(statuses), like, exact,
                Paging.of(page, size, sort, SORTS, defaultSort)), this::toResponse);
    }

    public record DayCount(LocalDate date, long total, Map<String, Long> byStatus) {
    }

    /** Per-day counts for a calendar range (max 62 days). */
    @Transactional(readOnly = true)
    public List<DayCount> calendar(LocalDate from, LocalDate to, Long doctorId, Long departmentId) {
        if (to.isBefore(from) || to.isAfter(from.plusDays(62))) {
            throw BusinessException.badRequest("INVALID_RANGE", "Choose a range of up to 62 days.");
        }
        int offset = time.zone().getRules().getOffset(time.now()).getTotalSeconds() / 60;
        Map<LocalDate, Map<String, Long>> grouped = new LinkedHashMap<>();
        for (Object[] row : appointments.countByLocalDay(dayStartUtc(from), dayStartUtc(to.plusDays(1)), doctorId, departmentId, offset)) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            grouped.computeIfAbsent(day, k -> new LinkedHashMap<>()).put((String) row[1], ((Number) row[2]).longValue());
        }
        return grouped.entrySet().stream()
                .map(e -> new DayCount(e.getKey(), e.getValue().values().stream().mapToLong(Long::longValue).sum(), e.getValue()))
                .toList();
    }

    // ================= internals =================

    private Appointment createBooking(Long patientId, Long doctorId, LocalDate date, LocalTime slotTime,
                                      Appointment.Type type, String reason, Long excludeFromOverlap) {
        Patient patient = patientService.requireActive(patientId);
        doctors.lockById(doctorId).orElseThrow(() -> BusinessException.notFound("Doctor", doctorId));
        Doctor doctor = doctorService.requireBookable(doctorId);

        if (date.isBefore(time.today())) {
            throw BusinessException.badRequest("DATE_IN_PAST", "Appointments cannot be booked in the past.");
        }
        if (exceptions.blocks(doctorId, date)) {
            throw BusinessException.conflict("DOCTOR_UNAVAILABLE", doctor.getDisplayName() + " is unavailable on " + date + ".");
        }
        DoctorSchedule session = sessionsOn(doctorId, date).stream()
                .filter(s -> slotTimes(s).contains(slotTime))
                .findFirst()
                .orElseThrow(() -> BusinessException.conflict("SLOT_NOT_IN_SCHEDULE",
                        slotTime + " is not a consultation slot for " + doctor.getDisplayName() + " on " + date + "."));
        LocalDateTime startUtc = time.toUtc(date.atTime(slotTime));
        LocalDateTime endUtc = startUtc.plusMinutes(session.getSlotMinutes());
        if (type != Appointment.Type.WALK_IN && time.toInstant(startUtc).isBefore(time.now())) {
            throw BusinessException.conflict("SLOT_IN_PAST", "That time has already passed today.");
        }
        if (!appointments.findLiveForDoctor(doctorId, startUtc, startUtc.plusSeconds(1)).isEmpty()) {
            throw BusinessException.conflict("SLOT_TAKEN", "That slot has just been booked. Choose another time.");
        }
        if (!appointments.findPatientOverlaps(patientId, startUtc, endUtc, excludeFromOverlap).isEmpty()) {
            throw BusinessException.conflict("PATIENT_DOUBLE_BOOKED", patient.getFullName() + " already has an appointment at that time.");
        }

        Appointment a = new Appointment();
        a.setAppointmentNo(sequences.nextFormatted("APT", date.getYear(), 6));
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setDepartment(doctor.getEmployee().getDepartment());
        a.setSlotStart(startUtc);
        a.setSlotEnd(endUtc);
        a.setTokenNo(appointments.maxToken(doctorId, dayStartUtc(date), dayStartUtc(date.plusDays(1))) + 1);
        a.setType(type);
        a.setStatus(Status.PENDING);
        a.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        a.setBookedBy(CurrentUser.require().getId());
        try {
            appointments.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("SLOT_TAKEN", "That slot has just been booked. Choose another time.");
        }
        return a;
    }

    private AppointmentResponse change(Long id, Status target, String reason) {
        return change(load(id), target, reason);
    }

    private AppointmentResponse change(Appointment a, Status target, String reason) {
        Map<String, Object> before = snapshot(a);
        if (target == Status.CANCELLED) {
            if (reason == null || reason.isBlank()) {
                throw BusinessException.badRequest("REASON_REQUIRED", "A cancellation reason is required.");
            }
            a.setCancelReason(reason.trim());
        }
        transition(a, target);
        appointments.saveAndFlush(a);
        audit.recordChange("STATUS_CHANGE", "APPOINTMENT", a.getId(),
                a.getAppointmentNo() + ": " + before.get("status") + " -> " + target + (reason == null ? "" : " (" + reason.trim() + ")"),
                before, snapshot(a));
        return toResponse(a);
    }

    private void transition(Appointment a, Status target) {
        if (!a.getStatus().canMoveTo(target)) {
            throw invalidTransition(a, target.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        Instant now = time.now();
        switch (target) {
            case CONFIRMED -> a.setConfirmedAt(now);
            case CHECKED_IN -> a.setCheckedInAt(now);
            case IN_PROGRESS -> a.setStartedAt(now);
            case COMPLETED -> a.setCompletedAt(now);
            case CANCELLED -> a.setCancelledAt(now);
            default -> { }
        }
        a.setStatus(target);
    }

    private static BusinessException invalidTransition(Appointment a, String action) {
        return BusinessException.conflict("INVALID_STATUS_TRANSITION",
                "Cannot " + action + " an appointment that is " + a.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
    }

    private void requireOwnDoctor(Appointment a) {
        HmsUserPrincipal me = CurrentUser.require();
        boolean own = doctors.findByUserId(me.getId()).map(d -> d.getId().equals(a.getDoctor().getId())).orElse(false);
        if (!own) {
            throw BusinessException.forbidden("NOT_YOUR_APPOINTMENT", "Only the treating doctor can start or complete this consultation.");
        }
    }

    private List<DoctorSchedule> sessionsOn(Long doctorId, LocalDate date) {
        return schedules.findByDoctorIdAndDayOfWeekAndActiveTrue(doctorId, date.getDayOfWeek().getValue()).stream()
                .filter(s -> s.appliesOn(date))
                .sorted((x, y) -> x.getStartTime().compareTo(y.getStartTime()))
                .toList();
    }

    /** Slot start times of a session, capped at maxPatients. */
    static List<LocalTime> slotTimes(DoctorSchedule s) {
        List<LocalTime> out = new ArrayList<>();
        LocalTime t = s.getStartTime();
        while (!t.plusMinutes(s.getSlotMinutes()).isAfter(s.getEndTime()) && out.size() < s.getMaxPatients()) {
            out.add(t);
            LocalTime next = t.plusMinutes(s.getSlotMinutes());
            if (next.isBefore(t)) {
                break;   // wrapped past midnight
            }
            t = next;
        }
        return out;
    }

    private LocalDateTime dayStartUtc(LocalDate date) {
        return time.toUtc(date.atStartOfDay());
    }

    private LocalDate localDate(Appointment a) {
        return time.toLocal(a.getSlotStart()).toLocalDate();
    }

    private Appointment load(Long id) {
        return appointments.findDetailed(id).orElseThrow(() -> BusinessException.notFound("Appointment", id));
    }

    private static Set<Status> parseStatuses(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.allOf(Status.class);
        }
        try {
            return Arrays.stream(raw.split(",")).map(s -> Status.valueOf(s.trim().toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Status.class)));
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("INVALID_FILTER", "Unknown appointment status in filter.");
        }
    }

    /** Actions the state machine and time rules allow now (the UI additionally checks permissions). */
    private List<String> allowedActions(Appointment a) {
        List<String> out = new ArrayList<>();
        Status s = a.getStatus();
        boolean today = localDate(a).equals(time.today());
        boolean started = !time.now().isBefore(time.toInstant(a.getSlotStart()));
        if (s.canMoveTo(Status.CONFIRMED)) out.add("confirm");
        if (s.canMoveTo(Status.CHECKED_IN) && today) out.add("checkIn");
        if (s.canMoveTo(Status.IN_PROGRESS)) out.add("start");
        if (s.canMoveTo(Status.COMPLETED)) out.add("complete");
        if (s.canMoveTo(Status.CANCELLED)) {
            out.add("cancel");
            out.add("reschedule");
        }
        if (s.canMoveTo(Status.NO_SHOW) && started) out.add("noShow");
        return out;
    }

    private AppointmentResponse toResponse(Appointment a) {
        LocalDateTime local = time.toLocal(a.getSlotStart());
        Patient p = a.getPatient();
        Doctor d = a.getDoctor();
        return new AppointmentResponse(a.getId(), a.getAppointmentNo(), time.toInstant(a.getSlotStart()),
                time.toInstant(a.getSlotEnd()), local.toLocalDate(), local.toLocalTime(), a.getTokenNo(),
                a.getType().name(), a.getStatus().name(), a.getReason(), a.getCancelReason(), a.getRescheduledFromId(),
                p.getId(), p.getMrn(), p.getFullName(), p.getPhone(), d.getId(), d.getDisplayName(), d.getSpecialization(),
                a.getDepartment().getId(), a.getDepartment().getName(), d.getConsultationFee(), a.getCheckedInAt(),
                a.getStartedAt(), a.getCompletedAt(), a.getCreatedAt(), allowedActions(a), a.getVersion());
    }

    private Map<String, Object> snapshot(Appointment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appointmentNo", a.getAppointmentNo());
        m.put("patient", a.getPatient().getMrn());
        m.put("doctorId", a.getDoctor().getId());
        m.put("slotStartLocal", time.toLocal(a.getSlotStart()).toString());
        m.put("status", a.getStatus().name());
        m.put("token", a.getTokenNo());
        if (a.getCancelReason() != null) {
            m.put("cancelReason", a.getCancelReason());
        }
        return m;
    }
}
