package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.AuditStateCapturable;
import io.openaev.database.audit.AuditStateIgnore;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.jsonapi.BusinessId;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

/**
 * Fully on v2 tenant isolation (TenantStatementInspector + can_access_tenant). The v1
 * {@code @Filter("tenantFilter")} and {@code TenantIdBaseListener} were removed as part of the
 * activation; they must NOT come back — the filter would AND two predicates and return zero rows,
 * and the listener is a v1 pattern that reads from TenantContext (no longer the source of truth for
 * activated tables). Write attribution is now explicit via {@code TenantWriteScopeResolver}.
 */
@Getter
@Setter
@Entity
@Table(name = "collectors")
@EntityListeners({ModelBaseListener.class})
@IdClass(ConnectorCompositeId.class)
public class Collector extends BaseConnectorEntity implements TenantIdBase, AuditStateCapturable {

  /** Expectation types answered by a collector that declares none (collectors released before). */
  public static final List<EXPECTATION_TYPE> LEGACY_SUPPORTED_EXPECTATION_TYPES =
      List.of(EXPECTATION_TYPE.DETECTION, EXPECTATION_TYPE.PREVENTION);

  @Id
  @Column(name = "collector_id")
  @JsonProperty("collector_id")
  @NotBlank
  private String id;

  @Id
  @Column(name = "tenant_id")
  @JsonIgnore
  private String tenantId;

  @Column(name = "collector_name")
  @JsonProperty("collector_name")
  @NotBlank
  private String name;

  @BusinessId
  @Column(name = "collector_type")
  @JsonProperty("collector_type")
  @NotBlank
  private String type;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "collector_type_id")
  @JsonIgnore
  private CollectorType collectorType;

  @Column(name = "collector_period")
  @JsonProperty("collector_period")
  private int period;

  /**
   * Optional source-declared author override. When set, the collector's payloads (and their arsenal
   * contracts) are attributed to this author organization instead of the collector's display name.
   */
  @Column(name = "collector_author")
  @JsonProperty("collector_author")
  private String author;

  @Column(name = "collector_external")
  @JsonProperty("collector_external")
  private boolean external = false;

  @Column(name = "collector_created_at")
  @JsonProperty("collector_created_at")
  @NotNull
  @AuditStateIgnore
  private Instant createdAt = now();

  @Column(name = "collector_updated_at")
  @JsonProperty("collector_updated_at")
  @NotNull
  @AuditStateIgnore
  private Instant updatedAt = now();

  @Column(name = "collector_last_execution")
  @JsonProperty("collector_last_execution")
  private Instant lastExecution;

  // ManyToOne (not OneToOne): the collector_security_platform column carries no unique
  // constraint, several collectors may legitimately point to the same security platform.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "collector_security_platform")
  @JsonProperty("collector_security_platform")
  @AuditStateIgnore
  private SecurityPlatform securityPlatform;

  /**
   * Expectation types the collector declared it answers, replaced at every registration. Null when
   * it declared none: it then answers {@link #LEGACY_SUPPORTED_EXPECTATION_TYPES}.
   */
  @Type(StringArrayType.class)
  @Column(name = "collector_supported_expectation_types", columnDefinition = "text[]")
  @JsonProperty("collector_supported_expectation_types")
  private EXPECTATION_TYPE[] supportedExpectationTypes;

  @JsonProperty("collector_state")
  @Column(name = "collector_state")
  @Type(JsonType.class)
  private ObjectNode state;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.COLLECTOR;

  /**
   * Whether this collector is expected to answer expectations of the given type, so that a pending
   * result is only seeded for collectors able to fill it.
   */
  public boolean supportsExpectationType(EXPECTATION_TYPE expectationType) {
    if (supportedExpectationTypes == null || supportedExpectationTypes.length == 0) {
      return LEGACY_SUPPORTED_EXPECTATION_TYPES.contains(expectationType);
    }
    return Arrays.asList(supportedExpectationTypes).contains(expectationType);
  }

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
