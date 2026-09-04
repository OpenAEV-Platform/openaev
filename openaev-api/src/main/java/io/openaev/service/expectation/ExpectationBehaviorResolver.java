package io.openaev.service.expectation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Inject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link ExpectationBehavior} handling a given expectation.
 *
 * <p>Behaviors are ordered by their Spring {@code @Order}, so specialized behaviors (e.g. phishing)
 * are evaluated before their generic parent.
 */
@Component
@RequiredArgsConstructor
public class ExpectationBehaviorResolver {

  private final List<ExpectationBehavior<?>> behaviors;

  /**
   * Resolves the behavior handling the given persisted expectation. The unchecked cast is safe: the
   * behavior is selected by its own {@link ExpectationBehavior#supports(BaseInjectExpectation)}, so
   * its type parameter always matches the runtime type of the expectation passed back to it.
   *
   * @param expectation the expectation to handle
   */
  @SuppressWarnings("unchecked")
  public ExpectationBehavior<BaseInjectExpectation> resolveFor(BaseInjectExpectation expectation) {
    return (ExpectationBehavior<BaseInjectExpectation>)
        behaviors.stream()
            .filter(b -> b.supports(expectation))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No behavior found for " + expectation.getClass().getSimpleName()));
  }

  /**
   * Resolves the behavior able to build and initialize an expectation from a content-form
   * expectation. Resolution is inject-aware so specialized behaviors (e.g. phishing) can win over
   * their generic parent for the same expectation type. The unchecked cast is safe: the same
   * behavior both builds the template and consumes it back.
   *
   * @param formExpectation the content-form expectation
   * @param inject the inject the expectation is attached to
   */
  @SuppressWarnings("unchecked")
  public ExpectationBehavior<BaseInjectExpectation> resolveForForm(
      io.openaev.model.inject.form.Expectation formExpectation, Inject inject) {
    return (ExpectationBehavior<BaseInjectExpectation>)
        behaviors.stream()
            .filter(b -> b.supportsFormExpectation(formExpectation, inject))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No behavior found for expectation type " + formExpectation.getType()));
  }
}
