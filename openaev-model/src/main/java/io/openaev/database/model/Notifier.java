package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A notification delivery channel: user interface (in-app), email, or webhook.
 *
 * <p>Notifiers are referenced by {@link NotificationTrigger}s. The {@code configuration} JSON holds
 * the type-specific settings (email subject/body templates, webhook URL/verb/headers/body
 * template). Built-in notifiers ("User interface", "Default mailer") are seeded per tenant and are
 * read-only.
 */
@Entity
@Getter
@Setter
@Table(name = "notifiers")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Notifier implements TenantBase {

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "notifier_id")
  @JsonProperty("notifier_id")
  @NotBlank
  private String id;

  @Column(name = "notifier_name")
  @JsonProperty("notifier_name")
  @NotBlank
  @Queryable(searchable = true, filterable = true, sortable = true)
  private String name;

  @Column(name = "notifier_description")
  @JsonProperty("notifier_description")
  private String description;

  @Column(name = "notifier_type")
  @JsonProperty("notifier_type")
  @NotNull
  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  private NotifierType type;

  @Type(JsonType.class)
  @Column(name = "notifier_configuration")
  @JsonProperty("notifier_configuration")
  private Map<String, Object> configuration = new HashMap<>();

  @Column(name = "notifier_built_in")
  @JsonProperty("notifier_built_in")
  private boolean builtIn = false;

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "notifier_created_at", updatable = false)
  @JsonProperty("notifier_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "notifier_updated_at")
  @JsonProperty("notifier_updated_at")
  @Queryable(sortable = true)
  private Instant updatedAt;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.NOTIFIER;
}
