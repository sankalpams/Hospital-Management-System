package com.hms.appointment;

import com.hms.common.AppException;
import com.hms.department.Department;
import com.hms.department.DepartmentRepository;
import com.hms.doctor.Doctor;
import com.hms.doctor.DoctorRepository;
import com.hms.patient.Patient;
import com.hms.patient.PatientRepository;
import com.hms.role.Role;
import com.hms.role.RoleCode;
import com.hms.security.HmsPrincipal;
import com.hms.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentStatusHistoryRepository historyRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentNumberGeneratorService numberGenerator;

    @Mock
    private DoctorScheduleRepository scheduleRepository;

    private AppointmentStateMachine stateMachine;
    private AppointmentMapper mapper;
    private AppointmentService appointmentService;

    private Doctor doctor;
    private Patient patient;
    private Department department;
    private HmsPrincipal receptionistPrincipal;
    private HmsPrincipal doctorPrincipal;

    @BeforeEach
    void setUp() {
        stateMachine = new AppointmentStateMachine();
        mapper = Mappers.getMapper(AppointmentMapper.class);
        appointmentService = new AppointmentService(
                appointmentRepository,
                historyRepository,
                doctorRepository,
                departmentRepository,
                patientRepository,
                stateMachine,
                numberGenerator,
                mapper,
                scheduleRepository
        );

        User doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setUsername("doctor");
        doctorUser.setFirstName("Nimal");
        doctorUser.setLastName("Fernando");

        department = new Department("CARD", "Cardiology", "Cardiology", "+94112345678", "Floor 1");
        department.setId(UUID.randomUUID());

        doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(doctorUser);
        doctor.setDepartment(department);
        doctor.setSpecialization("Cardiologist");

        patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setMrn("MRN-2026-000001");
        patient.setFirstName("Sunil");
        patient.setLastName("Perera");
        patient.setPhone("+94771234567");

        Role receptionistRole = new Role(RoleCode.RECEPTIONIST, "Receptionist", "Receptionist");
        receptionistPrincipal = new HmsPrincipal(UUID.randomUUID(), "reception", receptionistRole, Set.of("APPOINTMENT_READ", "APPOINTMENT_WRITE"));

        Role doctorRole = new Role(RoleCode.DOCTOR, "Doctor", "Doctor");
        doctorPrincipal = new HmsPrincipal(doctorUser.getId(), "doctor", doctorRole, Set.of("APPOINTMENT_READ", "APPOINTMENT_WRITE"));
    }

    @Test
    @DisplayName("Successfully books a valid appointment and logs status history")
    void testBookAppointmentSuccess() {
        AppointmentDtos.CreateAppointmentRequest request = new AppointmentDtos.CreateAppointmentRequest(
                doctor.getId(),
                patient.getId(),
                department.getId(),
                LocalDate.now().plusDays(2),
                LocalTime.of(9, 0),
                "Routine checkup",
                AppointmentType.STANDARD
        );

        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(appointmentRepository.existsByDoctorIdAndAppointmentDateAndStartTimeAndStatusNotIn(any(), any(), any(), any()))
                .thenReturn(false);
        when(appointmentRepository.findMaxTokenNumber(any(), any())).thenReturn(5);
        when(numberGenerator.generateNext(any())).thenReturn("APT-20261102-0006");
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(i -> {
            Appointment a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AppointmentDtos.AppointmentResponse response = appointmentService.bookAppointment(request, receptionistPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.appointmentNumber()).isEqualTo("APT-20261102-0006");
        assertThat(response.tokenNumber()).isEqualTo(6);
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);

        verify(historyRepository, times(1)).save(any(AppointmentStatusHistory.class));
    }

    @Test
    @DisplayName("Layer 2: Rejects booking with 409 Conflict when slot is already booked")
    void testServiceDoubleBookingRejection() {
        AppointmentDtos.CreateAppointmentRequest request = new AppointmentDtos.CreateAppointmentRequest(
                doctor.getId(),
                patient.getId(),
                department.getId(),
                LocalDate.now().plusDays(2),
                LocalTime.of(9, 0),
                "Routine checkup",
                AppointmentType.STANDARD
        );

        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(appointmentRepository.existsByDoctorIdAndAppointmentDateAndStartTimeAndStatusNotIn(any(), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, receptionistPrincipal))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus().value()).isEqualTo(409);
                    assertThat(appEx.getMessage()).contains("This time slot has already been booked");
                });

        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Layer 1: Translates DB Unique Constraint violation into 409 Conflict")
    void testDbConstraintDoubleBookingTranslation() {
        AppointmentDtos.CreateAppointmentRequest request = new AppointmentDtos.CreateAppointmentRequest(
                doctor.getId(),
                patient.getId(),
                department.getId(),
                LocalDate.now().plusDays(2),
                LocalTime.of(9, 0),
                "Routine checkup",
                AppointmentType.STANDARD
        );

        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(appointmentRepository.existsByDoctorIdAndAppointmentDateAndStartTimeAndStatusNotIn(any(), any(), any(), any()))
                .thenReturn(false);
        when(numberGenerator.generateNext(any())).thenReturn("APT-20261102-0001");
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint idx_appointments_no_double_booking"));

        assertThatThrownBy(() -> appointmentService.bookAppointment(request, receptionistPrincipal))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus().value()).isEqualTo(409);
                    assertThat(appEx.getMessage()).contains("This time slot has already been booked");
                });
    }

    @Test
    @DisplayName("Concurrent double-booking attempt: two threads race to book the exact same slot, one succeeds and one fails")
    void testConcurrentDoubleBookingAttempt() throws Exception {
        AppointmentDtos.CreateAppointmentRequest request = new AppointmentDtos.CreateAppointmentRequest(
                doctor.getId(),
                patient.getId(),
                department.getId(),
                LocalDate.now().plusDays(2),
                LocalTime.of(10, 0),
                "Annual checkup",
                AppointmentType.STANDARD
        );

        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(numberGenerator.generateNext(any())).thenReturn("APT-20261102-0001");

        // Simulate database mutex: only the first call to saveAndFlush succeeds, second throws unique constraint violation
        AtomicInteger callCount = new AtomicInteger(0);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(i -> {
            if (callCount.incrementAndGet() == 1) {
                Appointment a = i.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            } else {
                throw new DataIntegrityViolationException("duplicate key value violates unique constraint idx_appointments_no_double_booking");
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<Boolean> bookTask = () -> {
            barrier.await();
            try {
                appointmentService.bookAppointment(request, receptionistPrincipal);
                return true;
            } catch (AppException e) {
                if (e.getStatus().value() == 409) {
                    return false;
                }
                throw e;
            }
        };

        Future<Boolean> future1 = executor.submit(bookTask);
        Future<Boolean> future2 = executor.submit(bookTask);

        boolean result1 = future1.get(5, TimeUnit.SECONDS);
        boolean result2 = future2.get(5, TimeUnit.SECONDS);

        executor.shutdown();

        // Exactly one should succeed (true) and one should fail with 409 Conflict (false)
        assertThat(result1 ^ result2).isTrue();
    }

    @Test
    @DisplayName("Check-in changes status from SCHEDULED to CHECKED_IN")
    void testCheckIn() {
        Appointment apt = new Appointment();
        apt.setId(UUID.randomUUID());
        apt.setStatus(AppointmentStatus.SCHEDULED);
        apt.setDoctor(doctor);
        apt.setPatient(patient);
        apt.setDepartment(department);

        when(appointmentRepository.findById(apt.getId())).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        AppointmentDtos.AppointmentResponse resp = appointmentService.checkIn(apt.getId(), receptionistPrincipal);

        assertThat(resp.status()).isEqualTo(AppointmentStatus.CHECKED_IN);
        verify(historyRepository).save(any(AppointmentStatusHistory.class));
    }

    @Test
    @DisplayName("Start consultation changes status from CHECKED_IN to IN_CONSULTATION and records start time")
    void testStartConsultation() {
        Appointment apt = new Appointment();
        apt.setId(UUID.randomUUID());
        apt.setStatus(AppointmentStatus.CHECKED_IN);
        apt.setDoctor(doctor);
        apt.setPatient(patient);
        apt.setDepartment(department);

        when(appointmentRepository.findById(apt.getId())).thenReturn(Optional.of(apt));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        AppointmentDtos.AppointmentResponse resp = appointmentService.startConsultation(apt.getId(), doctorPrincipal);

        assertThat(resp.status()).isEqualTo(AppointmentStatus.IN_CONSULTATION);
        assertThat(apt.getConsultationStartTime()).isNotNull();
    }

    @Test
    @DisplayName("Doctor cannot access another doctor's private appointment details")
    void testDoctorVisibilityRestriction() {
        Doctor otherDoctor = new Doctor();
        otherDoctor.setId(UUID.randomUUID());
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherDoctor.setUser(otherUser);

        Appointment apt = new Appointment();
        apt.setId(UUID.randomUUID());
        apt.setDoctor(otherDoctor);
        apt.setPatient(patient);
        apt.setDepartment(department);

        when(appointmentRepository.findById(apt.getId())).thenReturn(Optional.of(apt));
        when(doctorRepository.findByUserId(doctorPrincipal.getUserId())).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> appointmentService.getAppointment(apt.getId(), doctorPrincipal))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus().value()).isEqualTo(403);
                    assertThat(appEx.getMessage()).contains("Doctors can only access appointments in their personal clinical schedule");
                });
    }
}

