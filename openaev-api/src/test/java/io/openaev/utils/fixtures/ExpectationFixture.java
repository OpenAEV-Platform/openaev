package io.openaev.utils.fixtures;

import static io.openaev.expectation.ExpectationBuilderService.*;
import static io.openaev.model.expectation.DetectionExpectation.*;
import static io.openaev.model.expectation.PreventionExpectation.preventionExpectationForAgent;
import static io.openaev.model.expectation.PreventionExpectation.preventionExpectationForAsset;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAgent;

import io.openaev.database.model.*;
import io.openaev.model.expectation.DetectionExpectation;
import io.openaev.model.expectation.PreventionExpectation;
import io.openaev.model.expectation.VulnerabilityExpectation;
import io.openaev.model.inject.form.Expectation;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class ExpectationFixture {

  static Double SCORE = 100.0;

  public static Expectation createExpectation(
      InjectExpectation.EXPECTATION_TYPE expectationType, String expectationName) {
    Expectation expectation = new Expectation();
    expectation.setExpectationGroup(false);
    expectation.setName(expectationName);
    expectation.setDescription("Expectation 1");
    expectation.setType(expectationType);
    expectation.setScore(10D);
    expectation.setExpirationTime(Instant.now().toEpochMilli());
    return expectation;
  }

  public static Expectation createExpectation(InjectExpectation.EXPECTATION_TYPE expectationType) {
    return createExpectation(expectationType, "Expectation 1");
  }

  public static Expectation createExpectation() {
    Expectation expectation = new Expectation();
    expectation.setScore(SCORE);
    expectation.setName("Expectation Name");
    expectation.setDescription("Expectation Description");
    expectation.setExpirationTime(60L);
    return expectation;
  }

  public static ExpectationUpdateInput getExpectationUpdateInput(String sourceId, Double score) {
    return ExpectationUpdateInput.builder()
        .sourceId(sourceId)
        .sourceName("security-platform-name")
        .sourceType("security-platform-type")
        .score(score)
        .build();
  }

  // -- DETECTION EXPECTATION --

  private static DetectionExpectation createTechnicalDetectionExpectationForAgent(
      Agent agent,
      Asset asset,
      AssetGroup assetGroup,
      Long expirationTime,
      List<InjectExpectationSignature> signatures) {
    return detectionExpectationForAgent(
        SCORE,
        DETECTION_NAME,
        "Detection Expectation",
        agent,
        asset,
        assetGroup,
        expirationTime,
        signatures);
  }

  public static DetectionExpectation createTechnicalDetectionExpectationForAsset(
      Asset asset, AssetGroup assetGroup, Long expirationTime) {
    return detectionExpectationForAsset(
        SCORE, DETECTION_NAME, "Detection Expectation", asset, assetGroup, expirationTime);
  }

  private static DetectionExpectation createDetectionExpectationForAssetGroup(
      AssetGroup assetGroup, Long expirationTime) {
    return detectionExpectationForAssetGroup(
        SCORE, DETECTION_NAME, "Detection Expectation", assetGroup, false, expirationTime);
  }

  public static List<io.openaev.model.Expectation> createDetectionExpectations(
      @NotNull final List<Agent> agents,
      @NotNull final Asset asset,
      @Nullable final AssetGroup assetGroup,
      @NotNull final Long expirationTime) {
    List<io.openaev.model.Expectation> detectionExpectations = new ArrayList<>();
    // Agent
    detectionExpectations.addAll(
        agents.stream()
            .map(
                a ->
                    createTechnicalDetectionExpectationForAgent(
                        a, asset, assetGroup, expirationTime, Collections.emptyList()))
            .toList());
    // Asset
    detectionExpectations.add(
        createTechnicalDetectionExpectationForAsset(asset, assetGroup, expirationTime));
    // Asset Group
    if (assetGroup != null) {
      detectionExpectations.add(
          createDetectionExpectationForAssetGroup(assetGroup, expirationTime));
    }
    return detectionExpectations;
  }

  // -- PREVENTION EXPECTATION --

  private static PreventionExpectation createTechnicalPreventionExpectationForAgent(
      Agent agent,
      Asset asset,
      AssetGroup assetGroup,
      Long expirationTime,
      List<InjectExpectationSignature> signatures) {
    return preventionExpectationForAgent(
        SCORE,
        PREVENTION_NAME,
        "Prevention Expectation",
        agent,
        asset,
        assetGroup,
        expirationTime,
        signatures);
  }

  private static PreventionExpectation createTechnicalPreventionExpectationForAsset(
      Asset asset, AssetGroup assetGroup, Long expirationTime) {
    return preventionExpectationForAsset(
        SCORE, PREVENTION_NAME, "Prevention Expectation", asset, assetGroup, expirationTime);
  }

  private static PreventionExpectation createPreventionExpectationForAssetGroup(
      AssetGroup assetGroup, Long expirationTime) {
    return PreventionExpectation.preventionExpectationForAssetGroup(
        SCORE, PREVENTION_NAME, "Prevention Expectation", assetGroup, false, expirationTime);
  }

  public static List<io.openaev.model.Expectation> createPreventionExpectations(
      @NotNull final List<Agent> agents,
      @NotNull final Asset asset,
      @Nullable final AssetGroup assetGroup,
      @NotNull final Long expirationTime) {
    List<io.openaev.model.Expectation> preventionExpectations = new ArrayList<>();
    // Agent
    preventionExpectations.addAll(
        agents.stream()
            .map(
                a ->
                    createTechnicalPreventionExpectationForAgent(
                        a, asset, assetGroup, expirationTime, Collections.emptyList()))
            .toList());
    // Asset
    preventionExpectations.add(
        createTechnicalPreventionExpectationForAsset(asset, assetGroup, expirationTime));
    // Asset Group
    if (assetGroup != null) {
      preventionExpectations.add(
          createPreventionExpectationForAssetGroup(assetGroup, expirationTime));
    }
    return preventionExpectations;
  }

  // --- VULNERABILITY EXPECTATION-----
  public static VulnerabilityExpectation createTechnicalVulnerabilityExpectationForAgent(
      Agent agent,
      Asset asset,
      AssetGroup assetGroup,
      Long expirationTime,
      List<InjectExpectationSignature> signatures) {
    return vulnerabilityExpectationForAgent(
        SCORE,
        VULNERABILITY_NAME,
        "Vulnerability Expectation",
        agent,
        asset,
        assetGroup,
        expirationTime,
        signatures);
  }
}
