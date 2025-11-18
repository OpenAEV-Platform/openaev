package io.openaev.database.model;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "connector_instances")
@EntityListeners(ModelBaseListener.class)
public class ConnectorInstance implements Base {

  public enum CURRENT_STATUS_TYPE {
    started,
    stopped
  }

  public enum REQUESTED_STATUS_TYPE {
    starting,
    stopping
  }

  public enum SOURCE {
    PROPERTIES_MIGRATION,
    CATALOG_DEPLOYMENT,
    OTHER
  }

  @Id
  @Column(name = "connector_instance_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("connector_instance_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "connector_instance_catalog_id", nullable = false)
  @JsonProperty("connector_instance_catalog")
  @NotNull
  private CatalogConnector catalogConnector;

  @Enumerated(EnumType.STRING)
  @Column(name = "connector_instance_current_status")
  @JsonProperty("connector_instance_current_status")
  @NotBlank
  private CURRENT_STATUS_TYPE currentStatus;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "connector_instance_source")
  @JsonProperty("connector_instance_source")
  @NotBlank
  private SOURCE source = SOURCE.OTHER;

  @Enumerated(EnumType.STRING)
  @Column(name = "connector_instance_requested_status")
  @JsonProperty("connector_instance_requested_status")
  private REQUESTED_STATUS_TYPE requestedStatus;

  @Column(name = "connector_instance_restart_count")
  @JsonProperty("connector_instance_restart_count")
  private Integer restartCount;

  @Column(name = "connector_instance_started_at")
  @JsonProperty("connector_instance_started_at")
  private Instant startedAt;

  @Column(name = "connector_instance_is_in_reboot_loop")
  @JsonProperty("connector_instance_is_in_reboot_loop")
  private boolean isInRebootLoop;

  @OneToMany(
      mappedBy = "connectorInstance",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("connector_instance_configurations")
  @NotNull
  private Set<ConnectorInstanceConfiguration> configurations = new HashSet<>();

  @OneToMany(
      mappedBy = "connectorInstance",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("connector_instance_logs")
  @NotNull
  private Set<ConnectorInstanceLog> logs = new HashSet<>();
}
