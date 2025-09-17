package io.openbas.collectors.expectations_expiration_manager.service;

import static io.openbas.collectors.expectations_expiration_manager.utils.ExpectationUtils.computeFailedMessage;
import static io.openbas.collectors.expectations_expiration_manager.utils.ExpectationUtils.isExpired;
import static io.openbas.utils.ExpectationUtils.HUMAN_EXPECTATION;
import static io.openbas.utils.inject_expectation_result.InjectExpectationResultUtils.expireEmptyResults;

import io.openbas.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openbas.database.model.Collector;
import io.openbas.database.model.InjectExpectation;
import io.openbas.rest.collector.service.CollectorService;
import io.openbas.rest.inject.form.InjectExpectationUpdateInput;
import io.openbas.service.InjectExpectationService;
import io.openbas.utils.ExpectationUtils;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
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

  @Transactional(rollbackFor = Exception.class)
  public void computeExpectations() {
    Collector collector = this.collectorService.collector(config.getId());
    List<InjectExpectation> expectations = this.injectExpectationService.expectationsNotFill();

    if (expectations.isEmpty()) {
      return;
    }

    List<InjectExpectation> updated = new ArrayList<>();
    this.processAgentExpectations(expectations, collector);
    this.processRemainingExpectations(expectations, collector, updated);

    if (updated.isEmpty()) {
      return;
    }
    this.injectExpectationService.updateAll(updated);
  }

  // -- PRIVATE --
  private void processAgentExpectations(
      @NotNull final List<InjectExpectation> expectations, @NotNull final Collector collector) {
    List<InjectExpectation> expectationAgents =
        expectations.stream().filter(ExpectationUtils::isAgentExpectation).toList();
    expectationAgents.forEach(
        expectation -> {
          if (isExpired(expectation)) {
            InjectExpectationUpdateInput input = new InjectExpectationUpdateInput();
            input.setIsSuccess(false);
            input.setResult(computeFailedMessage(expectation.getType()));
            expireEmptyResults(expectation.getResults());
            this.injectExpectationService.computeTechnicalExpectation(
                expectation, collector, input);
          }
        });
  }

  private void processRemainingExpectations(
      @NotNull final List<InjectExpectation> expectations,
      @NotNull final Collector collector,
      @NotNull final List<InjectExpectation> updated) {
    List<InjectExpectation> remainingExpectations =
        expectations.stream().filter(exp -> exp.getScore() == null).toList();
    remainingExpectations.forEach(
        expectation -> {
          if (isExpired(expectation)) {
            InjectExpectationUpdateInput input = new InjectExpectationUpdateInput();
            input.setIsSuccess(false);
            input.setResult(computeFailedMessage(expectation.getType()));
            expireEmptyResults(expectation.getResults());
            if (HUMAN_EXPECTATION.contains(expectation.getType())) {
              updated.add(
                  injectExpectationService.computeInjectExpectationForHumanResponse(
                      expectation, input, collector));
            } else {
              updated.add(
                  injectExpectationService.computeInjectExpectationForAgentOrAssetAgentless(
                      expectation, input, collector));
            }
          }
        });
  }
}
