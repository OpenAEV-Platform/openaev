package io.openaev.service.expectation;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildDefaultForPlayerManualValidation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ManualInjectExpectation;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Behavior implementation for {@link ManualInjectExpectation}.
 *
 * <p><strong>Dead code - not wired into any service yet.</strong>
 */
@Component
public class ManualBehavior extends AbstractTableTopBehavior {

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof ManualInjectExpectation;
  }

  @Override
  public void initializeResults(BaseInjectExpectation expectation) {
    if (expectation.getUser() != null) {
      expectation.setResults(List.of(buildDefaultForPlayerManualValidation()));
    }
  }
}
