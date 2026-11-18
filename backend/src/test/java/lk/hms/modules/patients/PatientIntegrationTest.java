package lk.hms.modules.patients;

import lk.hms.support.Api;
import lk.hms.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PatientIntegrationTest {

    @Autowired Api api;

    /** Unique fictitious identity per call so tests never collide on duplicate rules. */
    static Map<String, Object> patient() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Map<String, Object> m = new HashMap<>();
        m.put("firstName", "Test" + r.nextInt(1_000_000));
        m.put("lastName", "Patient" + r.nextInt(1_000_000));
        m.put("gender", "FEMALE");
        m.put("dateOfBirth", LocalDate.of(1960 + r.nextInt(50), 1 + r.nextInt(12), 1 + r.nextInt(28)).toString());
        m.put("phone", "07" + (10_000_000 + r.nextInt(89_999_999)));
        m.put("emergencyContacts", List.of(Map.of("name", "Kin Person", "relationship", "Sister", "phone", "0711234567", "primary", true)));
        return m;
    }

    static String newNic() {
        return String.valueOf(100_000_000_000L + ThreadLocalRandom.current().nextLong(899_999_999_999L));
    }

    @Test
    void registrationAssignsSequentialMrnAndValidatesInput() throws Exception {
        Api.Session desk = api.as("RECEPTIONIST");
        Api.Response a = desk.post("/api/v1/patients", patient());
        Api.Response b = desk.post("/api/v1/patients", patient());
        assertThat(a.status()).as(a.toString()).isEqualTo(201);
        String mrnA = a.body().get("mrn").asText();
        String mrnB = b.body().get("mrn").asText();
        assertThat(mrnA).matches("HMS-\\d{4}-\\d{6}");
        assertThat(Long.parseLong(mrnB.substring(9))).isEqualTo(Long.parseLong(mrnA.substring(9)) + 1);
        assertThat(a.body().get("emergencyContacts").get(0).get("primary").asBoolean()).isTrue();

        Map<String, Object> bad = patient();
        bad.put("nic", "12345");
        bad.put("phone", "12");
        bad.put("dateOfBirth", LocalDate.now().plusDays(3).toString());
        Api.Response invalid = desk.post("/api/v1/patients", bad);
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().get("fieldErrors").findValuesAsText("field")).contains("nic", "phone", "dateOfBirth");
    }

    @Test
    void nicIsUniqueAndOldFormatIsNormalised() throws Exception {
        Api.Session desk = api.as("RECEPTIONIST");
        String nic = String.valueOf(100_000_000 + ThreadLocalRandom.current().nextInt(899_999_999)) + "v";
        Map<String, Object> first = patient();
        first.put("nic", nic);
        Api.Response created = desk.post("/api/v1/patients", first);
        assertThat(created.body().get("nic").asText()).isEqualTo(nic.toUpperCase());

        Map<String, Object> second = patient();
        second.put("nic", nic.toUpperCase());
        Api.Response dup = desk.post("/api/v1/patients", second);
        assertThat(dup.code()).isEqualTo("NIC_EXISTS");
        assertThat(dup.body().get("message").asText()).contains(created.body().get("mrn").asText());
    }

    @Test
    void possibleDuplicateNeedsExplicitConfirmation() throws Exception {
        Api.Session desk = api.as("RECEPTIONIST");
        Map<String, Object> original = patient();
        desk.post("/api/v1/patients", original);

        Map<String, Object> twin = new HashMap<>(original);
        twin.put("phone", "0779999999");
        Api.Response check = desk.post("/api/v1/patients/duplicate-check", Map.of(
                "firstName", original.get("firstName"), "lastName", original.get("lastName"),
                "dateOfBirth", original.get("dateOfBirth"), "phone", "0779999999"));
        assertThat(check.body()).hasSize(1);
        assertThat(check.body().get(0).get("reason").asText()).isEqualTo("Same name and date of birth");

        assertThat(desk.post("/api/v1/patients", twin).code()).isEqualTo("POSSIBLE_DUPLICATE");
        twin.put("confirmNotDuplicate", true);
        assertThat(desk.post("/api/v1/patients", twin).status()).isEqualTo(201);
    }

    @Test
    void searchFindsByMrnNicPhoneAndName() throws Exception {
        Api.Session desk = api.as("RECEPTIONIST");
        Map<String, Object> p = patient();
        String nic = newNic();
        p.put("nic", nic);
        Api.Response created = desk.post("/api/v1/patients", p);
        String mrn = created.body().get("mrn").asText();
        for (String q : List.of(mrn, nic, (String) p.get("phone"), ((String) p.get("lastName")).toLowerCase())) {
            Api.Response res = desk.get("/api/v1/patients?q=" + q);
            assertThat(res.body().get("content").findValuesAsText("mrn")).as("search by " + q).contains(mrn);
        }
    }

    @Test
    void updatesUseOptimisticLockingAndStatusChangesAreGuarded() throws Exception {
        Api.Session desk = api.as("RECEPTIONIST");
        Api.Response created = desk.post("/api/v1/patients", patient());
        long id = created.body().get("id").asLong();
        long version = created.body().get("version").asLong();

        Map<String, Object> edit = patient();
        edit.put("version", version);
        edit.put("confirmNotDuplicate", true);
        assertThat(desk.put("/api/v1/patients/" + id, edit).status()).isEqualTo(200);
        assertThat(desk.put("/api/v1/patients/" + id, edit).code()).as("stale version").isEqualTo("CONCURRENT_MODIFICATION");

        assertThat(desk.post("/api/v1/patients/" + id + "/status", Map.of("status", "DECEASED", "reason", "Recorded")).code())
                .isEqualTo("DATE_REQUIRED");
        Api.Response dead = desk.post("/api/v1/patients/" + id + "/status",
                Map.of("status", "DECEASED", "deceasedAt", LocalDate.now().toString(), "reason", "Death certificate seen"));
        assertThat(dead.body().get("status").asText()).isEqualTo("DECEASED");
        assertThat(desk.post("/api/v1/patients/" + id + "/status", Map.of("status", "ACTIVE", "reason", "Undo")).code())
                .isEqualTo("PATIENT_DECEASED");
    }

    @Test
    void documentsAcceptRealPdfsAndRejectDisguisedFiles() throws Exception {
        Api.Session desk = api.as("RECEPTIONIST");
        long id = desk.post("/api/v1/patients", patient()).body().get("id").asLong();
        byte[] pdf = "%PDF-1.4\n%fake but valid header\n".getBytes(StandardCharsets.US_ASCII);

        Api.Response ok = desk.upload("/api/v1/patients/" + id + "/documents",
                new MockMultipartFile("file", "../../referral letter.pdf", "application/pdf", pdf),
                Map.of("category", "REFERRAL", "title", "GP referral"));
        assertThat(ok.status()).as(ok.toString()).isEqualTo(201);
        assertThat(ok.body().get("originalName").asText()).isEqualTo("referral letter.pdf");

        Api.Response disguised = desk.upload("/api/v1/patients/" + id + "/documents",
                new MockMultipartFile("file", "virus.pdf", "application/pdf", "<script>alert(1)</script>".getBytes()),
                Map.of("category", "OTHER", "title", "Bad"));
        assertThat(disguised.status()).isEqualTo(415);
        assertThat(disguised.code()).isEqualTo("FILE_TYPE_NOT_ALLOWED");

        assertThat(desk.get("/api/v1/patients/" + id + "/documents").body()).hasSize(1);
        long docId = ok.body().get("id").asLong();
        Api.Response dl = desk.get("/api/v1/documents/" + docId + "/download");
        assertThat(dl.status()).isEqualTo(200);
        assertThat(dl.raw().getResponse().getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(dl.raw().getResponse().getContentAsByteArray()).isEqualTo(pdf);
    }

    @Test
    void permissionsAreEnforcedPerRole() throws Exception {
        Api.Session desk = api.as("RECEPTIONIST");
        long id = desk.post("/api/v1/patients", patient()).body().get("id").asLong();
        Api.Session pharmacist = api.as("PHARMACIST");
        assertThat(pharmacist.get("/api/v1/patients/" + id).status()).isEqualTo(200);         // demographics: yes
        assertThat(pharmacist.post("/api/v1/patients", patient()).status()).isEqualTo(403);    // register: no
        assertThat(pharmacist.get("/api/v1/patients/" + id + "/documents").status()).isEqualTo(403);
        assertThat(api.as("HR_MANAGER").get("/api/v1/patients").status()).isEqualTo(403);
    }
}
