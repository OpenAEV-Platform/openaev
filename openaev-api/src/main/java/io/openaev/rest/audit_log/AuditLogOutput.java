package io.openaev.rest.audit_log;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/** Output DTO for audit log entries returned by the search endpoint. */
public record AuditLogOutput(
    @JsonProperty("audit_log_id") String id,
    @JsonProperty("audit_log_event_type") String eventType,
    @JsonProperty("audit_log_event_status") String eventStatus,
    @JsonProperty("audit_log_event_access") String eventAccess,
    @JsonProperty("audit_log_event_scope") String eventScope,
    @JsonProperty("audit_log_user_id") String userId,
    @JsonProperty("audit_log_tenant_id") String tenantId,
    @JsonProperty("audit_log_user_email") String userEmail,
    @JsonProperty("audit_log_source_ip") String sourceIp,
    @JsonProperty("audit_log_user_agent") String userAgent,
    @JsonProperty("audit_log_timestamp") Instant timestamp,
    @JsonProperty("audit_log_created_at") Instant createdAt,
    @JsonProperty("audit_log_context_data") Map<String, Object> contextData) {}
