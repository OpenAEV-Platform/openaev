package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

/**
 * A version of the executable content of a payload. The payload row always holds its active,
 * approved content; an edit that needs approval is stored here as a PENDING version and only
 * written to the payload when approved. At most one version per payload is PENDING.
 */
@Getter
@Setter
@Entity
@Table(name = "payload_versions")
@EntityListeners(ModelBaseListener.class)
// payload_versions is a tenant-v2 active table (inspector + can_access_tenant), like payloads, so
// no v1 @Filter.
public class PayloadVersion implements TenantBase {

  public enum STATUS {
    PENDING,
    APPROVED,
    REJECTED,
    SUPERSEDED
  }

  /** What submitted the version. */
  public enum ORIGIN {
    UPDATE,
    COLLECTOR
  }

  @Id
  @ControlledUuidGeneration
  @Column(name = "payload_version_id")
  @NotBlank
  private String id;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_id", updatable = false, nullable = false)
  @JsonIgnore
  private Payload payload;

  @Column(name = "payload_version_number", nullable = false)
  private int number;

  @Column(name = "payload_version_status", nullable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private STATUS status;

  @Column(name = "payload_version_origin", nullable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private ORIGIN origin;

  @Type(JsonType.class)
  @Column(name = "payload_version_snapshot", nullable = false)
  @NotNull
  private PayloadExecutableContent snapshot;

  @Column(name = "payload_version_fingerprint", nullable = false)
  @NotBlank
  private String fingerprint;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_version_author")
  @JsonIgnore
  private User author;

  // Name snapshots, so the history stays readable after a user is deleted.
  @Column(name = "payload_version_author_name")
  private String authorName;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_version_decider")
  @JsonIgnore
  private User decider;

  @Column(name = "payload_version_decider_name")
  private String deciderName;

  @Column(name = "payload_version_comment")
  private String comment;

  @Column(name = "payload_version_created_at", nullable = false, updatable = false)
  @NotNull
  private Instant createdAt = now();

  @Column(name = "payload_version_decided_at")
  private Instant decidedAt;
}
