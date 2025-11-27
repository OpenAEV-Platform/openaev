package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.AgentHelper;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agents")
@EntityListeners(ModelBaseListener.class)
public class Agent implements Base {

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
  // ID is UUID by default and external reference for CrowdStrike agent
  private String id = UUID.randomUUID().toString();

  @Queryable(sortable = true, filterable = true, path = "asset.id")
  @ManyToOne
  @JoinColumn(name = "agent_asset")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("agent_asset")
  @Schema(type = "string")
  @NotNull
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
  @JoinColumn(name = "agent_executor")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("agent_executor")
  @Schema(type = "string")
  private Executor executor;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "agent_version")
  @JsonProperty("agent_version")
  private String version;

  /** Used for Caldera only */
  @ManyToOne(fetch = FetchType.EAGER)
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JoinColumn(name = "agent_parent")
  @JsonProperty("agent_parent")
  @Schema(type = "string")
  private Agent parent;

  /** Used for Caldera only */
  @OneToOne(fetch = FetchType.EAGER)
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JoinColumn(name = "agent_inject")
  @JsonProperty("agent_inject")
  @Schema(type = "string")
  private Inject inject;

  @JsonProperty("agent_active")
  public boolean isActive() {
    return new AgentHelper().isAgentActiveFromLastSeen(this.getLastSeen());
  }

  /** Used for Caldera only */
  @Column(name = "agent_process_name")
  @JsonProperty("agent_process_name")
  private String processName;

  @Column(name = "agent_external_reference")
  @JsonProperty("agent_external_reference")
  private String externalReference;

  @Column(name = "agent_last_seen")
  @JsonProperty("agent_last_seen")
  private Instant lastSeen;

  @Column(name = "agent_created_at")
  @JsonProperty("agent_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "agent_updated_at")
  @JsonProperty("agent_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Column(name = "agent_cleared_at")
  @JsonProperty("agent_cleared_at")
  private Instant clearedAt = now();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.AGENT;

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public Agent() {}
}
