package io.openaev.database.model;

import static io.openaev.helper.InjectExpectationHelper.computeStatus;
import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity(name = "InjectExpectation")
@Table(name = "injects_expectations")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "inject_expectation_type", discriminatorType = DiscriminatorType.STRING)
@EntityListeners(ModelBaseListener.class)
public class BaseInjectExpectation implements Base, Cloneable {

  /**
   * Creates a shallow clone of this InjectExpectation with deep-copied collections.
   *
   * <p>The following collections are deep-copied to prevent shared mutable state:
   *
   * <ul>
   *   <li>{@code signatures} - copied to new ArrayList
   *   <li>{@code results} - copied to new ArrayList
   * </ul>
   *
   * <p><strong>Important:</strong> The {@code traces} collection is intentionally NOT copied and
   * will be empty in the clone. Traces represent execution history that belongs to the original
   * expectation instance and should not be duplicated when cloning for a new execution context.
   *
   * @return a new InjectExpectation with copied signatures and results, but empty traces
   */
  @Override
  public BaseInjectExpectation clone() {
    try {
      BaseInjectExpectation clone = (BaseInjectExpectation) super.clone();
      clone.signatures =
          this.signatures != null ? new ArrayList<>(this.signatures) : new ArrayList<>();
      clone.results = this.results != null ? new ArrayList<>(this.results) : new ArrayList<>();
      clone.traces = new ArrayList<>();
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone should be supported for Cloneable objects", e);
    }
  }

  public enum EXPECTATION_TYPE {
    ARTICLE,
    CHALLENGE,
    MANUAL,
    PREVENTION,
    DETECTION,
    VULNERABILITY;

    // Compile-time constants for @DiscriminatorValue (annotations require String constants)
    public static final String ARTICLE_VALUE = "ARTICLE";
    public static final String CHALLENGE_VALUE = "CHALLENGE";
    public static final String MANUAL_VALUE = "MANUAL";
    public static final String PREVENTION_VALUE = "PREVENTION";
    public static final String DETECTION_VALUE = "DETECTION";
    public static final String VULNERABILITY_VALUE = "VULNERABILITY";
  }

  public enum EXPECTATION_STATUS {
    FAILED,
    PENDING,
    PARTIAL,
    UNKNOWN,
    SUCCESS
  }

  @Queryable(filterable = true, label = "inject expectation type")
  @Column(name = "inject_expectation_type", insertable = false, updatable = false)
  @JsonProperty("inject_expectation_type")
  @Enumerated(EnumType.STRING)
  @Getter(AccessLevel.NONE)
  private EXPECTATION_TYPE type;

  /**
   * Returns the expectation type. When the entity has been read from the database, Hibernate
   * populates the field via the discriminator column. For newly created (not yet persisted)
   * instances the field is {@code null} because it is {@code insertable = false}; in that case we
   * derive the value from the {@link DiscriminatorValue} annotation present on the concrete
   * subclass, so callers always get a non-null result without relying on persistence.
   */
  public EXPECTATION_TYPE getType() {
    if (type != null) {
      return type;
    }
    DiscriminatorValue dv = this.getClass().getAnnotation(DiscriminatorValue.class);
    if (dv != null) {
      return EXPECTATION_TYPE.valueOf(dv.value());
    }
    return null;
  }

