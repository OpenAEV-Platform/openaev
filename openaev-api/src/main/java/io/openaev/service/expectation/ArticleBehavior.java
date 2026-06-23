package io.openaev.service.expectation;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildDefaultForMediaPressure;

import io.openaev.database.model.ArticleInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Behavior implementation for {@link ArticleInjectExpectation}.
 *
 * <p><strong>Dead code - not wired into any service yet.</strong>
 */
@Component
public class ArticleBehavior extends AbstractTableTopBehavior {

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof ArticleInjectExpectation;
  }

  @Override
  public void initializeResults(BaseInjectExpectation expectation) {
    if (expectation.getUser() != null) {
      expectation.setResults(List.of(buildDefaultForMediaPressure()));
    }
  }
}
