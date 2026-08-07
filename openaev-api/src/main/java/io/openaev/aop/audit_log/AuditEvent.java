package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.model.ResourceType;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Immutable descriptor for a generic audit event. Built via Lombok {@code @Builder} and passed to
 * {@code AuditLogger.logEvent(AuditEvent)}.
 *
 * <p>For mutation events, {@code entityDiffs} carries field-level before/after diffs. For execution
 * and system events, {@code contextData} carries arbitrary structured metadata.
 */
@Getter
@Builder
public class AuditEvent {

  @NonNull private final EventType eventType;
  @NonNull private final AuditEventScope eventScope;
  @NonNull private final EventStatus eventStatus;
  private final ResourceType resourceType;
  private final String resourceId;
  private final String message;
  private final Map<String, Object> contextData;
  private final JsonNode entityDiffs;
  @NonNull private final AuditEventOrigin origin;
}
