package io.openaev.model.expectation;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.DETECTION;

import io.openaev.database.model.*;
import io.openaev.model.Expectation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetectionExpectation implements Expectation {

  private Double score;
  private String name;
  private String description;
  private Agent agent;
  private Asset asset;
  private AssetGroup assetGroup;
  private boolean expectationGroup;
  private Long expirationTime;
  private List<InjectExpectationSignature> injectExpectationSignatures;

  private DetectionExpectation() {}

  @Override
  public InjectExpectation.EXPECTATION_TYPE type() {
    return DETECTION;
  }

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
