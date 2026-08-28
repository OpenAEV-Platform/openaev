package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Outbox row produced by the live notification engine for every (trigger, user) match.
 *
 * <p>This is the OpenAEV equivalent of OpenCTI's {@code stream.notification} Redis stream: live
 * matches are recorded here so digest triggers can replay them over a time window. Rows are purged
 * by the notification event retention job.
 *
 * <p>Deliberately not listened ({@link #isListened()}): these rows are engine internals and must
 * not be broadcast over SSE.
 */
@Entity
@Getter
@Setter
@Table(name = "notification_events")
@EntityListeners(TenantBaseListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class NotificationEventRecord implements TenantBase {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "notification_event_id")
  @JsonProperty("notification_event_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "notification_trigger_id", nullable = false)
  @JsonProperty("notification_event_trigger")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  @NotNull
  private NotificationTrigger trigger;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  @JsonProperty("notification_event_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  @NotNull
  private User user;

  @Column(name = "notification_event_type")
  @JsonProperty("notification_event_type")
  @NotNull
  @Enumerated(EnumType.STRING)
  private NotificationTriggerEventType eventType;

  @Column(name = "notification_event_message")
  @JsonProperty("notification_event_message")
  private String message;

  @Column(name = "notification_event_resource_type")
  @JsonProperty("notification_event_resource_type")
  @Enumerated(EnumType.STRING)
  private ResourceType resourceTypeValue;

  @Column(name = "notification_event_resource_id")
  @JsonProperty("notification_event_resource_id")
  private String resourceId;

  @CreationTimestamp
  @Column(name = "notification_event_created_at", updatable = false)
  @JsonProperty("notification_event_created_at")
  private Instant createdAt;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Override
  @JsonIgnore
  public boolean isListened() {
    return false;
  }

  @Override
  @JsonIgnore
  public ResourceType getResourceType() {
    return ResourceType.UNKNOWN;
  }
}
