package io.openaev.model.expectation;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.DETECTION;

import io.openaev.database.model.*;
import io.openaev.model.Expectation;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Expectation that requires security tools to detect an injected activity.
 *
 * <p>Detection expectations verify that security monitoring systems (SIEM, EDR, etc.) properly
 * identify and alert on simulated attack activities. This measures the effectiveness of detection
 * capabilities.
 *
 * <p>Detection expectations can target:
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
public class DetectionExpectation implements Expectation {

  /** The score value when this expectation is fulfilled (0-100). */
  private Double score;

  /** Display name for this expectation. */
  private String name;

  /** Detailed description of what should be detected. */
  private String description;

  /** The specific agent being monitored for detection. */
  private Agent agent;

  /** The asset where detection should occur. */
  private Asset asset;

  /** The asset group being monitored. */
  private AssetGroup assetGroup;

  /** Whether this expectation is part of a group evaluation. */
  private boolean expectationGroup;

  /** Time in seconds after which this expectation expires. */
  private Long expirationTime;

  /** Signatures that can satisfy this detection expectation. */
  private List<InjectExpectationSignature> injectExpectationSignatures;

  private DetectionExpectation() {}

  @Override
  public InjectExpectation.EXPECTATION_TYPE type() {
    return DETECTION;
  }

  /**
   * Creates a detection expectation targeting a specific agent on an asset.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   * @param name the display name for this expectation
   * @param description detailed description of the expected detection
   * @param agent the agent to monitor
   * @param asset the asset where the agent resides
   * @param assetGroup optional asset group for grouping
   * @param expirationTime time in seconds until expiration
   * @param injectExpectationSignatures signatures that satisfy this expectation
   * @return a configured DetectionExpectation
   */
  public static DetectionExpectation detectionExpectationForAgent(
      @Nullable final Double score,
      @NotBlank final String name,
      final String description,
      @NotNull final Agent agent,
      @NotNull final Asset asset,
      final AssetGroup assetGroup,
      final Long expirationTime,
      List<InjectExpectationSignature> injectExpectationSignatures) {
    DetectionExpectation detectionExpectation = new DetectionExpectation();
    detectionExpectation.setScore(Objects.requireNonNullElse(score, 100.0));
    detectionExpectation.setName(name);
    detectionExpectation.setDescription(description);
    detectionExpectation.setAgent(agent);
    detectionExpectation.setAsset(asset);
    detectionExpectation.setAssetGroup(assetGroup);
    detectionExpectation.setExpirationTime(expirationTime);
    detectionExpectation.setInjectExpectationSignatures(injectExpectationSignatures);
    return detectionExpectation;
  }

  /**
   * Creates a detection expectation targeting an asset.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   * @param name the display name for this expectation
   * @param description detailed description of the expected detection
   * @param asset the asset to monitor
   * @param assetGroup optional asset group (sets expectationGroup=true if present)
   * @param expirationTime time in seconds until expiration
   * @return a configured DetectionExpectation
   */
  public static DetectionExpectation detectionExpectationForAsset(
      @Nullable final Double score,
      @NotBlank final String name,
      final String description,
      @NotNull final Asset asset,
      final AssetGroup assetGroup,
      final Long expirationTime) {
    DetectionExpectation detectionExpectation = new DetectionExpectation();
    detectionExpectation.setScore(Objects.requireNonNullElse(score, 100.0));
    detectionExpectation.setName(name);
    detectionExpectation.setDescription(description);
    detectionExpectation.setAsset(asset);
    detectionExpectation.setAssetGroup(assetGroup);
    detectionExpectation.setExpectationGroup(assetGroup != null);
    detectionExpectation.setExpirationTime(expirationTime);
    return detectionExpectation;
  }

  /**
   * Creates a detection expectation targeting an asset group.
   *
   * @param score the score value when fulfilled (defaults to 100.0 if null)
   * @param name the display name for this expectation
   * @param description detailed description of the expected detection
   * @param assetGroup the asset group to monitor
   * @param expectationGroup whether to evaluate as a group or individually
   * @param expirationTime time in seconds until expiration
   * @return a configured DetectionExpectation
   */
  public static DetectionExpectation detectionExpectationForAssetGroup(
      @Nullable final Double score,
      @NotBlank final String name,
      final String description,
      @NotNull final AssetGroup assetGroup,
      final boolean expectationGroup,
      final Long expirationTime) {
    DetectionExpectation detectionExpectation = new DetectionExpectation();
    detectionExpectation.setScore(Objects.requireNonNullElse(score, 100.0));
    detectionExpectation.setName(name);
    detectionExpectation.setDescription(description);
    detectionExpectation.setAssetGroup(assetGroup);
    detectionExpectation.setExpectationGroup(expectationGroup);
    detectionExpectation.setExpirationTime(expirationTime);
    return detectionExpectation;
  }
}
