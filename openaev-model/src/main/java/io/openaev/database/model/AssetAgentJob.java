package io.openaev.database.model;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "asset_agent_jobs")
@EntityListeners(ModelBaseListener.class)
public class AssetAgentJob implements Base {

  @Id
  @Column(name = "asset_agent_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("asset_agent_id")
  @NotBlank
  private String id;

  @Getter
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "asset_agent_inject")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("asset_agent_inject")
  @Schema(implementation = String.class)
  private Inject inject;

  @Getter
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "asset_agent_agent")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("asset_agent_agent")
  @Schema(implementation = String.class)
  private Agent agent;

  @Getter
  @Column(name = "asset_agent_command")
  @JsonProperty("asset_agent_command")
  @NotBlank
  private String command;

  @ManyToOne
  @JoinColumn(name = "tenant_id")
  @JsonIgnore
  @NotNull
  private Tenant tenant = new Tenant(DEFAULT_TENANT_UUID);

  @Override
  public String toString() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String getId() {
    return id;
  }

  @Deprecated(since = "1.11.0")
  @JsonProperty("asset_agent_asset")
  public String getAgentId() {
    return agent.getId();
  }
}
