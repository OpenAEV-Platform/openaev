package io.openaev.service.expectation;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildDefaultForPlayerManualValidation;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectExpectationResultRepository;
import io.openaev.model.inject.form.Expectation;
import io.openaev.service.InjectExpectationUtils;
import org.springframework.stereotype.Component;

/** Behavior implementation for {@link ManualInjectExpectation}. */
@Component
public class ManualBehavior extends AbstractTableTopBehavior {

  public ManualBehavior(
      InjectExpectationRepository injectExpectationRepository,
      InjectExpectationResultRepository injectExpectationResultRepository) {
    super(injectExpectationRepository, injectExpectationResultRepository);
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof ManualInjectExpectation;
  }

  @Override
  public boolean supportsFormExpectationType(BaseInjectExpectation.EXPECTATION_TYPE type) {
    return type == BaseInjectExpectation.EXPECTATION_TYPE.MANUAL;
  }

  @Override
  protected InjectExpectationResult buildDefaultPlayerResult(Double expectedScore) {
    return buildDefaultForPlayerManualValidation();
  }

  @Override
  public ManualInjectExpectation convertFormExpectationToBaseInjectExpectation(
      Expectation formExpectation, Exercise exercise, Inject inject) {
    ManualInjectExpectation manualExpectation = new ManualInjectExpectation();
    InjectExpectationUtils.setCommonFields(
        manualExpectation, formExpectation, exercise, inject, this.expectationPropertiesConfig);
    return manualExpectation;
  }
}
