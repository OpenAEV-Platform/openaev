package io.openaev.database.model;

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

  /** {@inheritDoc} The expected security platform list is copied to avoid shared mutable state. */
  @Override
  public TechnicalInjectExpectation clone() {
    TechnicalInjectExpectation clone = (TechnicalInjectExpectation) super.clone();
    clone.expectedSecurityPlatforms =
        this.expectedSecurityPlatforms != null
            ? new ArrayList<>(this.expectedSecurityPlatforms)
            : new ArrayList<>();
    return clone;
  }
}
