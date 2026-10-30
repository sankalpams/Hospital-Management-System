package com.hms.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import java.lang.reflect.RecordComponent;

@Aspect @Component @RequiredArgsConstructor
public class AuditAspect {
    private final AuditService auditService;
    private final ObjectMapper mapper;
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        JsonNode before = safe(mapper.valueToTree(joinPoint.getArgs()));
        Object result = joinPoint.proceed();
        String id = entityId(joinPoint.getArgs(), result, audited.entityIdArgument());
        auditService.record(audited.action(), audited.entityType(), id, before, safe(mapper.valueToTree(result)));
        return result;
    }
    private String entityId(Object[] args, Object result, int index) {
        if (index >= 0 && index < args.length && args[index] != null) return String.valueOf(args[index]);
        if (result != null && result.getClass().isRecord()) for (RecordComponent c : result.getClass().getRecordComponents()) {
            if ("id".equals(c.getName())) try { return String.valueOf(c.getAccessor().invoke(result)); } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }
    private JsonNode safe(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.fieldNames().forEachRemaining(name -> {
                String key = name.toLowerCase();
                if (key.contains("password") || key.contains("phone") || key.contains("nic") || key.contains("address") || key.contains("token")) object.put(name, "[REDACTED]");
                else safe(object.get(name));
            });
        } else if (node.isArray()) node.forEach(this::safe);
        return node;
    }
}
