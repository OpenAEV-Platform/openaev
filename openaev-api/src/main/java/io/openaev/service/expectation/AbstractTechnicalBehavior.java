package io.openaev.service.expectation;

import static io.openaev.service.InjectExpectationUtils.computeScores;
import static io.openaev.utils.AgentUtils.getPrimaryAgents;
import static io.openaev.utils.ExpectationUtils.*;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.*;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.hibernate.Hibernate;

public abstract class AbstractTechnicalBehavior implements ExpectationBehavior {

  protected final CollectorService collectorService;

  protected AbstractTechnicalBehavior(CollectorService collectorService) {
    this.collectorService = collectorService;
  }

  @Override
  public void applyResultToLeaves(BaseInjectExpectation expectation, ExpectationUpdateInput input) {
    if (isAssetGroupExpectation(expectation)) {
      throw new IllegalArgumentException("Not possible to update Asset Group directly");
    }

    boolean isAgentless = isAgentless(expectation);
    if (isAssetExpectation(expectation) && !isAgentless) {
      List<BaseInjectExpectation> expectationsForAgents =
          getExpectationsAgentsForAsset(expectation);
      expectationsForAgents.forEach(
          e -> computeInjectExpectationForAgentOrAssetAgentless(e, input));
      return;
    }

    computeInjectExpectationForAgentOrAssetAgentless(expectation, input);
  }

  @Override
  public void initializeResults(BaseInjectExpectation expectation) {
    if (expectation.getAgent() != null) {
      expectation.setResults(setUpFromCollectors(collectorService.securityPlatformCollectors()));
    }
  }

  @Override
  public List<BaseInjectExpectation> propagate(BaseInjectExpectation expectation) {
    return propagateTechnicalExpectation(expectation, isAgentless(expectation), null);
  }

  protected void computeInjectExpectationForAgentOrAssetAgentless(
      BaseInjectExpectation expectation, ExpectationUpdateInput input) {
    String result =
        ExpectationType.label(
            expectation.getType(), expectation.getExpectedScore(), input.getScore());
    addResult(expectation, input, result);
    Double score = computeScore(expectation.getResults(), expectation);
    expectation.setScore(score);
  }

  protected List<BaseInjectExpectation> propagateTechnicalExpectation(
      BaseInjectExpectation expectation,
      boolean isAgentless,
      Function<Double, InjectExpectationResult> addResult) {
    List<BaseInjectExpectation> expectations = new ArrayList<>();

    if (!isAgentless) {
      expectations.addAll(propagateToAsset(expectation, addResult));
    }
    expectations.addAll(propagateToAssetGroup(expectation, addResult));

    return expectations;
  }

  protected List<BaseInjectExpectation> propagateToAsset(
      BaseInjectExpectation expectation, Function<Double, InjectExpectationResult> addResult) {
    List<BaseInjectExpectation> expectationsForAgents = getExpectationsAgentsForAsset(expectation);
    List<BaseInjectExpectation> expectationsForAssets = getExpectationsAssets(expectation);
    computeScores(expectationsForAgents, expectationsForAssets, expectation, addResult);
    return expectationsForAssets;
  }

  protected List<BaseInjectExpectation> propagateToAssetGroup(
      BaseInjectExpectation expectation, Function<Double, InjectExpectationResult> addResult) {
    if (expectation.getAssetGroup() == null) {
      return new ArrayList<>();
    }

    List<BaseInjectExpectation> expectationsForAssets =
        getExpectationsAssetsForAssetGroup(expectation);
    List<BaseInjectExpectation> expectationForAssetGroups = getExpectationAssetGroups(expectation);
    computeScores(expectationsForAssets, expectationForAssetGroups, expectation, addResult);
    return expectationForAssetGroups;
  }

  protected boolean isAgentless(BaseInjectExpectation expectation) {
    Endpoint endpoint = (Endpoint) Hibernate.unproxy(expectation.getAsset());
    return getPrimaryAgents(endpoint).isEmpty();
  }
}
