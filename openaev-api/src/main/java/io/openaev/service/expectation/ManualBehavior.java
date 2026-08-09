package io.openaev.service.expectation;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildDefaultForPlayerManualValidation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.ManualInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import org.springframework.stereotype.Component;

/** Behavior implementation for {@link ManualInjectExpectation}. */
@Component
public class ManualBehavior extends AbstractTableTopBehavior {

  public ManualBehavior(InjectExpectationRepository injectExpectationRepository) {
    super(injectExpectationRepository);
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof ManualInjectExpectation;
  }

  @Override
  protected InjectExpectationResult buildDefaultPlayerResult() {
    return buildDefaultForPlayerManualValidation();
  }
}
