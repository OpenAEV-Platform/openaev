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
  public void computeExpectations() {
    Collector collector = this.collectorService.collector(config.getId());
    List<BaseInjectExpectation> expectations =
        this.injectExpectationService.expectationsNotFillAndExpired(BATCH_SIZE);
    List<BaseInjectExpectation> updated = new ArrayList<>();
    this.processAgentExpectations(expectations, collector);
    this.processRemainingExpectations(expectations, collector, updated);
    this.injectExpectationService.updateAll(updated);
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
    remainingExpectations.forEach(
        expectation -> {
          InjectExpectationUpdateInput input = new InjectExpectationUpdateInput();
          input.setIsSuccess(false);
          input.setResult(computeFailedMessage(expectation.getType()));
          expireEmptyResults(expectation.getResults(), FAILED_SCORE_VALUE, EXPIRED);
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
  }
}
