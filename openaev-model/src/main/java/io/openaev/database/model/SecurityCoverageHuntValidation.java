package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Outbox row of the OpenCTI hunt validation loop: one emulated (inject, ATT&CK technique, security
 * platform) triple that OpenCTI is asked to validate its hunts against, through the {@code
 * huntValidateFromEmulation} mutation.
 *
 * <p>The unique key {@code (inject, technique, security platform, tenant)} makes the validation
 * idempotent: the security coverage job plans a triple once, however many times it reruns for the
 * simulation, and the delivery job sends it until OpenCTI accepts it or the attempts run out.
 *
 * <p>Deliberately not listened ({@link #isListened()}): these rows are integration internals and
 * must not be broadcast over SSE.
 *
 * <p>Fully on multi-tenancy v2. The single writer ({@code SecurityCoverageHuntValidationService})
 * stamps the simulation tenant explicitly inside a per-tenant scoped transaction, so there is no v1
 * {@code @Filter} and no {@code TenantBaseListener}.
 */
@Entity
@Getter
@Setter
@Table(name = "security_coverage_hunt_validations")
@EntityListeners(AuditableListener.class)
public class SecurityCoverageHuntValidation implements TenantBase, Auditable {

  public enum Status {
    /** Planned, waiting for (another) delivery attempt. */
    PENDING,
    /** Accepted by OpenCTI, never sent again. */
    VALIDATED,
    /** Every attempt failed, never sent again. */
    FAILED
  }

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "security_coverage_hunt_validation_id")
  @JsonProperty("security_coverage_hunt_validation_id")
  @NotBlank
  private String id;

  @Column(name = "security_coverage_hunt_validation_inject_id", nullable = false, updatable = false)
  @JsonProperty("security_coverage_hunt_validation_inject_id")
  @NotBlank
  private String injectId;

  /** ATT&CK external id of the emulated technique (e.g. {@code T1059.001}). */
  @Column(
      name = "security_coverage_hunt_validation_technique_id",
      nullable = false,
      updatable = false)
  @JsonProperty("security_coverage_hunt_validation_technique_id")
  @NotBlank
  private String techniqueId;

  @Column(
      name = "security_coverage_hunt_validation_security_platform_id",
      nullable = false,
      updatable = false)
  @JsonProperty("security_coverage_hunt_validation_security_platform_id")
  @NotBlank
  private String securityPlatformId;

  @Column(name = "security_coverage_hunt_validation_security_platform_name", nullable = false)
  @JsonProperty("security_coverage_hunt_validation_security_platform_name")
  @NotBlank
  private String securityPlatformName;

  /** STIX id of the OpenCTI Security Coverage the simulation was generated from. */
  @Column(name = "security_coverage_hunt_validation_coverage_external_id", nullable = false)
  @JsonProperty("security_coverage_hunt_validation_coverage_external_id")
  @NotBlank
  private String coverageExternalId;

  @Column(name = "security_coverage_hunt_validation_window_start", nullable = false)
  @JsonProperty("security_coverage_hunt_validation_window_start")
  @NotNull
  private Instant windowStart;

  @Column(name = "security_coverage_hunt_validation_window_end", nullable = false)
  @JsonProperty("security_coverage_hunt_validation_window_end")
  @NotNull
  private Instant windowEnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "security_coverage_hunt_validation_status", nullable = false)
  @JsonProperty("security_coverage_hunt_validation_status")
  @NotNull
  private Status status = Status.PENDING;

  @Column(name = "security_coverage_hunt_validation_attempts", nullable = false)
  @JsonProperty("security_coverage_hunt_validation_attempts")
  private int attempts = 0;

  @Column(name = "security_coverage_hunt_validation_next_attempt_at", nullable = false)
  @JsonProperty("security_coverage_hunt_validation_next_attempt_at")
  @NotNull
  private Instant nextAttemptAt;

  @Column(name = "security_coverage_hunt_validation_last_error", columnDefinition = "text")
  @JsonProperty("security_coverage_hunt_validation_last_error")
  private String lastError;

  @Column(name = "security_coverage_hunt_validation_hunts_count")
  @JsonProperty("security_coverage_hunt_validation_hunts_count")
  private Integer huntsCount;

  @Column(name = "security_coverage_hunt_validation_runs_count")
  @JsonProperty("security_coverage_hunt_validation_runs_count")
  private Integer runsCount;

  @Column(name = "security_coverage_hunt_validation_validated_at")
  @JsonProperty("security_coverage_hunt_validation_validated_at")
  private Instant validatedAt;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Column(
      name = "security_coverage_hunt_validation_created_at",
      nullable = false,
      updatable = false)
  @JsonProperty("security_coverage_hunt_validation_created_at")
  private Instant createdAt;

  @Column(name = "security_coverage_hunt_validation_updated_at", nullable = false)
  @JsonProperty("security_coverage_hunt_validation_updated_at")
  private Instant updatedAt;

  @Override
  @JsonIgnore
  public boolean isListened() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return id != null && id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
