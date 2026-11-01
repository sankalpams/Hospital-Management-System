package com.hms.appointment;

import com.hms.department.Department;
import com.hms.doctor.Doctor;
import com.hms.doctor.DoctorRepository;
import com.hms.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotGenerationServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DoctorScheduleRepository scheduleRepository;

    @Mock
    private ScheduleExceptionRepository exceptionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private SlotGenerationService slotService;

    private Doctor doctor;
    private UUID doctorId;
    private LocalDate testMonday;

    @BeforeEach
    void setUp() {
        doctorId = UUID.randomUUID();
        User user = new User();
        user.setFirstName("Nimal");
        user.setLastName("Fernando");

        Department department = new Department("CARD", "Cardiology", "Cardiology", "+94112345678", "Floor 1");

        doctor = new Doctor();
        doctor.setId(doctorId);
        doctor.setUser(user);
        doctor.setDepartment(department);

        testMonday = LocalDate.of(2026, 11, 2); // Monday
    }

    @Test
    @DisplayName("Generates regular 20-min slots based on weekly template")
    void testRegularSlotGeneration() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(exceptionRepository.findOverlappingExceptions(eq(doctorId), any(), any()))
                .thenReturn(Collections.emptyList());

        DoctorSchedule schedule = new DoctorSchedule(
                doctor,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                20,
                3,
                "Room 101"
        );
        when(scheduleRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(doctorId, DayOfWeek.MONDAY))
                .thenReturn(Optional.of(schedule));

        when(appointmentRepository.findByDoctorIdAndAppointmentDateAndStatusNotIn(eq(doctorId), eq(testMonday), any()))
                .thenReturn(Collections.emptyList());

        List<AppointmentDtos.TimeSlotDto> slots = slotService.generateSlots(doctorId, testMonday);

        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(0).endTime()).isEqualTo(LocalTime.of(9, 20));
        assertThat(slots.get(0).isBooked()).isFalse();

        assertThat(slots.get(1).startTime()).isEqualTo(LocalTime.of(9, 20));
        assertThat(slots.get(1).endTime()).isEqualTo(LocalTime.of(9, 40));

        assertThat(slots.get(2).startTime()).isEqualTo(LocalTime.of(9, 40));
        assertThat(slots.get(2).endTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("Returns empty slots when doctor is on LEAVE")
    void testLeaveException() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));

        ScheduleException leave = new ScheduleException(
                doctor,
                ScheduleExceptionType.LEAVE,
                testMonday.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
                testMonday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
                "Annual Medical Conference",
                null,
                null
        );
        when(exceptionRepository.findOverlappingExceptions(eq(doctorId), any(), any()))
                .thenReturn(List.of(leave));

        List<AppointmentDtos.TimeSlotDto> slots = slotService.generateSlots(doctorId, testMonday);

        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("Marks slot as BLOCKED when covered by BLOCKED_SLOT exception")
    void testBlockedSlotException() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));

        ZoneId zone = ZoneId.of("Asia/Colombo");
        OffsetDateTime blockStart = testMonday.atTime(9, 20).atZone(zone).toOffsetDateTime();
        OffsetDateTime blockEnd = testMonday.atTime(9, 40).atZone(zone).toOffsetDateTime();

        ScheduleException block = new ScheduleException(
                doctor,
                ScheduleExceptionType.BLOCKED_SLOT,
                blockStart,
                blockEnd,
                "Ward rounds",
                null,
                null
        );
        when(exceptionRepository.findOverlappingExceptions(eq(doctorId), any(), any()))
                .thenReturn(List.of(block));

        DoctorSchedule schedule = new DoctorSchedule(
                doctor,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                20,
                3,
                "Room 101"
        );
        when(scheduleRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(doctorId, DayOfWeek.MONDAY))
                .thenReturn(Optional.of(schedule));

        when(appointmentRepository.findByDoctorIdAndAppointmentDateAndStatusNotIn(eq(doctorId), eq(testMonday), any()))
                .thenReturn(Collections.emptyList());

        List<AppointmentDtos.TimeSlotDto> slots = slotService.generateSlots(doctorId, testMonday);

        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).isBlocked()).isFalse();
        assertThat(slots.get(1).isBlocked()).isTrue();
        assertThat(slots.get(1).status()).isEqualTo("BLOCKED");
        assertThat(slots.get(2).isBlocked()).isFalse();
    }

    @Test
    @DisplayName("Uses EXTRA_SESSION schedule when defined on a date")
    void testExtraSessionException() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));

        ZoneId zone = ZoneId.of("Asia/Colombo");
        OffsetDateTime extraStart = testMonday.atTime(16, 0).atZone(zone).toOffsetDateTime();
        OffsetDateTime extraEnd = testMonday.atTime(17, 0).atZone(zone).toOffsetDateTime();

        ScheduleException extra = new ScheduleException(
                doctor,
                ScheduleExceptionType.EXTRA_SESSION,
                extraStart,
                extraEnd,
                "Evening Clinic Special Session",
                30,
                2
        );
        when(exceptionRepository.findOverlappingExceptions(eq(doctorId), any(), any()))
                .thenReturn(List.of(extra));

        when(appointmentRepository.findByDoctorIdAndAppointmentDateAndStatusNotIn(eq(doctorId), eq(testMonday), any()))
                .thenReturn(Collections.emptyList());

        List<AppointmentDtos.TimeSlotDto> slots = slotService.generateSlots(doctorId, testMonday);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).startTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(slots.get(0).endTime()).isEqualTo(LocalTime.of(16, 30));
        assertThat(slots.get(1).startTime()).isEqualTo(LocalTime.of(16, 30));
        assertThat(slots.get(1).endTime()).isEqualTo(LocalTime.of(17, 0));
    }
}

