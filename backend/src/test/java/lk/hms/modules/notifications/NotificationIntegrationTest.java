package lk.hms.modules.notifications;

import lk.hms.support.Api;
import lk.hms.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class NotificationIntegrationTest {

    @Autowired Api api;
    @Autowired NotificationService service;
    @Autowired TransactionTemplate tx;

    @Test
    void usersSeeAndClearOnlyTheirOwnNotifications() throws Exception {
        Api.Session pharmacist = api.as("PHARMACIST");
        Api.Session nurse = api.as("NURSE");
        tx.executeWithoutResult(s -> service.notifyUsers(List.of(pharmacist.user.getId()), "PHARMACY", "WARNING",
                "Low stock: Paracetamol", "12 tablets left", "/pharmacy"));

        assertThat(pharmacist.get("/api/v1/notifications/unread-count").body().get("count").asLong()).isEqualTo(1);
        long id = pharmacist.get("/api/v1/notifications?unread=true").body().at("/content/0/id").asLong();

        assertThat(nurse.get("/api/v1/notifications").body().get("totalElements").asLong()).isZero();
        assertThat(nurse.post("/api/v1/notifications/" + id + "/read", null).status()).as("cannot touch others'").isEqualTo(404);

        assertThat(pharmacist.post("/api/v1/notifications/" + id + "/read", null).status()).isEqualTo(204);
        assertThat(pharmacist.get("/api/v1/notifications/unread-count").body().get("count").asLong()).isZero();
    }

    @Test
    void permissionBroadcastReachesEveryActiveHolder() throws Exception {
        Api.Session a = api.as("PHARMACIST");
        Api.Session b = api.as("PHARMACIST");
        tx.executeWithoutResult(s -> service.notifyPermission("pharmacy:dispense", "PHARMACY", "INFO", "Broadcast", null, null));
        for (Api.Session s : List.of(a, b)) {
            assertThat(s.get("/api/v1/notifications?unread=true").body().get("content").findValuesAsText("title")).contains("Broadcast");
        }
        assertThat(a.post("/api/v1/notifications/read-all", null).body().get("updated").asInt()).isGreaterThanOrEqualTo(1);
    }
}