  // region basic
  @Id
  @NotBlank
  @Setter
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "inject_expectation_id")
  @JsonProperty("inject_expectation_id")
  private String id;

  @Setter
  @Column(name = "inject_expectation_name")
  @JsonProperty("inject_expectation_name")
  private String name;

  @Setter
  @Column(name = "inject_expectation_description")
  @JsonProperty("inject_expectation_description")
  private String description;

  @Setter
  @OneToMany(
      mappedBy = "injectExpectation",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  // Batch fetching instead of @Fetch(SUBSELECT): the collector polling endpoints load up to 10k
  // expectations with a NATIVE query (subselect fetching does not apply to those owners) and then
  // initialize this collection for serialization. Batching keeps that to one IN-clause query per
  // 1000 expectations instead of one query per expectation.
  @BatchSize(size = 1000)
  @JsonProperty("inject_expectation_signatures")
  private List<InjectExpectationSignature> signatures = new ArrayList<>();

  @Setter
  @Type(JsonType.class)
  @Column(name = "inject_expectation_results")
  @JsonProperty("inject_expectation_results")
  private List<InjectExpectationResult> results = new ArrayList<>();

  @Setter
  @Column(name = "inject_expectation_score")
  @JsonProperty("inject_expectation_score")
  private Double score;

  @JsonProperty("inject_expectation_status")
  public EXPECTATION_STATUS getResponse() {
    return computeStatus(this.getScore(), this.getExpectedScore());
  }

  @Setter
  @Column(name = "inject_expectation_expected_score")
  @JsonProperty("inject_expectation_expected_score")
  @NotNull
  private Double expectedScore;

  /** Expiration time in seconds */
  @Setter
  @Column(name = "inject_expiration_time")
  @JsonProperty("inject_expiration_time")
  @NotNull
  private Long expirationTime;

  @Queryable(filterable = true, label = "created at")
  @Setter
  @Column(name = "inject_expectation_created_at")
  @JsonProperty("inject_expectation_created_at")
  @CreationTimestamp
  private Instant createdAt = now();

  @Queryable(filterable = true, label = "updated at")
  @Setter
  @Column(name = "inject_expectation_updated_at")
  @JsonProperty("inject_expectation_updated_at")
  @UpdateTimestamp
  private Instant updatedAt = now();

  @Setter
  @Column(name = "inject_expectation_group")
  @JsonProperty("inject_expectation_group")
  private boolean expectationGroup;

  /**
   * Optional display order of this expectation within its inject, ascending. Populated from the
   * injector contract (see the phishing action, which orders its human steps email {@literal ->}
   * link {@literal ->} submission) and used by the results UI to sort the chain timeline and the
   * per-type list deterministically. {@code null} means unordered (every expectation that does not
   * declare an order), and the UI falls back to name / id.
   */
  @Setter
  @Column(name = "inject_expectation_order")
  @JsonProperty("inject_expectation_order")
  private Integer order;

  /**
   * Security platform types expected to fulfil this (technical) expectation. When non-empty, only
   * collectors of those types are pre-seeded as pending results and considered for scoring. Empty
   * or null means "any security platform" (legacy behaviour).
   */
  @Setter
  @Type(JsonType.class)
  @Column(name = "inject_expectation_expected_security_platforms", columnDefinition = "jsonb")
  @JsonProperty("inject_expectation_expected_security_platforms")
  private List<SecurityPlatform.SECURITY_PLATFORM_TYPE> expectedSecurityPlatforms =
      new ArrayList<>();

  @Setter
  @Column(name = "inject_expectation_signatures_initialized")
  @JsonIgnore
  private boolean signaturesInitialized = false;

  // endregion

  // region contextual relations
  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exercise_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_exercise")
  @Schema(implementation = String.class)
  private Exercise exercise;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inject_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_inject")
  @Schema(implementation = String.class)
  private Inject inject;

  // endregion

  @OneToMany(
      mappedBy = "injectExpectation",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @JsonProperty("inject_expectation_traces")
  private List<InjectExpectationTrace> traces = new ArrayList<>();

  @Setter(AccessLevel.PROTECTED)
  @Transient
  private String successLabel = "Successful";

  @Setter(AccessLevel.PROTECTED)
  @Transient
  private String failureLabel = "Failed";

  /**
   * True when the collection window of this expectation is over: no collector, player or manual
   * validation can fulfil it anymore. Mirrors the SQL predicate used by the expectations expiration
   * manager ({@code created_at + expiration_time seconds < now()}).
   */
  @JsonIgnore
  public boolean isExpired() {
    return expirationTime != null
        && createdAt != null
        && createdAt.plusSeconds(expirationTime).isBefore(now());
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
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
