package lk.hms.modules.billing;

import com.fasterxml.jackson.databind.JsonNode;
import lk.hms.support.Api;
import lk.hms.support.Fixtures;
import lk.hms.support.IntegrationTest;
import lk.hms.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class BillingIntegrationTest {

    @Autowired Api api;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    Api.Session desk, accountant;
    long patientId, ecgServiceId, dressingServiceId;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        desk = api.as("RECEPTIONIST");
        accountant = api.as("ACCOUNTANT");
        patientId = Fixtures.patient(desk);
        ecgServiceId = jdbc.queryForObject("SELECT id FROM service_catalogue WHERE code='IMG-ECG'", Long.class);          // 1200.00
        dressingServiceId = jdbc.queryForObject("SELECT id FROM service_catalogue WHERE code='PROC-DRESS'", Long.class);  // 1500.00
    }

    private long charge(long serviceId, String qty) throws Exception {
        Api.Response r = desk.post("/api/v1/charges", Map.of("patientId", patientId, "serviceId", serviceId, "quantity", qty));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body().get("id").asLong();
    }

    private JsonNode issuedInvoice(List<Long> chargeIds) throws Exception {
        long id = desk.post("/api/v1/invoices", Map.of("patientId", patientId, "chargeIds", chargeIds)).body().get("id").asLong();
        return desk.post("/api/v1/invoices/" + id + "/issue", null).body();
    }

    @Test
    void completedConsultationRaisesTheDoctorsFeeOnce() throws Exception {
        Api.Session admin = api.as("HOSPITAL_ADMIN");
        Api.Session doctor = api.as("DOCTOR");
        long doctorId = Fixtures.doctor(admin, Fixtures.department(admin), doctor.user.getId());   // fee 2500.00
        Fixtures.schedule(admin, doctorId, 1, "09:00", "12:00", 15, 4);
        long apt = Fixtures.book(desk, patientId, doctorId, "2030-06-03", "09:00").body().get("id").asLong();
        desk.post("/api/v1/appointments/" + apt + "/check-in", null);
        doctor.post("/api/v1/appointments/" + apt + "/start", null);
        long enc = doctor.get("/api/v1/patients/" + patientId + "/emr").body().at("/encounters/0/id").asLong();
        doctor.post("/api/v1/encounters/" + enc + "/diagnoses", Map.of("description", "Gastritis", "type", "PRIMARY", "certainty", "CONFIRMED"));
        assertThat(doctor.post("/api/v1/appointments/" + apt + "/complete", null).status()).isEqualTo(200);

        JsonNode unbilled = desk.get("/api/v1/patients/" + patientId + "/unbilled-charges").body();
        assertThat(unbilled).hasSize(1);
        assertThat(unbilled.get(0).get("category").asText()).isEqualTo("CONSULTATION");
        assertThat(unbilled.get(0).get("amount").decimalValue()).isEqualByComparingTo("2500.00");
    }

    @Test
    void invoiceLifecycleWithPartialPaymentsAndRefund() throws Exception {
        long ecg = charge(ecgServiceId, "1");
        long dressing = charge(dressingServiceId, "2");   // 3000.00
        Api.Response noReason = desk.post("/api/v1/invoices", Map.of("patientId", patientId, "chargeIds", List.of(ecg, dressing), "discountAmount", "200.00"));
        assertThat(noReason.code()).isEqualTo("DISCOUNT_REASON_REQUIRED");

        Api.Response draft = desk.post("/api/v1/invoices", Map.of("patientId", patientId, "chargeIds", List.of(ecg, dressing),
                "discountAmount", "200.00", "discountReason", "Senior citizen"));
        assertThat(draft.status()).isEqualTo(201);
        assertThat(draft.body().get("totalAmount").decimalValue()).isEqualByComparingTo("4000.00");   // 4200 - 200, tax 0%
        assertThat(draft.body().get("invoiceNo").asText()).matches("INV-2030-\\d{6}");
        long id = draft.body().get("id").asLong();
        assertThat(desk.post("/api/v1/invoices", Map.of("patientId", patientId, "chargeIds", List.of(ecg))).code()).isEqualTo("CHARGE_ALREADY_BILLED");
        assertThat(desk.post("/api/v1/invoices/" + id + "/payments", Map.of("amount", "10", "method", "CASH")).code()).as("draft").isEqualTo("INVOICE_NOT_PAYABLE");

        desk.post("/api/v1/invoices/" + id + "/issue", null);
        assertThat(desk.post("/api/v1/invoices/" + id + "/payments", Map.of("amount", "100", "method", "CARD")).code()).isEqualTo("REFERENCE_REQUIRED");
        assertThat(desk.post("/api/v1/invoices/" + id + "/payments", Map.of("amount", "4000.01", "method", "CASH")).code()).isEqualTo("OVERPAYMENT");

        JsonNode partial = desk.post("/api/v1/invoices/" + id + "/payments", Map.of("amount", "1500", "method", "CASH")).body();
        assertThat(partial.get("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(partial.get("balanceDue").decimalValue()).isEqualByComparingTo("2500.00");
        JsonNode paid = desk.post("/api/v1/invoices/" + id + "/payments", Map.of("amount", "2500", "method", "CARD", "referenceNo", "AUTH-7781")).body();
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.at("/payments/0/receiptNo").asText()).matches("RCP-2030-\\d{6}");

        long cashPayment = paid.at("/payments/0/id").asLong();
        assertThat(desk.post("/api/v1/payments/" + cashPayment + "/refund", Map.of("amount", "100", "reason", "Overcharged")).status())
                .as("receptionist cannot refund").isEqualTo(403);
        assertThat(accountant.post("/api/v1/payments/" + cashPayment + "/refund", Map.of("amount", "1500.01", "reason", "Too much")).code())
                .isEqualTo("REFUND_TOO_LARGE");
        JsonNode refunded = accountant.post("/api/v1/payments/" + cashPayment + "/refund", Map.of("amount", "500", "reason", "Dressing not done")).body();
        assertThat(refunded.get("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(refunded.get("amountPaid").decimalValue()).isEqualByComparingTo("3500.00");

        assertThat(accountant.post("/api/v1/invoices/" + id + "/void", Map.of("reason", "Wrong patient")).code()).isEqualTo("INVOICE_HAS_PAYMENTS");
    }

    @Test
    void voidingReleasesChargesForRebilling() throws Exception {
        long ecg = charge(ecgServiceId, "1");
        long id = issuedInvoice(List.of(ecg)).get("id").asLong();
        assertThat(desk.post("/api/v1/invoices/" + id + "/void", Map.of("reason", "Mistake")).status()).as("receptionist").isEqualTo(403);
        assertThat(accountant.post("/api/v1/invoices/" + id + "/void", Map.of("reason", "Billed to wrong scheme")).body().get("status").asText()).isEqualTo("VOID");
        assertThat(desk.get("/api/v1/patients/" + patientId + "/unbilled-charges").body()).hasSize(1);
        assertThat(desk.post("/api/v1/invoices", Map.of("patientId", patientId, "chargeIds", List.of(ecg))).status()).isEqualTo(201);
    }

    @Test
    void concurrentPaymentsCannotOverpay() throws Exception {
        long id = issuedInvoice(List.of(charge(ecgServiceId, "1"))).get("id").asLong();   // 1200.00
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Api.Response>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return desk.post("/api/v1/invoices/" + id + "/payments", Map.of("amount", "1200", "method", "CASH"));
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Api.Response> f : results) {
            if (f.get().status() == 200) ok++;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(desk.get("/api/v1/invoices/" + id).body().get("amountPaid").decimalValue()).isEqualByComparingTo("1200.00");
    }

    @Test
    void chargesForAnotherPatientAreRejected() throws Exception {
        long other = Fixtures.patient(desk);
        long ecg = charge(ecgServiceId, "1");
        assertThat(desk.post("/api/v1/invoices", Map.of("patientId", other, "chargeIds", List.of(ecg))).code()).isEqualTo("INVALID_CHARGES");
        assertThat(api.as("NURSE").get("/api/v1/invoices").status()).isEqualTo(403);
    }
}
