package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.AuditStateCapturable;
import io.openaev.database.audit.AuditStateIgnore;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
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

/**
 * Fully on tenant isolation v2: reads are scoped by {@code TenantStatementInspector} through the
 * request's {@code TxCtx} (or, for platform-level XTM Composer callbacks, an explicit {@code
 * TxCtx.allTenants()} set on the joined transaction); writes are explicitly attributed via {@code
 * ConfigurationMigration#migrate}'s {@code setTenant(...)} or the create-endpoint's write-scope
 * resolver. The v1 {@code @Filter} must NOT come back: it would AND its own thread-local predicate
 * with v2's scope and silently return nothing. {@link TenantBaseListener} stays as an inert
 * fallback (it only sets {@code tenant} when still {@code null}); every write path here sets it
 * explicitly first, so the listener never fires in practice.
 */
@Getter
@Setter
@Entity(name = "ConnectorInstance")
@Table(name = "connector_instances")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
public class ConnectorInstancePersisted extends ConnectorInstance
    implements TenantBase, AuditStateCapturable {
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
  @AuditStateIgnore
  private Instant startedAt;

  // Fixes a bug due to a new version of jackson and lombok
  // cf: https://github.com/projectlombok/lombok/issues/3978
  @Getter(onMethod_ = @JsonProperty("connector_instance_is_in_reboot_loop"))
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
  @AuditStateIgnore
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

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

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
