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
@Table(name = "content_changes")
public class ContentChange implements TenantBase {

  public enum Action {
    INSTALL(false),
    UNINSTALL(false),
    REVOKE(false),
    LIFT_REVOCATION(false),
    UPDATE(true),
    ROLLBACK(false),
    SAVE(true);

    private final boolean newVersion;

    Action(boolean newVersion) {
      this.newVersion = newVersion;
    }

    /** Made its version from the one just below it. */
    public boolean madeVersion() {
      return newVersion;
    }
  }

  public enum Mode {
    MANUAL,
    AUTO
  }

  @Id
  @Column(name = "content_change_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- CONTENT --

  @Column(name = "content_change_type", updatable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private ContentType type;

  // Null for local content, which is found by its content id.
  @Column(name = "content_change_repository", updatable = false)
  private String repository;

  @Column(name = "content_change_external_id", updatable = false)
  private String externalId;

  @Column(name = "content_change_content_id", updatable = false)
  private String contentId;

  @Column(name = "content_change_version", updatable = false)
  @NotNull
  private String version;

  // -- CHANGE --

  @Column(name = "content_change_action", updatable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private Action action;

  @Column(name = "content_change_mode", updatable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private Mode mode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_change_user", updatable = false)
  private User user;

  @Column(name = "content_change_created_at", updatable = false)
  @NotNull
  private Instant createdAt = now();
}
