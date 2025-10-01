package io.openaev.injectors.openaev;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.model.expectation.DetectionExpectation.*;
import static io.openaev.model.expectation.ManualExpectation.*;
import static io.openaev.model.expectation.PreventionExpectation.*;
import static io.openaev.utils.AgentUtils.getActiveAgents;
import static io.openaev.utils.ExpectationUtils.*;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAssetGroup;

import io.openaev.database.model.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Injector;
import io.openaev.injectors.openaev.model.OpenAEVImplantInjectContent;
import io.openaev.model.ExecutionProcess;
import io.openaev.model.Expectation;
import io.openaev.model.expectation.DetectionExpectation;
import io.openaev.model.expectation.ManualExpectation;
import io.openaev.model.expectation.PreventionExpectation;
import io.openaev.model.expectation.VulnerabilityExpectation;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component(OpenAEVImplantContract.TYPE)
@RequiredArgsConstructor
@Slf4j
public class OpenAEVImplantExecutor extends Injector {

  private final AssetGroupService assetGroupService;
  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;

  @Override
  public ExecutionProcess process(Execution execution, ExecutableInject injection)
      throws Exception {
    Inject inject = this.injectService.inject(injection.getInjection().getInject().getId());

    List<AssetToExecute> assetToExecutes = this.injectService.resolveAllAssetsToExecute(inject);

    // Check assetToExecutes target
    if (assetToExecutes.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "Found 0 asset to execute the ability on (likely this inject does not have any target or the targeted asset is inactive and has been purged)",
              ExecutionTraceAction.COMPLETE));
    }

    // Compute expectations
    OpenAEVImplantInjectContent content =
        contentConvert(injection, OpenAEVImplantInjectContent.class);

    List<Expectation> expectations = new ArrayList<>();

    assetToExecutes.forEach(
        assetToExecute ->
            computeExpectationsForAssetAndAgents(expectations, content, assetToExecute, inject));

    List<AssetGroup> assetGroups = injection.getAssetGroups();
    assetGroups.forEach(
        (assetGroup -> computeExpectationsForAssetGroup(expectations, content, assetGroup)));

    injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);

    return new ExecutionProcess(true);
  }

  // -- PRIVATE --

  /** In case of direct assetToExecute, we have an individual expectation for the assetToExecute */
  private void computeExpectationsForAssetAndAgents(
      @NotNull final List<Expectation> expectations,
      @NotNull final OpenAEVImplantInjectContent content,
      @NotNull final AssetToExecute assetToExecute,
      final Inject inject) {

    if (!content.getExpectations().isEmpty()) {

      Map<String, Endpoint> valueTargetedAssetsMap = injectService.getValueTargetedAssetMap(inject);

      expectations.addAll(
          content.getExpectations().stream()
              .flatMap(
                  expectation ->
                      switch (expectation.getType()) {
                        case PREVENTION ->
                            getPreventionExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                valueTargetedAssetsMap,
                                inject.getId())
                                .stream();
                        case DETECTION ->
                            getDetectionExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                valueTargetedAssetsMap,
                                inject.getId())
                                .stream();
                        case VULNERABILITY ->
                            getVulnerabilityExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                valueTargetedAssetsMap,
                                inject.getId())
                                .stream();
                        case MANUAL ->
                            getManualExpectationsByAsset(
                                OAEV_IMPLANT,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation)
                                .stream();
                        default -> Stream.of();
                      })
              .toList());
    }
  }

  /**
   * In case of asset group if expectation group -> we have an expectation for the group and one for
   * each asset if not expectation group -> we have an individual expectation for each asset
   */
  private void computeExpectationsForAssetGroup(
      @NotNull final List<Expectation> expectations,
      @NotNull final OpenAEVImplantInjectContent content,
      @NotNull final AssetGroup assetGroup) {
    if (!content.getExpectations().isEmpty()) {
      expectations.addAll(
          content.getExpectations().stream()
              .flatMap(
                  expectation ->
                      switch (expectation.getType()) {
                        case PREVENTION -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              prevExpectation ->
                                                  InjectExpectation.EXPECTATION_TYPE.PREVENTION
                                                      == prevExpectation.type())
                                          .anyMatch(
                                              prevExpectation ->
                                                  ((PreventionExpectation) prevExpectation)
                                                              .getAsset()
                                                          != null
                                                      && ((PreventionExpectation) prevExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                preventionExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case DETECTION -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              detExpectation ->
                                                  InjectExpectation.EXPECTATION_TYPE.DETECTION
                                                      == detExpectation.type())
                                          .anyMatch(
                                              detExpectation ->
                                                  ((DetectionExpectation) detExpectation).getAsset()
                                                          != null
                                                      && ((DetectionExpectation) detExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                detectionExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case VULNERABILITY -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              vulExpectation ->
                                                  InjectExpectation.EXPECTATION_TYPE.VULNERABILITY
                                                      == vulExpectation.type())
                                          .anyMatch(
                                              vulExpectation ->
                                                  ((VulnerabilityExpectation) vulExpectation)
                                                              .getAsset()
                                                          != null
                                                      && ((VulnerabilityExpectation) vulExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                vulnerabilityExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case MANUAL -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              manExpectation ->
                                                  InjectExpectation.EXPECTATION_TYPE.MANUAL
                                                      == manExpectation.type())
                                          .anyMatch(
                                              manExpectation ->
                                                  ((ManualExpectation) manExpectation).getAsset()
                                                          != null
                                                      && ((ManualExpectation) manExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                manualExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.getExpirationTime(),
                                    expectation.isExpectationGroup()));
                          }
                          yield Stream.of();
                        }
                        default -> Stream.of();
                      })
              .toList());
    }
  }
}
