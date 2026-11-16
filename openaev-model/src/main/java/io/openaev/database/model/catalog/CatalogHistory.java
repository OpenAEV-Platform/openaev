package io.openaev.database.model.catalog;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.openaev.database.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** One change a tenant made to its catalogue content: what, which version, and by whom. */
@Getter
@Setter
@Entity
@Table(name = "catalog_history")
public class CatalogHistory implements TenantBase {

  public enum Action {
    INSTALL,
    UNINSTALL
  }

  public enum Mode {
    MANUAL,
    AUTO
  }

  @Id
  @Column(name = "catalog_history_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- CONTENT --

  @Column(name = "catalog_history_type", updatable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private ContentType type;

  @Column(name = "catalog_history_repository", updatable = false)
  @NotNull
  private String repository;

  @Column(name = "catalog_history_external_id", updatable = false)
  @NotNull
  private String externalId;

  @Column(name = "catalog_history_version", updatable = false)
  @NotNull
  private String version;

  // -- CHANGE --

  @Column(name = "catalog_history_action", updatable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private Action action;

  @Column(name = "catalog_history_mode", updatable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private Mode mode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "catalog_history_user", updatable = false)
  private User user;

  @Column(name = "catalog_history_created_at", updatable = false)
  @NotNull
  private Instant createdAt = now();
}
