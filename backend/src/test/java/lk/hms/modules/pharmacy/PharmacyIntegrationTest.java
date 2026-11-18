package lk.hms.modules.pharmacy;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Clock: 2030-06-03. Each test has its own medicine so stock never leaks between tests. */
@IntegrationTest
class PharmacyIntegrationTest {

    @Autowired Api api;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    Api.Session admin, desk, doctor, pharmacist;
    long encounterId, patientId, medicineId;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        admin = api.as("HOSPITAL_ADMIN");
        desk = api.as("RECEPTIONIST");
        doctor = api.as("DOCTOR");
        pharmacist = api.as("PHARMACIST");
        long doctorId = Fixtures.doctor(admin, Fixtures.department(admin), doctor.user.getId());
        Fixtures.schedule(admin, doctorId, 1, "09:00", "12:00", 15, 4);
        patientId = Fixtures.patient(desk);
        long apt = Fixtures.book(desk, patientId, doctorId, "2030-06-03", "09:00").body().get("id").asLong();
        desk.post("/api/v1/appointments/" + apt + "/check-in", null);
        doctor.post("/api/v1/appointments/" + apt + "/start", null);
        encounterId = doctor.get("/api/v1/patients/" + patientId + "/emr").body().at("/encounters/0/id").asLong();

