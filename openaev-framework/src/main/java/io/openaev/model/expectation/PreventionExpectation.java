package io.openaev.model.expectation;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.PREVENTION;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.InjectExpectationSignature;
import io.openaev.model.Expectation;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Expectation that requires security controls to prevent an injected activity.
 *
 * <p>Prevention expectations verify that security controls (firewalls, endpoint protection, etc.)
 * successfully block or prevent simulated attack activities. This measures the effectiveness of
 * preventive security capabilities.
 *
 * <p>Prevention expectations can target:
 *
 * <ul>
 *   <li>A specific agent on an asset
 *   <li>An individual asset
 *   <li>An asset group (evaluated collectively or individually)
 * </ul>
 *
 * @see Expectation
 * @see InjectExpectationSignature
 */
@Getter
@Setter
public class PreventionExpectation implements Expectation {

  /** The score value when this expectation is fulfilled (0-100). */
  private Double score;

  /** Display name for this expectation. */
  private String name;

  /** Detailed description of what should be prevented. */
  private String description;

  /** The specific agent being monitored for prevention. */
  private Agent agent;

  /** The asset where prevention should occur. */
  private Asset asset;

  /** The asset group being monitored. */
  private AssetGroup assetGroup;

  /** Whether this expectation is part of a group evaluation. */
  private boolean expectationGroup;

  /** Time in seconds after which this expectation expires. */
  private Long expirationTime;

  /** Signatures that can satisfy this prevention expectation. */
  private List<InjectExpectationSignature> injectExpectationSignatures;

  private PreventionExpectation() {}

  @Override
  public EXPECTATION_TYPE type() {
    return PREVENTION;
  }

  /**
   * Creates a prevention expectation targeting a specific agent on an asset.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   * @param name the display name for this expectation
   * @param description detailed description of the expected prevention
   * @param agent the agent to monitor
   * @param asset the asset where the agent resides
   * @param assetGroup optional asset group for grouping
   * @param expirationTime time in seconds until expiration
   * @param injectExpectationSignatures signatures that satisfy this expectation
   * @return a configured PreventionExpectation
   */
  public static PreventionExpectation preventionExpectationForAgent(
      @Nullable final Double score,
      @NotBlank final String name,
      final String description,
      @NotNull Agent agent,
      @NotNull final Asset asset,
      final AssetGroup assetGroup,
      final Long expirationTime,
      final List<InjectExpectationSignature> injectExpectationSignatures) {
    PreventionExpectation preventionExpectation = new PreventionExpectation();
    preventionExpectation.setScore(Objects.requireNonNullElse(score, 100.0));
    preventionExpectation.setName(name);
    preventionExpectation.setDescription(description);
    preventionExpectation.setAgent(agent);
    preventionExpectation.setAsset(asset);
    preventionExpectation.setAssetGroup(assetGroup);
    preventionExpectation.setExpirationTime(expirationTime);
    preventionExpectation.setInjectExpectationSignatures(injectExpectationSignatures);
    return preventionExpectation;
  }

  /**
   * Creates a prevention expectation targeting an asset.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   * @param name the display name for this expectation
   * @param description detailed description of the expected prevention
   * @param asset the asset to monitor
   * @param assetGroup optional asset group (sets expectationGroup=true if present)
   * @param expirationTime time in seconds until expiration
   * @return a configured PreventionExpectation
   */
  public static PreventionExpectation preventionExpectationForAsset(
      @Nullable final Double score,
      @NotBlank final String name,
      final String description,
      @NotNull final Asset asset,
      final AssetGroup assetGroup,
      final Long expirationTime) {
    PreventionExpectation preventionExpectation = new PreventionExpectation();
    preventionExpectation.setScore(Objects.requireNonNullElse(score, 100.0));
    preventionExpectation.setName(name);
    preventionExpectation.setDescription(description);
    preventionExpectation.setAsset(asset);
    preventionExpectation.setAssetGroup(assetGroup);
    preventionExpectation.setExpectationGroup(assetGroup != null);
    preventionExpectation.setExpirationTime(expirationTime);
    return preventionExpectation;
  }

  /**
   * Creates a prevention expectation targeting an asset group.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   * @param name the display name for this expectation
   * @param description detailed description of the expected prevention
   * @param assetGroup the asset group to monitor
   * @param expectationGroup whether to evaluate as a group or individually
   * @param expirationTime time in seconds until expiration
   * @return a configured PreventionExpectation
   */
  public static PreventionExpectation preventionExpectationForAssetGroup(
      @Nullable final Double score,
      @NotBlank final String name,
      final String description,
      @NotNull final AssetGroup assetGroup,
      final boolean expectationGroup,
      @NotNull final Long expirationTime) {
    PreventionExpectation preventionExpectation = new PreventionExpectation();
    preventionExpectation.setScore(Objects.requireNonNullElse(score, 100.0));
    preventionExpectation.setName(name);
    preventionExpectation.setDescription(description);
    preventionExpectation.setAssetGroup(assetGroup);
    preventionExpectation.setExpectationGroup(expectationGroup);
    preventionExpectation.setExpirationTime(expirationTime);
    return preventionExpectation;
  }
}
