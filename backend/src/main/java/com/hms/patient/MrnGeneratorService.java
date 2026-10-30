package com.hms.patient;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class MrnGeneratorService {
    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong fallbackSequence = new AtomicLong(1000);

    public String nextMrn() {
        int year = LocalDate.now().getYear();
        long seqValue;
        try {
            Long next = jdbcTemplate.queryForObject("SELECT nextval('patient_mrn_seq')", Long.class);
            seqValue = next != null ? next : fallbackSequence.incrementAndGet();
        } catch (Exception e) {
            seqValue = fallbackSequence.incrementAndGet();
        }
        return String.format("MRN-%d-%06d", year, seqValue);
    }
}

