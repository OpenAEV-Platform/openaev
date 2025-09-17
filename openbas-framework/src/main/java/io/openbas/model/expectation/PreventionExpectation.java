package io.openbas.model.expectation;

import static io.openbas.database.model.InjectExpectation.EXPECTATION_TYPE.PREVENTION;

import io.openbas.database.model.Agent;
import io.openbas.database.model.Asset;
import io.openbas.database.model.AssetGroup;
import io.openbas.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openbas.database.model.InjectExpectationSignature;
import io.openbas.model.Expectation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PreventionExpectation implements Expectation {

  private Double score;
  private String name;
  private String description;
  private Agent agent;
  private Asset asset;
  private AssetGroup assetGroup;
  private boolean expectationGroup;
  private Long expirationTime;
  private List<InjectExpectationSignature> injectExpectationSignatures;

  private PreventionExpectation() {}

  @Override
  public EXPECTATION_TYPE type() {
    return PREVENTION;
  }

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
    preventionExpectation.setExpirationTime(expirationTime);
    return preventionExpectation;
  }

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
