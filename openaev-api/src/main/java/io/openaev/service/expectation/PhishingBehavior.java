package io.openaev.service.expectation;

import static io.openaev.injectors.phishing.service.PhishingTrackingService.NO_INTERACTION_MESSAGE;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForPlayerManualValidation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.ManualInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.injectors.phishing.PhishingContract;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Specialized manual behavior for phishing injects.
 *
 * <p>Phishing uses MANUAL expectations but initializes them as "resisted" at send time.
 */
@Component
@Order(0)
public class PhishingBehavior extends ManualBehavior {

  public PhishingBehavior(InjectExpectationRepository injectExpectationRepository) {
    super(injectExpectationRepository);
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    if (!(expectation instanceof ManualInjectExpectation)) {
      return false;
    }
    return expectation.getInject() != null
        && PhishingContract.TYPE.equals(expectation.getInject().getType());
  }

  @Override
  public boolean supportsFormExpectation(
      io.openaev.model.inject.form.Expectation formExpectation,
      io.openaev.database.model.Inject inject) {
    return formExpectation.getType() == BaseInjectExpectation.EXPECTATION_TYPE.MANUAL
        && inject != null
        && PhishingContract.TYPE.equals(inject.getType());
  }

  @Override
  protected InjectExpectationResult buildDefaultPlayerResult(Double expectedScore) {
    return buildForPlayerManualValidation(NO_INTERACTION_MESSAGE, expectedScore);
  }
}
