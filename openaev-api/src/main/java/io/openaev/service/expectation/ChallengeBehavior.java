package io.openaev.service.expectation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ChallengeInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.utils.challenge.ChallengeExpectationUtils;
import org.springframework.stereotype.Component;

/** Behavior implementation for {@link ChallengeInjectExpectation}. */
@Component
public class ChallengeBehavior extends AbstractTableTopBehavior {

  public ChallengeBehavior(InjectExpectationRepository injectExpectationRepository) {
    super(injectExpectationRepository);
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof ChallengeInjectExpectation;
  }

  // TODO /!\ /!\ : The UI needs to be fixed: when the score and result are initialized to
  //  null, the user can no longer validate the flag.
  @Override
  protected InjectExpectationResult buildDefaultPlayerResult(Double expectedScore) {
    return ChallengeExpectationUtils.buildDefaultChallengeInjectExpectationResult();
  }
}
