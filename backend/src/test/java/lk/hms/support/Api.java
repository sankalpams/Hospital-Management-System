package lk.hms.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import lk.hms.modules.users.entity.User;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Test helper: sign in through the real /auth/login endpoint, then call APIs with that session.
 * Registered by {@link MockMvcSupportConfig} (only in MockMvc contexts), not by component scanning.
 */
public class Api {

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final TestUsers testUsers;

    public Api(MockMvc mvc, ObjectMapper json, TestUsers testUsers) {
        this.mvc = mvc;
        this.json = json;
        this.testUsers = testUsers;
    }

    /** A signed-in session for a fresh user with the given role. */
    public Session as(String roleCode) throws Exception {
        User u = testUsers.active(roleCode);
        return login(u.getUsername(), TestUsers.PASSWORD, u);
    }

    public Session login(String username, String password, User user) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
        if (res.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login failed (" + res.getResponse().getStatus() + "): "
                    + res.getResponse().getContentAsString());
        }
        return new Session(res.getResponse().getCookie("HMS_SESSION"), user);
    }

    public final class Session {
        public final Cookie cookie;
        public final User user;

        Session(Cookie cookie, User user) {
            this.cookie = cookie;
            this.user = user;
        }

        public Response get(String url) throws Exception {
            return send(MockMvcRequestBuilders.get(url));
        }

        public Response post(String url, Object body) throws Exception {
            return send(json(MockMvcRequestBuilders.post(url), body));
        }

        public Response put(String url, Object body) throws Exception {
            return send(json(MockMvcRequestBuilders.put(url), body));
        }

        public Response delete(String url) throws Exception {
            return send(MockMvcRequestBuilders.delete(url).with(csrf()));
        }

        public Response upload(String url, MockMultipartFile file, Map<String, String> params) throws Exception {
            var builder = MockMvcRequestBuilders.multipart(url).file(file);
            builder.with(csrf());
            params.forEach(builder::param);
            return send(builder);
        }

        private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Object body) throws Exception {
            b.with(csrf()).contentType(MediaType.APPLICATION_JSON);
            if (body != null) {
                b.content(Api.this.json.writeValueAsString(body));
            }
            return b;
        }

        private Response send(MockHttpServletRequestBuilder b) throws Exception {
            MvcResult r = mvc.perform(b.cookie(cookie)).andReturn();
            String text = r.getResponse().getContentAsString();
            JsonNode body = text.isEmpty() || !text.startsWith("{") && !text.startsWith("[") ? null : json.readTree(text);
            return new Response(r.getResponse().getStatus(), body, r);
        }
    }

    public record Response(int status, JsonNode body, MvcResult raw) {
        public String code() {
            return body == null || body.get("code") == null ? null : body.get("code").asText();
        }

        @Override
        public String toString() {
            return status + " " + body;
        }
    }
}
