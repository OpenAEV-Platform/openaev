package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/**
 * An in-app notification delivered to a single user by the UI notifier.
 *
 * <p>The {@code content} JSON is a list of {@code {title, events:[{message, operation,
 * resource_type, resource_id}]}} groups, mirroring OpenCTI's notification content shape. Live
 * notifications carry one group with one event; digests carry one group per composed trigger.
 *
 * <p>Implements {@link UserScoped} so SSE delivery is restricted to the owning user.
 */
@Entity
@Getter
@Setter
@Table(name = "notifications")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Notification implements TenantBase, UserScoped {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "notification_id")
  @JsonProperty("notification_id")
  @NotBlank
  private String id;

  /** Name of the trigger that produced this notification. */
  @Column(name = "notification_name")
  @JsonProperty("notification_name")
  @NotBlank
  @Queryable(searchable = true, filterable = true, sortable = true)
  private String name;

  @Column(name = "notification_type")
  @JsonProperty("notification_type")
  @NotNull
  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  private NotificationTriggerType type;

  @Type(JsonType.class)
  @Column(name = "notification_content")
  @JsonProperty("notification_content")
  private List<Map<String, Object>> content = new ArrayList<>();

  @Column(name = "notification_is_read")
  @JsonProperty("notification_is_read")
  @Queryable(filterable = true, sortable = true)
  private boolean read = false;

  // EAGER: the owner id must stay readable after the session closes (SSE user scoping)
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", nullable = false)
  @JsonProperty("notification_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  @NotNull
  @Queryable(filterable = true, path = "user.id")
  private User user;

  @CreationTimestamp
  @Column(name = "notification_created_at", updatable = false)
  @JsonProperty("notification_created_at")
  @Queryable(sortable = true)
  private Instant createdAt;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Override
  @JsonIgnore
  public String getOwnerUserId() {
    return this.user != null ? this.user.getId() : null;
  }

  @Override
  @JsonIgnore
  public ResourceType getResourceType() {
    return ResourceType.NOTIFICATION;
  }
}
