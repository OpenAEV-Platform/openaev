package io.openaev.service.expectation;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildDefaultForMediaPressure;

import io.openaev.database.model.ArticleInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.repository.InjectExpectationRepository;
import org.springframework.stereotype.Component;

/** Behavior implementation for {@link ArticleInjectExpectation}. */
@Component
public class ArticleBehavior extends AbstractTableTopBehavior {

  public ArticleBehavior(InjectExpectationRepository injectExpectationRepository) {
    super(injectExpectationRepository);
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof ArticleInjectExpectation;
  }

  @Override
  protected InjectExpectationResult buildDefaultPlayerResult() {
    return buildDefaultForMediaPressure();
  }
}
