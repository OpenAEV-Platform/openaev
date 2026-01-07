package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
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
public class ConnectorInstancePersisted extends ConnectorInstance implements Base {
  @Id
  @Column(name = "connector_instance_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("connector_instance_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connector_instance_catalog_id", nullable = false)
  @JsonProperty("connector_instance_catalog")
  @NotNull
  @JsonBackReference
  private CatalogConnector catalogConnector;

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
  @JsonProperty("connector_instance_logs")
  @NotNull
  private Set<ConnectorInstanceLog> logs = new HashSet<>();

  /** Shadow base class members */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "connector_instance_current_status")
  @JsonProperty("connector_instance_current_status")
  @NotNull
  private CURRENT_STATUS_TYPE currentStatus;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "connector_instance_source")
  @JsonProperty("connector_instance_source")
  @NotNull
  private SOURCE source = SOURCE.OTHER;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "connector_instance_requested_status")
  @JsonProperty("connector_instance_requested_status")
  private REQUESTED_STATUS_TYPE requestedStatus;

  @OneToMany(
      mappedBy = "connectorInstance",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("connector_instance_configurations")
  @NotNull
  private Set<ConnectorInstanceConfiguration> configurations = new HashSet<>();

  @Override
  public String getClassName() {
    if (this.getCatalogConnector() != null) {
      return this.getCatalogConnector().getClassName();
    }
    return "";
  }

  @Override
  public String getHashIdentity() {
    CatalogConnector cc = this.getCatalogConnector();
    if (cc == null) {
      return "UNKNOWN";
    }
    if (cc.getContainerImage() != null && !cc.getContainerImage().isBlank()) {
      return String.format("IMAGE[%s:%s]", cc.getContainerImage(), cc.getContainerVersion());
    }

    return String.format("BUILTIN[%s]", this.getClassName());
  }
}
