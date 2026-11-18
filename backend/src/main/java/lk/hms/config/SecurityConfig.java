package lk.hms.config;

import lk.hms.common.security.JsonSecurityHandlers;
import lk.hms.common.security.PasswordChangeRequiredFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Session-cookie authentication for a same-origin SPA:
 * <ul>
 *   <li>server-side sessions (Spring Session JDBC), HttpOnly + SameSite=Strict cookie</li>
 *   <li>CSRF double-submit: XSRF-TOKEN cookie, echoed by Axios in X-XSRF-TOKEN</li>
 *   <li>every /api/v1 route requires authentication except the auth bootstrap endpoints;
 *       fine-grained permissions are enforced with @PreAuthorize on each controller method</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookiePath("/");
        repo.setCookieCustomizer(c -> c.sameSite("Strict"));
        return repo;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, JsonSecurityHandlers handlers,
                                           CsrfTokenRepository csrfTokenRepository,
                                           SecurityContextRepository securityContextRepository,
                                           HmsProperties properties) throws Exception {
        // Plain (non-XOR) handler: the SPA reads the raw cookie value and sends it back as a header.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfHandler))
                .cors(cors -> cors.configurationSource(corsSource(properties.corsOriginList())))
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(rc -> rc.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())                 // handled by AuthController (audited)
                .anonymous(a -> {})
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .headers(h -> h
                        .contentTypeOptions(c -> {})
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .cacheControl(c -> {}))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/actuator/health").permitAll()
                        .requestMatchers("/api/v1/docs/**").permitAll()   // only enabled in dev profile
                        .anyRequest().authenticated())
                .addFilterAfter(new PasswordChangeRequiredFilter(handlers), AuthorizationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsSource(List<String> origins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!origins.isEmpty()) {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(origins);
            cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cfg.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Request-Id"));
            cfg.setExposedHeaders(List.of("X-Request-Id", "Location"));
            cfg.setAllowCredentials(true);
            cfg.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", cfg);
        }
        return source;
    }
}
