package lk.hms.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

/** Beans that need MockMvc; imported by {@link IntegrationTest} only. */
@TestConfiguration
public class MockMvcSupportConfig {

    @Bean
    Api api(MockMvc mvc, ObjectMapper json, TestUsers testUsers) {
        return new Api(mvc, json, testUsers);
    }
}
