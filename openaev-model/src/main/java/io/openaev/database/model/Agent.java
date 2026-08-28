package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.AuditStateCapturable;
import io.openaev.database.audit.AuditStateIgnore;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JoinColumnOrFormula;
import org.hibernate.annotations.JoinColumnsOrFormulas;
import org.hibernate.annotations.JoinFormula;

@Getter
@Setter
@Entity
@Table(name = "agents")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Agent implements TenantBase, AuditStateCapturable {

  public static final long ACTIVE_THRESHOLD_MILLIS = 3_600_000L;
  public static final String ADMIN_SYSTEM_WINDOWS = "nt authority\\system";
  public static final String ADMIN_SYSTEM_UNIX = "root";

  public enum PRIVILEGE {
    @JsonProperty("admin")
    admin,
    @JsonProperty("standard")
    standard,
  }

  public enum DEPLOYMENT_MODE {
    @JsonProperty("service")
    service,
    @JsonProperty("session")
    session,
  }

  @Id
  @Column(name = "agent_id")
  @JsonProperty("agent_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(sortable = true, filterable = true, path = "asset.id")
  @ManyToOne
  @JoinColumn(name = "agent_asset")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("agent_asset")
  @Schema(implementation = String.class)
  @NotNull
  @AuditStateIgnore
  private Asset asset;

  @Queryable(sortable = true)
  @Column(name = "agent_privilege")
  @JsonProperty("agent_privilege")
  @Enumerated(EnumType.STRING)
  @NotNull
  private PRIVILEGE privilege;

  @Queryable(sortable = true)
  @Column(name = "agent_deployment_mode")
  @JsonProperty("agent_deployment_mode")
  @Enumerated(EnumType.STRING)
  @NotNull
  private DEPLOYMENT_MODE deploymentMode;

  @Queryable(sortable = true, filterable = true, searchable = true)
  @Column(name = "agent_executed_by_user")
  @JsonProperty("agent_executed_by_user")
  @NotBlank
  private String executedByUser;

  @Queryable(sortable = true)
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumnsOrFormulas({
    @JoinColumnOrFormula(
        column = @JoinColumn(name = "agent_executor", referencedColumnName = "executor_id")),
    @JoinColumnOrFormula(
        formula = @JoinFormula(value = "tenant_id", referencedColumnName = "tenant_id"))
  })
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("agent_executor")
  @Schema(implementation = String.class)
  private Executor executor;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "agent_version")
  @JsonProperty("agent_version")
  private String version;

  /** Used for Caldera only */
  @ManyToOne(fetch = FetchType.EAGER)
  @JsonSerialize(using = MonoIdSerializer.class)
  @JoinColumn(name = "agent_parent")
  @JsonProperty("agent_parent")
  @Schema(implementation = String.class)
  @AuditStateIgnore
  private Agent parent;

  /** Used for Caldera only */
  @OneToOne(fetch = FetchType.EAGER)
  @JsonSerialize(using = MonoIdSerializer.class)
  @JoinColumn(name = "agent_inject")
  @JsonProperty("agent_inject")
  @Schema(implementation = String.class)
  @AuditStateIgnore
  private Inject inject;

  @JsonProperty("agent_active")
  public boolean isActive() {
    return this.status == AgentStatus.ACTIVE;
  }

  @AuditStateIgnore
  @Column(name = "agent_status", nullable = false)
  @Enumerated(EnumType.STRING)
  private AgentStatus status = AgentStatus.ACTIVE;

  /** Used for Caldera only */
  @Column(name = "agent_process_name")
  @JsonProperty("agent_process_name")
  private String processName;

  @Column(name = "agent_external_reference")
  @JsonProperty("agent_external_reference")
  private String externalReference;

  @AuditStateIgnore
  @Column(name = "agent_last_seen")
  @JsonProperty("agent_last_seen")
  private Instant lastSeen;

  @Column(name = "agent_created_at")
  @JsonProperty("agent_created_at")
  @NotNull
  @AuditStateIgnore
  private Instant createdAt = now();

  @AuditStateIgnore
  @Column(name = "agent_updated_at")
  @JsonProperty("agent_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Column(name = "agent_cleared_at")
  @JsonProperty("agent_cleared_at")
  @AuditStateIgnore
  private Instant clearedAt = now();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.AGENT;

  // Ignore json and not null
  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public Agent() {}
}
