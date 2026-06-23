package io.openaev.service.expectation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ChallengeInjectExpectation;
import org.springframework.stereotype.Component;

/**
 * Behavior implementation for {@link ChallengeInjectExpectation}.
 *
 * <p><strong>Dead code - not wired into any service yet.</strong>
 */
@Component
public class ChallengeBehavior extends AbstractTableTopBehavior {

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof ChallengeInjectExpectation;
  }

  @Override
  public void initializeResults(BaseInjectExpectation expectation) {
    // Intentionally left blank to match legacy behavior (UI limitation for default challenge
    // result).
  }
}
