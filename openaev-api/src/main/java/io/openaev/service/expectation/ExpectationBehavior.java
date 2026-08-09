package io.openaev.service.expectation;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.addResult;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.computeResultsScore;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.execution.ExecutableInject;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.rest.inject.service.AssetToExecute;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Strategy interface for the inject expectation lifecycle.
 *
 * @param <T> the concrete expectation type this behavior builds templates for (technical or
 *     table-top)
 */
public interface ExpectationBehavior<T extends BaseInjectExpectation> {

  /**
   * Returns {@code true} if this behavior handles the given expectation type.
   *
   * @param expectation the expectation to check
   */
  boolean supports(BaseInjectExpectation expectation);

  /**
   * Creates and persists inject expectations for each target from a template expectation.
   *
   * @param executableInject the executable inject containing targets
   * @param expectationTemplate the expectation template to apply on each target
   */
  void initializeAndSaveInjectExpectationsFromExecutableInject(
      ExecutableInject executableInject, T expectationTemplate, @Nullable String implantType);

  /**
   * Creates and persists inject expectations for each target from a template expectation, reusing
   * asset targets already resolved by the caller when provided.
   *
   * <p>The default implementation ignores the pre-resolved assets (table-top behaviors resolve
   * their own team/player targets); technical behaviors override it to avoid re-running the
   * expensive asset resolution once per expectation type.
   *
   * @param executableInject the executable inject containing targets
   * @param expectationTemplate the expectation template to apply on each target
   * @param preResolvedAssets asset targets already resolved by the caller, or {@code null}
   */
  default void initializeAndSaveInjectExpectationsFromExecutableInject(
      ExecutableInject executableInject,
      T expectationTemplate,
      @Nullable String implantType,
      @Nullable List<AssetToExecute> preResolvedAssets) {
    initializeAndSaveInjectExpectationsFromExecutableInject(
        executableInject, expectationTemplate, implantType);
  }

  /**
   * Initialize expectation result.
   *
   * @param expectation the inject expectation to initialize
   */
  void initializeResults(BaseInjectExpectation expectation);

  /**
   * Retrieve the leaf-level expectations.
   *
   * <p>For table-top expectations, leaves are player expectations. For technical expectations,
   * leaves are agent expectations (or the endpoint if agentless).
   *
   * @param expectation the expectation whose leaves should be resolved
   * @return the list of leaf expectations to update (empty if the expectation type does not match)
   */
  List<? extends BaseInjectExpectation> getLeaves(BaseInjectExpectation expectation);

  /**
   * Validates that the given expectation can be updated directly; throws otherwise.
   *
   * @param expectation the expectation to validate
   * @throws IllegalArgumentException if direct update is not allowed
   */
  default void throwIfCannotUpdateThisExpectation(BaseInjectExpectation expectation) {}

  /**
   * Applies a result to every leaf expectation and recomputes their score.
   *
   * @param expectation the expectation that received the update
   * @param input the update input containing the new score
   * @return the list of leaf expectations that were modified
   */
  default List<? extends BaseInjectExpectation> applyResultToLeaves(
      BaseInjectExpectation expectation, ExpectationUpdateInput input) {
    if (input.getScore() == null) {
      throw new IllegalArgumentException("Expectation score cannot be null");
    }
    throwIfCannotUpdateThisExpectation(expectation);
    List<? extends BaseInjectExpectation> leaves = getLeaves(expectation);
    for (BaseInjectExpectation leaf : leaves) {
      addResult(leaf, input, resolveResultLabel(leaf, input.getScore()));
      leaf.setScore(computeResultsScore(leaf.getResults()));
    }
    return leaves;
  }

  /**
   * Returns the success/failure label based on whether the actual score meets the expected score.
   *
   * @param expectation the expectation providing the threshold and labels
   * @param actualScore the score to evaluate
   * @return {@link BaseInjectExpectation#getSuccessLabel()} or {@link
   *     BaseInjectExpectation#getFailureLabel()}
   */
  default String resolveResultLabel(
      BaseInjectExpectation expectation, @NotNull double actualScore) {
    return actualScore >= expectation.getExpectedScore()
        ? expectation.getSuccessLabel()
        : expectation.getFailureLabel();
  }

  /**
   * Recomputes scores of parent expectations from their children after a leaf-level update.
   *
   * @param expectation the expectation that received the update
   * @return the list of parent expectations that were modified
   */
  List<? extends BaseInjectExpectation> recomputeParentScores(BaseInjectExpectation expectation);
}