        long category = pharmacist.post("/api/v1/medicine-categories", Map.of("name", "Cat " + Fixtures.uid())).body().get("id").asLong();
        Map<String, Object> med = new HashMap<>(Map.of("code", "MED" + Fixtures.uid(), "genericName", "Metformin", "categoryId", category,
                "dosageForm", "TABLET", "strength", "500 mg", "unit", "tablet", "unitPrice", "4.25", "reorderLevel", 4));
        med.put("prescriptionRequired", true);
        Api.Response m = pharmacist.post("/api/v1/medicines", med);
        assertThat(m.status()).as(m.toString()).isEqualTo(201);
        medicineId = m.body().get("id").asLong();
    }

    private JsonNode receive(String batch, String expiry, int qty) throws Exception {
        Api.Response r = pharmacist.post("/api/v1/stock/receipts", Map.of("medicineId", medicineId, "batchNo", batch,
                "expiryDate", expiry, "quantity", qty, "costPrice", "2.00"));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body();
    }

    private JsonNode prescribe(int qty) throws Exception {
        Api.Response r = doctor.post("/api/v1/encounters/" + encounterId + "/prescriptions", Map.of("items", List.of(
                Map.of("medicineId", medicineId, "dose", "1 tablet", "frequency", "BD", "route", "ORAL", "durationDays", 7, "quantity", qty))));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.body();
    }

    private Api.Response dispense(Api.Session who, JsonNode rx, int qty) throws Exception {
        return who.post("/api/v1/prescriptions/" + rx.get("id").asLong() + "/dispense",
                Map.of("items", List.of(Map.of("prescriptionItemId", rx.at("/items/0/id").asLong(), "quantity", qty))));
    }

    private void assertLedgerBalances() {
        Integer mismatches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM medicine_batches b
                WHERE b.medicine_id = ? AND b.quantity_on_hand <> (SELECT COALESCE(SUM(t.quantity),0) FROM stock_transactions t WHERE t.batch_id = b.id)
                """, Integer.class, medicineId);
        assertThat(mismatches).as("quantity on hand == sum of ledger movements").isZero();
    }

    @Test
    void dispensesFirstExpiryFirstAndNeverExpiredStock() throws Exception {
        receive("LATE", "2030-12-31", 10);
        receive("EARLY", "2030-07-15", 5);
        jdbc.update("""
                INSERT INTO medicine_batches(medicine_id, batch_no, expiry_date, received_date, quantity_received, quantity_on_hand, cost_price)
                VALUES (?, 'OLD', '2030-06-01', '2029-01-01', 100, 100, 1.00)
                """, medicineId);   // expired 2 days ago (and outside the ledger on purpose: it must never be touched)

        JsonNode rx = prescribe(12);
        Api.Response r = dispense(pharmacist, rx, 12);
        assertThat(r.status()).as(r.toString()).isEqualTo(200);
        assertThat(r.body().get("prescriptionStatus").asText()).isEqualTo("DISPENSED");

        Map<String, Integer> onHand = new HashMap<>();
        jdbc.query("SELECT batch_no, quantity_on_hand FROM medicine_batches WHERE medicine_id = ?",
                rs -> { onHand.put(rs.getString(1), rs.getInt(2)); }, medicineId);
        assertThat(onHand).containsEntry("EARLY", 0).containsEntry("LATE", 3).containsEntry("OLD", 100);

        JsonNode charges = desk.get("/api/v1/patients/" + patientId + "/unbilled-charges").body();
        assertThat(charges.findValuesAsText("category")).containsExactly("PHARMACY", "PHARMACY");
        assertThat(charges.findValues("amount").stream().map(JsonNode::decimalValue).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo("51.00");   // 12 x 4.25

        jdbc.update("DELETE FROM medicine_batches WHERE batch_no = 'OLD' AND medicine_id = ?", medicineId);
        assertLedgerBalances();
    }

    @Test
    void cannotExceedPrescribedOrUsableStockAndPartialDispensingIsTracked() throws Exception {
        receive("B1", "2030-12-31", 6);
        JsonNode rx = prescribe(10);
        assertThat(dispense(pharmacist, rx, 11).code()).isEqualTo("EXCEEDS_PRESCRIBED");
        assertThat(dispense(pharmacist, rx, 7).code()).isEqualTo("INSUFFICIENT_STOCK");
        Api.Response part = dispense(pharmacist, rx, 6);
        assertThat(part.body().get("prescriptionStatus").asText()).isEqualTo("PARTIALLY_DISPENSED");
        JsonNode queue = pharmacist.get("/api/v1/pharmacy/queue?patientId=" + patientId).body();
        assertThat(queue.at("/0/items/0/remaining").asInt()).isEqualTo(4);
        assertThat(queue.at("/0/items/0/stockAvailable").asInt()).isZero();

        assertThat(pharmacist.get("/api/v1/notifications?unread=true").body().get("content").findValuesAsText("title"))
                .anyMatch(t -> t.startsWith("Out of stock: Metformin"));
        assertThat(api.as("NURSE").post("/api/v1/prescriptions/" + rx.get("id").asLong() + "/dispense",
                Map.of("items", List.of(Map.of("prescriptionItemId", rx.at("/items/0/id").asLong(), "quantity", 1)))).status()).isEqualTo(403);
        assertLedgerBalances();
    }

    @Test
    void receiptAdjustmentAndWriteOffRules() throws Exception {
        assertThat(pharmacist.post("/api/v1/stock/receipts", Map.of("medicineId", medicineId, "batchNo", "X", "expiryDate", "2030-06-03",
                "quantity", 5, "costPrice", "1")).code()).isEqualTo("EXPIRED_STOCK");
        long batch = receive("ADJ1", "2030-06-20", 8).get("id").asLong();
        assertThat(pharmacist.post("/api/v1/stock/receipts", Map.of("medicineId", medicineId, "batchNo", "adj1", "expiryDate", "2031-01-01",
                "quantity", 5, "costPrice", "1")).code()).as("batch numbers are case-insensitive").isEqualTo("BATCH_EXISTS");

        assertThat(pharmacist.post("/api/v1/stock/adjustments", Map.of("batchId", batch, "quantity", -9, "reason", "Breakage")).code())
                .isEqualTo("INSUFFICIENT_STOCK");
        assertThat(pharmacist.post("/api/v1/stock/adjustments", Map.of("batchId", batch, "quantity", -2, "reason", "Breakage"))
                .body().get("quantityOnHand").asInt()).isEqualTo(6);

        JsonNode alerts = pharmacist.get("/api/v1/pharmacy/alerts").body();
        assertThat(alerts.get("expiringSoon").findValuesAsText("batchNo")).contains("ADJ1");

        clock.set(java.time.Instant.parse("2030-06-21T03:00:00Z"));   // ADJ1 has now expired
        assertThat(pharmacist.post("/api/v1/stock/write-off-expired", null).body().get("batches").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(pharmacist.get("/api/v1/medicines/" + medicineId + "/batches").body().get(0).get("quantityOnHand").asInt()).isZero();
        JsonNode tx = pharmacist.get("/api/v1/stock/transactions?medicineId=" + medicineId).body();
        assertThat(tx.get("content").findValuesAsText("type")).containsExactly("EXPIRED_WRITE_OFF", "ADJUSTMENT_OUT", "RECEIPT");
        assertLedgerBalances();
    }

    @Test
    void concurrentDispensingCannotOversell() throws Exception {
        receive("C1", "2030-12-31", 10);
        List<JsonNode> rxs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rxs.add(prescribe(8));
        }
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<Api.Response>> results = new ArrayList<>();
        for (JsonNode rx : rxs) {
            results.add(pool.submit(() -> {
                go.await();
                return dispense(pharmacist, rx, 8);
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Api.Response> f : results) {
            if (f.get().status() == 200) ok++;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity_on_hand FROM medicine_batches WHERE medicine_id = ? AND batch_no = 'C1'", Integer.class, medicineId))
                .isEqualTo(2);
        assertLedgerBalances();
    }
}
