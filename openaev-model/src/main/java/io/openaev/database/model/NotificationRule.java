package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Getter
@Setter
@Table(name = "notification_rules")
@EntityListeners(ModelBaseListener.class)
public class NotificationRule implements Base {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "notification_rule_id")
  @JsonProperty("notification_rule_id")
  @NotBlank
  private String id;

  @Column(name = "notification_resource_type")
  @JsonProperty("notification_resource_type")
  @NotNull
  @Enumerated(EnumType.STRING)
  private NotificationRuleResourceType notificationResourceType;

  @Column(name = "notification_resource_id")
  @JsonProperty("notification_resource_id")
  @NotBlank
  @Queryable(searchable = true, filterable = true)
  private String resourceId;

  @Column(name = "notification_rule_trigger")
  @JsonProperty("notification_rule_trigger")
  @NotNull
  @Enumerated(EnumType.STRING)
  private NotificationRuleTrigger trigger;

  @Column(name = "notification_rule_type")
  @JsonProperty("notification_rule_type")
  @NotNull
  @Enumerated(EnumType.STRING)
  private NotificationRuleType type;

  @Column(name = "notification_rule_subject")
  @JsonProperty("notification_rule_subject")
  @NotBlank
  private String subject;

  @JoinColumn(name = "user_id")
  @JsonProperty("notification_rule_owner")
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @Queryable(searchable = true, filterable = true, path = "owner.id")
  private User owner;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.NOTIFICATION_RULE;
}
