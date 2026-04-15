package io.openaev.engine.model.auditlog;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Indexable;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Elasticsearch / OpenSearch document for audit log entries.
 *
 * <p>This model does <b>not</b> extend {@code EsBase} because audit-log documents have their own
 * dedicated mapping with nested objects ({@code user_metadata}, {@code context_data}) and different
 * field naming conventions.
 *
 * <p>The index is created with a custom mapping during engine initialization (not via the generic
 * reflection-based mapper used for other indices).
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.ALWAYS)
@Indexable(index = "audit-log", label = "Audit Log")
public class LogEvent {

  // -- Document identity --

  /** Unique document identifier (UUID). */
  private String id;

  /** Fixed discriminator value: always {@code "Activity"}. */
  @JsonProperty("entity_type")
  private String entityType = "Activity";

  /** Document creation timestamp (immutable). */
  @JsonProperty("created_at")
  private Instant createdAt;

  // -- Event envelope --

  /** High-level event category: {@code "mutation"} or {@code "authentication"}. */
  @JsonProperty("event_type")
  private String eventType;

  /** Outcome: {@code "success"} or {@code "error"}. */
  @JsonProperty("event_status")
  private String eventStatus;

  /** Access classification: {@code "administration"} or {@code "extended"}. */
  @JsonProperty("event_access")
  private String eventAccess;

  /**
   * Specific action: {@code "create"}, {@code "update"}, {@code "delete"}, {@code "duplicate"},
   * {@code "status_change"}, {@code "login"}, {@code "logout"}, {@code "unauthorized"}.
   */
  @JsonProperty("event_scope")
  private String eventScope;

  // -- Actor --

  /** ID of the user who performed the action (nullable for anonymous). */
  @JsonProperty("user_id")
  private String userId;

  /** Tenant context (nullable until multi-tenancy is fully rolled out). */
  @JsonProperty("tenant_id")
  private String tenantId;

  /** Nested user metadata (email, IP, user-agent). */
  @JsonProperty("user_metadata")
  private UserMetadata userMetadata;

  // -- Timing --

  /** Event timestamp (when the action occurred). */
  private Instant timestamp;

  // -- Payload --

  /**
   * Free-form contextual data. Mapped as {@code dynamic: true} in ES so arbitrary keys are
   * accepted. Typical keys: {@code resource_type}, {@code resource_id}, {@code resource_name},
   * {@code message}, {@code input}, {@code old_value}, {@code parent_id}, {@code source_entity_id},
   * {@code provider}, {@code reason}.
   */
  @JsonProperty("context_data")
  private Map<String, Object> contextData;

  // -- Nested objects --

  /** User metadata: request-level information about the actor. */
  @Getter
  @Setter
  @JsonAutoDetect(
      getterVisibility = JsonAutoDetect.Visibility.NONE,
      fieldVisibility = JsonAutoDetect.Visibility.ANY)
  public static class UserMetadata {

    /** Denormalized email address of the actor. */
    @JsonProperty("user_email")
    private String userEmail;

    /** Raw User-Agent header from the HTTP request. */
    @JsonProperty("user_agent")
    private String userAgent;

    /** Value of the X-Forwarded-For header (first entry = original client). */
    @JsonProperty("x_forwarded_for")
    private String xForwardedFor;

    /** Resolved remote IP address (remoteAddr or X-Real-IP). */
    private String ip;
  }
}
