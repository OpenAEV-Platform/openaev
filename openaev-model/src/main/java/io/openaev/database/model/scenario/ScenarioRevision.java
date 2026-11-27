package io.openaev.database.model.scenario;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.openaev.database.model.User;
import io.openaev.database.model.catalog.ContentChange;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/** The content of a catalogue scenario, kept at install and before an update or a rollback. */
@Getter
@Setter
@Entity
@Table(name = "scenario_revisions")
public class ScenarioRevision implements TenantBase {

  public enum Reason {
    INSTALL(ContentChange.Action.INSTALL),
    UPDATE(ContentChange.Action.UPDATE),
    ROLLBACK(ContentChange.Action.ROLLBACK);

    private final ContentChange.Action action;

    Reason(ContentChange.Action action) {
      this.action = action;
    }

    public ContentChange.Action action() {
      return action;
    }
  }

  // -- ID --

  @Id
  @Column(name = "scenario_revision_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  // -- TENANT --

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- REVISION --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "scenario_id", updatable = false, nullable = false)
  @NotNull
  private Scenario scenario;

  @Column(name = "scenario_revision_reason", updatable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private Reason reason;

  @Column(name = "scenario_revision_catalog_version", updatable = false)
  @NotNull
  private String catalogVersion;

  @Type(JsonType.class)
  @Column(name = "scenario_revision_content", columnDefinition = "jsonb", updatable = false)
  @NotNull
  private ObjectNode content;

  // -- AUDIT --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "scenario_revision_user", updatable = false)
  private User user;

  @Column(name = "scenario_revision_created_at", updatable = false)
  @NotNull
  private Instant createdAt = now();
}
