package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "connector_instances")
@EntityListeners(ModelBaseListener.class)
public class ConnectorInstance implements Base {

  @Id
  @Column(name = "connector_instance_id")
  @JsonProperty("connector_instance_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "connector_instance_catalog", nullable = false)
  @JsonIgnore
  private CatalogConnector catalogConnector;

  @Column(name = "connector_instance_hash")
  @JsonProperty("connector_instance_hash")
  @NotBlank
  private String hash;

  @Column(name = "connector_instance_current_status")
  @JsonProperty("connector_instance_current_status")
  @NotBlank
  private String currentStatus;

  @Column(name = "connector_instance_requested_status")
  @JsonProperty("connector_instance_requested_status")
  private String requestedStatus;

  @Column(name = "connector_instance_restart_count")
  @JsonProperty("connector_instance_restart_count")
  private Integer restartCount;

  @Column(name = "connector_instance_started_at")
  @JsonProperty("connector_instance_started_at")
  private Instant startedAt;

  @Column(name = "connector_instance_is_in_reboot_loop")
  @JsonProperty("connector_instance_is_in_reboot_loop")
  private Boolean isInRebootLoop;

  @OneToMany(
      mappedBy = "connectorInstance",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("connector_instance_configurations")
  @NotNull
  private Set<ConnectorInstanceConfiguration> configurations = new HashSet<>();
}
