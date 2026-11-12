package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

@Getter
@Setter
@Entity
public abstract class TechnicalInjectExpectation extends BaseInjectExpectation {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "agent_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_agent")
  @Schema(implementation = String.class)
  private Agent agent;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_asset")
  @Schema(implementation = String.class)
  private Asset asset;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_group_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("inject_expectation_asset_group")
  @Schema(implementation = String.class)
  private AssetGroup assetGroup;

  @Column(name = "inject_expectation_signatures_initialized")
  @JsonIgnore
  private boolean signaturesInitialized = false;

  /**
   * Frozen at initialization: {@code true} when this technical detection/prevention expectation
   * required a security platform collector to ever be fulfilled but none was connected at creation
   * time, so it was resolved as a definitive failure (score 0, empty results) instead of staying
   * pending. Persisted rather than recomputed so a collector connected later does not retroactively
   * flip the flag to {@code false} and hide why the leaf is a definitive failure. Always {@code
   * false} for vulnerability expectations (fulfilled by the assessment injector, not a collector).
   */
  @Column(name = "inject_expectation_collector_missing_at_init")
  @JsonIgnore
  private boolean collectorMissingAtInit = false;

  @OneToMany(
      mappedBy = "injectExpectation",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @JsonProperty("inject_expectation_traces")
  private List<InjectExpectationTrace> traces = new ArrayList<>();

  /**
   * Security platform types expected to fulfil this (technical) expectation. When non-empty, only
   * collectors of those types are pre-seeded as pending results and considered for scoring. Empty
   * or null means "any security platform" (legacy behaviour).
   */
  @Type(JsonType.class)
  @Column(name = "inject_expectation_expected_security_platforms", columnDefinition = "jsonb")
  @JsonProperty("inject_expectation_expected_security_platforms")
  private List<SecurityPlatform.SECURITY_PLATFORM_TYPE> expectedSecurityPlatforms =
      new ArrayList<>();

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

  /**
   * {@inheritDoc}
   *
   * <p>The {@code signatures} and {@code expectedSecurityPlatforms} lists are deep-copied to avoid
   * shared mutable state. The {@code traces} collection is intentionally reset to empty: traces
   * represent execution history that belongs to the original instance and must not be duplicated
   * when cloning for a new execution context.
   */
  @Override
  public TechnicalInjectExpectation clone() {
    TechnicalInjectExpectation clone = (TechnicalInjectExpectation) super.clone();
    clone.signatures =
        this.signatures != null ? new ArrayList<>(this.signatures) : new ArrayList<>();
    clone.expectedSecurityPlatforms =
        this.expectedSecurityPlatforms != null
            ? new ArrayList<>(this.expectedSecurityPlatforms)
            : new ArrayList<>();
    clone.traces = new ArrayList<>();
    return clone;
  }
}
