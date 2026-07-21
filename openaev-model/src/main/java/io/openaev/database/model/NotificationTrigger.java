package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A subscription to platform events, modeled on OpenCTI triggers.
 *
 * <p>Two flavours:
 *
 * <ul>
 *   <li><b>LIVE</b>: fires when an entity of {@code watchedResourceType} matching {@code filters}
 *       (or {@code instanceId} for instance triggers) undergoes one of the subscribed {@code
 *       eventTypes} (create/update/delete). Matches are dispatched immediately to the trigger's
 *       {@link Notifier}s and recorded as {@link NotificationEventRecord}s.
 *   <li><b>DIGEST</b>: fires periodically ({@code period} + {@code triggerTime}) and aggregates the
 *       {@link NotificationEventRecord}s produced by its composed live triggers ({@code
 *       childTriggers}) over the elapsed period.
 * </ul>
 *
 * <p>Recipients default to the owner; additional users or groups can be targeted by administrators.
 * Group recipients fan out to their users at match time.
 */
@Entity
@Getter
@Setter
@Table(name = "notification_triggers")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class NotificationTrigger implements TenantBase {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "notification_trigger_id")
  @JsonProperty("notification_trigger_id")
  @NotBlank
  private String id;

  @Column(name = "notification_trigger_name")
  @JsonProperty("notification_trigger_name")
  @NotBlank
  @Queryable(searchable = true, filterable = true, sortable = true)
  private String name;

  @Column(name = "notification_trigger_type")
  @JsonProperty("notification_trigger_type")
  @NotNull
  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  private NotificationTriggerType type;

  @Column(name = "notification_trigger_enabled")
  @JsonProperty("notification_trigger_enabled")
  private boolean enabled = true;

  // -- LIVE --

  /** Resource type watched by a live trigger (one of the notification catalog types). */
  @Column(name = "notification_trigger_resource_type")
  @JsonProperty("notification_trigger_resource_type")
  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  private ResourceType watchedResourceType;

  @Type(JsonType.class)
  @Column(name = "notification_trigger_event_types")
  @JsonProperty("notification_trigger_event_types")
  private List<NotificationTriggerEventType> eventTypes = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "notification_trigger_filters")
  @JsonProperty("notification_trigger_filters")
  private Filters.FilterGroup filters;

  /** When set, the live trigger only matches events on this specific entity (instance trigger). */
  @Column(name = "notification_trigger_instance_id")
  @JsonProperty("notification_trigger_instance_id")
  @Queryable(filterable = true)
  private String instanceId;

  // -- DIGEST --

  @Column(name = "notification_trigger_period")
  @JsonProperty("notification_trigger_period")
  @Enumerated(EnumType.STRING)
  private NotificationTriggerPeriod period;

  /**
   * UTC firing time of a digest. Formats: DAY = {@code "HH:mm"}, WEEK = {@code "<1-7>-HH:mm"} (ISO
   * day of week), MONTH = {@code "<1-31>-HH:mm"}. HOUR digests fire on the hour and ignore it.
   */
  @Column(name = "notification_trigger_time")
  @JsonProperty("notification_trigger_time")
  private String triggerTime;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "notification_triggers_children",
      joinColumns = @JoinColumn(name = "notification_trigger_id"),
      inverseJoinColumns = @JoinColumn(name = "child_notification_trigger_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("notification_trigger_children")
  @Schema(implementation = String[].class)
  private List<NotificationTrigger> childTriggers = new ArrayList<>();

  // -- DELIVERY --

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "notification_triggers_notifiers",
      joinColumns = @JoinColumn(name = "notification_trigger_id"),
      inverseJoinColumns = @JoinColumn(name = "notifier_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("notification_trigger_notifiers")
  @Schema(implementation = String[].class)
  private List<Notifier> notifiers = new ArrayList<>();

  // -- RECIPIENTS --

  @JoinColumn(name = "user_id")
  @JsonProperty("notification_trigger_owner")
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  @Queryable(filterable = true, path = "owner.id")
  private User owner;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "notification_triggers_users",
      joinColumns = @JoinColumn(name = "notification_trigger_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("notification_trigger_recipient_users")
  @Schema(implementation = String[].class)
  private List<User> recipientUsers = new ArrayList<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "notification_triggers_groups",
      joinColumns = @JoinColumn(name = "notification_trigger_id"),
      inverseJoinColumns = @JoinColumn(name = "group_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("notification_trigger_recipient_groups")
  @Schema(implementation = String[].class)
  private List<Group> recipientGroups = new ArrayList<>();

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "notification_trigger_created_at", updatable = false)
  @JsonProperty("notification_trigger_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "notification_trigger_updated_at")
  @JsonProperty("notification_trigger_updated_at")
  @Queryable(sortable = true)
  private Instant updatedAt;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Override
  @JsonIgnore
  public ResourceType getResourceType() {
    return ResourceType.NOTIFICATION_TRIGGER;
  }
}
