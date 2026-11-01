package com.hms.appointment;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AppointmentNumberGeneratorService {

    private final JdbcTemplate jdbcTemplate;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String generateNext(LocalDate date) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('appointment_number_seq')", Long.class);
        long sequenceNumber = seq != null ? seq : 1L;
        String datePart = (date != null ? date : LocalDate.now()).format(DATE_FORMAT);
        return String.format("APT-%s-%04d", datePart, sequenceNumber % 10000);
    }
}

