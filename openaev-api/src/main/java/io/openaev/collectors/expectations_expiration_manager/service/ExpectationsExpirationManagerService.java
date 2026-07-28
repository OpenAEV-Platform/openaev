package io.openaev.collectors.expectations_expiration_manager.service;

import static io.openaev.collectors.expectations_expiration_manager.utils.ExpectationUtils.*;
import static io.openaev.service.InjectExpectationUtils.FAILED_SCORE_VALUE;
import static io.openaev.utils.ExpectationUtils.HUMAN_EXPECTATION;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.expireEmptyResults;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.ExpectationUtils;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class ExpectationsExpirationManagerService {

  private final InjectExpectationService injectExpectationService;
  private final ExpectationsExpirationManagerConfig config;
  private final CollectorService collectorService;

  public static final String EXPIRED = "Expired";

  private static final int BATCH_SIZE = 1000;

  @Transactional(rollbackFor = Exception.class)
  public void computeExpectations(String tenantId) {
    Collector collector = this.collectorService.collector(config.getId());
    List<BaseInjectExpectation> expectations =
        this.injectExpectationService.expectationsNotFillAndExpired(tenantId, BATCH_SIZE);
    log.debug(
        "Found {} pending expired expectations for tenant {} (expirationTime={}s, assetExpirationTime={}s)",
        expectations.size(),
        tenantId,
        config.getExpirationTime(),
        config.getExpirationTimeForAsset());
    List<BaseInjectExpectation> updated = new ArrayList<>();
    this.processAgentExpectations(expectations, collector);
    this.processRemainingExpectations(expectations, collector, updated);
    this.injectExpectationService.updateAll(updated);
    if (!expectations.isEmpty()) {
      log.debug("Expired {} expectations for tenant {}", expectations.size(), tenantId);
    }
  }

  // -- PRIVATE --
  private void processAgentExpectations(
      @NotNull final List<BaseInjectExpectation> expectations, @NotNull final Collector collector) {
    List<TechnicalInjectExpectation> expiredExpectations = new ArrayList<>();
    Map<String, InjectExpectationUpdateInput> inputsById = new LinkedHashMap<>();
    for (BaseInjectExpectation expectation : expectations) {
      if (!(expectation instanceof TechnicalInjectExpectation technicalExpectation)
          || !ExpectationUtils.isAgentExpectation(technicalExpectation)) {
        continue;
      }
      InjectExpectationUpdateInput input = buildExpirationInput(expectation);
      expiredExpectations.add(technicalExpectation);
      inputsById.put(expectation.getId(), input);
    }
    // Batched: one save for all agent expirations plus one propagation per distinct parent,
    // instead of one save + full propagation chain per expectation
    this.injectExpectationService.bulkComputeTechnicalExpectations(
        expiredExpectations, inputsById, collector, true);
  }

  private void processRemainingExpectations(
      @NotNull final List<BaseInjectExpectation> expectations,
      @NotNull final Collector collector,
      @NotNull final List<BaseInjectExpectation> updated) {
    List<BaseInjectExpectation> remainingExpectations =
        expectations.stream().filter(exp -> exp.getScore() == null).toList();

    // PARENT technical expectations (asset level with agent children, asset group level) must
    // ALWAYS be derived from their children, never force-failed with a direct "Expired" result:
    // when a security platform already answered the agents (e.g. Microsoft Defender green
    // PREVENTED/DETECTED) but the parent score is still null at expiration time, force-failing the
    // parent permanently cements a wrong "Not prevented"/"Not detected" verdict on the asset while
    // its agents show green, corrupting every statistic built on the parent rows. Recompute them
    // from children instead; asset parents first so asset group parents read fresh asset scores.
    List<TechnicalInjectExpectation> assetParents = new ArrayList<>();
    List<TechnicalInjectExpectation> assetGroupParents = new ArrayList<>();
    List<BaseInjectExpectation> directlyAnswerable = new ArrayList<>();
    for (BaseInjectExpectation expectation : remainingExpectations) {
      if (expectation instanceof TechnicalInjectExpectation technicalExpectation
          && injectExpectationService.isParentTechnicalExpectation(technicalExpectation)) {
        if (ExpectationUtils.isAssetGroupExpectation(technicalExpectation)) {
          assetGroupParents.add(technicalExpectation);
        } else {
          assetParents.add(technicalExpectation);
        }
      } else {
        directlyAnswerable.add(expectation);
      }
    }
    // Genuine leaves (human expectations, agentless assets / AI targets) are answered directly
    // with the type's default verdict (failed, except VULNERABILITY where silence means "Not
    // vulnerable"). Processed FIRST so parent recomputation below reads the final leaf scores
    // (an asset group may aggregate agentless asset leaves).
    directlyAnswerable.forEach(
        expectation -> {
          InjectExpectationUpdateInput input = buildExpirationInput(expectation);
          if (HUMAN_EXPECTATION.contains(expectation.getType())) {
            updated.add(
                injectExpectationService.computeInjectExpectationForHumanResponse(
                    expectation, input, collector));
          } else if (expectation instanceof TechnicalInjectExpectation technicalExpectation) {
            updated.add(
                injectExpectationService.computeInjectExpectationForAgentOrAssetAgentless(
                    technicalExpectation, input, collector));
          }
        });

    assetParents.forEach(
        parent ->
            updated.addAll(injectExpectationService.recomputeParentTechnicalExpectation(parent)));
    assetGroupParents.forEach(
        parent ->
            updated.addAll(injectExpectationService.recomputeParentTechnicalExpectation(parent)));
  }

  /**
   * Builds the expiration verdict for an unanswered expectation, honoring the type's signal
   * polarity: silence means FAILURE for detection/prevention/human expectations (nothing was
   * prevented, detected or validated within the window), but SUCCESS for VULNERABILITY (no scanner
   * reported a finding, so the target defaults to "Not vulnerable"). Empty per-source result rows
   * are expired with the matching score.
   *
   * @param expectation the expired, unanswered expectation
   * @return the update input carrying the expiration verdict
   */
  private InjectExpectationUpdateInput buildExpirationInput(
      @NotNull final BaseInjectExpectation expectation) {
    InjectExpectationUpdateInput input = new InjectExpectationUpdateInput();
    if (ExpectationType.VULNERABILITY.toString().equals(expectation.getType().toString())) {
      input.setIsSuccess(true);
      input.setResult(computeSuccessMessage(expectation.getType()));
      expireEmptyResults(expectation.getResults(), expectation.getExpectedScore(), EXPIRED);
    } else {
      input.setIsSuccess(false);
      input.setResult(computeFailedMessage(expectation.getType()));
      expireEmptyResults(expectation.getResults(), FAILED_SCORE_VALUE, EXPIRED);
    }
    return input;
  }
}
