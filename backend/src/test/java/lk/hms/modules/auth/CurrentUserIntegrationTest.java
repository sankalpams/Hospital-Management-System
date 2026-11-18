package lk.hms.modules.auth;

import lk.hms.support.Api;
import lk.hms.support.Fixtures;
import lk.hms.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class CurrentUserIntegrationTest {

    @Autowired Api api;

    @Test
    void meNamesTheLinkedDoctorProfileSoTheUiOffersMyPatientsOnlyWhenItExists() throws Exception {
        Api.Session unlinked = api.as("DOCTOR");
        assertThat(unlinked.get("/api/v1/auth/me").body().has("doctorId")).as("no profile, field omitted").isFalse();
        assertThat(unlinked.get("/api/v1/appointments?mine=true").code()).isEqualTo("NOT_A_DOCTOR");

        Api.Session admin = api.as("HOSPITAL_ADMIN");
        Api.Session linked = api.as("DOCTOR");
        long doctorId = Fixtures.doctor(admin, Fixtures.department(admin), linked.user.getId());
        assertThat(linked.get("/api/v1/auth/me").body().get("doctorId").asLong()).isEqualTo(doctorId);
        assertThat(linked.get("/api/v1/appointments?mine=true").status()).isEqualTo(200);
    }
}
