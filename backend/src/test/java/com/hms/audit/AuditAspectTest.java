package com.hms.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock private AuditService auditService;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private Audited audited;

    private ObjectMapper objectMapper;
    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditAspect = new AuditAspect(auditService, objectMapper);
    }

    record SampleResult(UUID id, String status, String note) {}

    @Test
    @DisplayName("AuditAspect redacts sensitive fields (password, phone, nic, address, token) from before and after JSON")
    void audit_RedactsSensitiveFields() throws Throwable {
        UUID entityId = UUID.randomUUID();

        Map<String, Object> inputArgs = new HashMap<>();
        inputArgs.put("username", "testuser");
        inputArgs.put("password", "SecretPassword123!");
        inputArgs.put("phone", "+94771234567");
        inputArgs.put("nicNumber", "199012345678");
        inputArgs.put("homeAddress", "No 12, Galle Road, Colombo");
        inputArgs.put("refreshToken", "jwt-refresh-sample-token");
        inputArgs.put("publicField", "unredacted");

        Object[] args = new Object[]{inputArgs};
        SampleResult sampleResult = new SampleResult(entityId, "ACTIVE", "Created successfully");

        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn(sampleResult);
        when(audited.action()).thenReturn(AuditAction.CREATE);
        when(audited.entityType()).thenReturn("User");
        when(audited.entityIdArgument()).thenReturn(-1);

        Object result = auditAspect.audit(joinPoint, audited);

        assertThat(result).isEqualTo(sampleResult);

        ArgumentCaptor<JsonNode> beforeCaptor = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<JsonNode> afterCaptor = ArgumentCaptor.forClass(JsonNode.class);

        verify(auditService).record(
                eq(AuditAction.CREATE),
                eq("User"),
                eq(entityId.toString()),
                beforeCaptor.capture(),
                afterCaptor.capture()
        );

        JsonNode beforeNode = beforeCaptor.getValue();
        JsonNode argNode = beforeNode.get(0);

        assertThat(argNode.get("password").asText()).isEqualTo("[REDACTED]");
        assertThat(argNode.get("phone").asText()).isEqualTo("[REDACTED]");
        assertThat(argNode.get("nicNumber").asText()).isEqualTo("[REDACTED]");
        assertThat(argNode.get("homeAddress").asText()).isEqualTo("[REDACTED]");
        assertThat(argNode.get("refreshToken").asText()).isEqualTo("[REDACTED]");
        assertThat(argNode.get("publicField").asText()).isEqualTo("unredacted");
        assertThat(argNode.get("username").asText()).isEqualTo("testuser");
    }
}

