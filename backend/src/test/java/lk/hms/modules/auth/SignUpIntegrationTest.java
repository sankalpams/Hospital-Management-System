package lk.hms.modules.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.hms.modules.users.entity.User;
import lk.hms.modules.users.entity.UserStatus;
import lk.hms.modules.users.repository.UserRepository;
import lk.hms.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SignUpIntegrationTest {

    private static final String PASSWORD = "Harbour-Lantern-482";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    private ResultActions signUp(String username, String role) throws Exception {
        return mvc.perform(post("/api/v1/auth/sign-up").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "fullName", "Test Person",
                        "role", role, "password", PASSWORD))));
    }

    private ResultActions login(String username) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))));
    }

    private static String newName() {
        return "su." + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void requestStaysPendingUntilAnAdministratorActivatesIt() throws Exception {
        String name = newName();
        signUp(name, "NURSE").andExpect(status().isAccepted());

        User created = users.findByUsername(name).orElseThrow();
        assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(created.isMustChangePassword()).isFalse();
        login(name).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));

        created.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(created);
        login(name).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("NURSE"));
    }

    @Test
    void administratorRolesCannotBeRequested() throws Exception {
        for (String role : new String[] {"SUPER_ADMIN", "HOSPITAL_ADMIN"}) {
            signUp(newName(), role).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ROLE_NOT_REQUESTABLE"));
        }
    }

    @Test
    void weakPasswordAndTakenUsernameAreRefused() throws Exception {
        String name = newName();
        mvc.perform(post("/api/v1/auth/sign-up").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", name, "fullName", "Weak",
                                "role", "NURSE", "password", "password"))))
                .andExpect(status().isBadRequest());
        signUp(name, "NURSE").andExpect(status().isAccepted());
        signUp(name, "NURSE").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void signUpRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/v1/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
