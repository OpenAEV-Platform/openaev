package io.openaev.model.expectation;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.model.Expectation;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Expectation that requires manual verification by an operator.
 *
 * <p>Manual expectations are fulfilled when an operator manually confirms that a specific outcome
 * has been achieved. This is useful for verifying results that cannot be automatically measured,
 * such as:
 *
 * <ul>
 *   <li>Physical security responses
 *   <li>Human behavior observations
 *   <li>Qualitative assessments
 *   <li>External system verifications
 * </ul>
 *
 * <p>Manual expectations can target agents, assets, or asset groups.
 *
 * @see Expectation
 */
@Getter
@Setter
@EqualsAndHashCode
public class ManualExpectation implements Expectation {

  /** The score value when this expectation is fulfilled (0-100). */
  private Double score;

  /** Display name for this expectation. */
  private String name;

  /** Detailed description of what should be verified. */
  private String description;

  /** The specific agent associated with this expectation. */
  private Agent agent;

  /** The asset where verification should occur. */
  private Asset asset;

  /** The asset group being evaluated. */
  private AssetGroup assetGroup;

  /** Whether this expectation is part of a group evaluation. */
  private boolean expectationGroup;

  /** Time in seconds after which this expectation expires. */
  private Long expirationTime;

  /** Creates an empty manual expectation. */
  public ManualExpectation() {}

  /**
   * Creates a manual expectation with the specified score.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   */
  public ManualExpectation(final Double score) {
    this.score = Objects.requireNonNullElse(score, 100.0);
  }

  /**
   * Creates a manual expectation from a form expectation.
   *
   * @param expectation the form expectation containing configuration
   */
  public ManualExpectation(io.openaev.model.inject.form.Expectation expectation) {
    this(expectation.getScore());
    this.name = expectation.getName();
    this.description = expectation.getDescription();
    this.expectationGroup = expectation.isExpectationGroup();
    this.expirationTime = expectation.getExpirationTime();
  }

  /**
   * Creates a manual expectation targeting a specific agent on an asset.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   * @param name the display name for this expectation
   * @param description detailed description of what to verify
   * @param agent the agent to evaluate
   * @param asset the asset where the agent resides
   * @param assetGroup optional asset group for grouping
   * @param expirationTime time in seconds until expiration
   * @return a configured ManualExpectation
   */
  public static ManualExpectation manualExpectationForAgent(
      @Nullable final Double score,
      @NotBlank final String name,
      final String description,
      @NotNull final Agent agent,
      @NotNull final Asset asset,
      final AssetGroup assetGroup,
      final Long expirationTime) {
    ManualExpectation manualExpectation = new ManualExpectation();
    manualExpectation.setScore(Objects.requireNonNullElse(score, 100.0));
    manualExpectation.setName(name);
    manualExpectation.setDescription(description);
    manualExpectation.setAgent(agent);
    manualExpectation.setAsset(asset);
    manualExpectation.setAssetGroup(assetGroup);
    manualExpectation.setExpirationTime(expirationTime);
    return manualExpectation;
  }

  /**
   * Creates a manual expectation targeting an asset.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   * @param name the display name for this expectation
   * @param description detailed description of what to verify
   * @param asset the asset to evaluate
   * @param assetGroup optional asset group (sets expectationGroup=true if present)
   * @param expirationTime time in seconds until expiration
   * @return a configured ManualExpectation
   */
  public static ManualExpectation manualExpectationForAsset(
      @Nullable final Double score,
      @NotBlank final String name,
      final String description,
      @NotNull final Asset asset,
      final AssetGroup assetGroup,
      final Long expirationTime) {
    ManualExpectation manualExpectation = new ManualExpectation();
    manualExpectation.setScore(Objects.requireNonNullElse(score, 100.0));
    manualExpectation.setName(name);
    manualExpectation.setDescription(description);
    manualExpectation.setAsset(asset);
    manualExpectation.setAssetGroup(assetGroup);
    manualExpectation.setExpirationTime(expirationTime);
    manualExpectation.setExpectationGroup(assetGroup != null);
    return manualExpectation;
  }

  /**
   * Creates a manual expectation targeting an asset group.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   * @param name the display name for this expectation
   * @param description detailed description of what to verify
   * @param assetGroup the asset group to evaluate
   * @param expirationTime time in seconds until expiration
   * @param expectationGroup whether to evaluate as a group or individually
   * @return a configured ManualExpectation
   */
  public static ManualExpectation manualExpectationForAssetGroup(
      @Nullable final Double score,
      @NotBlank final String name,
      final String description,
      @NotNull final AssetGroup assetGroup,
      final Long expirationTime,
      final boolean expectationGroup) {
    ManualExpectation manualExpectation = new ManualExpectation();
    manualExpectation.setScore(Objects.requireNonNullElse(score, 100.0));
    manualExpectation.setName(name);
    manualExpectation.setDescription(description);
    manualExpectation.setAssetGroup(assetGroup);
    manualExpectation.setExpirationTime(expirationTime);
    manualExpectation.setExpectationGroup(expectationGroup);
    return manualExpectation;
  }

  @Override
  public EXPECTATION_TYPE type() {
    return EXPECTATION_TYPE.MANUAL;
  }
}
