package com.hms.patient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MrnGeneratorServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Generates correctly formatted MRN with current year and padded sequence number")
    void nextMrn_Format() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(123L);

        MrnGeneratorService service = new MrnGeneratorService(jdbcTemplate);
        String mrn = service.nextMrn();

        int currentYear = LocalDate.now().getYear();
        assertThat(mrn).isEqualTo(String.format("MRN-%d-000123", currentYear));
    }

    @Test
    @DisplayName("MRN generation is thread-safe and produces 100% unique sequence numbers under high concurrency")
    void nextMrn_HighConcurrency() throws InterruptedException, ExecutionException {
        AtomicLong counter = new AtomicLong(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenAnswer(inv -> counter.getAndIncrement());

        MrnGeneratorService service = new MrnGeneratorService(jdbcTemplate);

        int threadCount = 20;
        int requestsPerThread = 50;
        int totalRequests = threadCount * requestsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<String> generatedMrns = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<Void>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                for (int j = 0; j < requestsPerThread; j++) {
                    generatedMrns.add(service.nextMrn());
                }
                return null;
            }));
        }

        latch.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(generatedMrns).hasSize(totalRequests);
    }
}

