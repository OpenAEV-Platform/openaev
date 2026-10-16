package io.openaev.service.expectation;

import static io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService.EXPIRED;
import static io.openaev.service.InjectExpectationUtils.FAILED_SCORE_VALUE;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.addResult;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.computeResultsScore;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.expireEmptyResults;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.removeResult;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.execution.ExecutableInject;
import io.openaev.model.inject.form.Expectation;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
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
   * Creates and persists inject expectations for each target from a template expectation, reusing
   * asset targets already resolved by the caller when provided.
   *
   * @param executableInject the executable inject containing targets
   * @param expectationTemplate the expectation template to apply on each target
   * @param implantType the implant/injector type used to compute signatures when relevant
   */
  void initializeAndSaveInjectExpectationsFromExecutableInject(
      ExecutableInject executableInject, T expectationTemplate, @Nullable String implantType);

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
   * @param verdict the verdict carrying the source and the new score
   * @return the list of leaf expectations that were modified
   */
  default List<? extends BaseInjectExpectation> applyResultToLeaves(
      BaseInjectExpectation expectation, ExpectationVerdict verdict) {
    if (verdict.score() == null) {
      throw new IllegalArgumentException("Expectation score cannot be null");
    }
    throwIfCannotUpdateThisExpectation(expectation);
    List<? extends BaseInjectExpectation> leaves = getLeaves(expectation);
    for (BaseInjectExpectation leaf : leaves) {
      addResultToLeaf(leaf, verdict, resolveResultLabel(leaf, verdict.score()));
      leaf.setScore(computeResultsScore(leaf.getResults(), leaf));
    }
    return leaves;
  }

  /**
   * Adds the verdict to a leaf expectation. Defaults to an upsert on the verdict source, keeping
   * the results of the other sources (e.g. one result per security platform).
   *
   * @param leaf the leaf expectation receiving the result
   * @param verdict the verdict carrying the source and the score
   * @param resultLabel the success/failure label of the result
   */
  default void addResultToLeaf(
      BaseInjectExpectation leaf, ExpectationVerdict verdict, String resultLabel) {
    addResult(leaf, verdict, resultLabel);
  }

  /**
   * Removes the result of a source from every leaf expectation.
   *
   * @param expectation the expectation whose result is deleted
   * @param sourceId the ID of the source whose result is removed
   * @return the list of expectations that were modified (the leaves, and the expectation itself
   *     when it is a parent)
   */
  default List<? extends BaseInjectExpectation> deleteResultFromLeaves(
      BaseInjectExpectation expectation, String sourceId) {
    List<? extends BaseInjectExpectation> leaves = getLeaves(expectation);
    for (BaseInjectExpectation leaf : leaves) {
      removeResultFromLeaf(leaf, sourceId);
      leaf.setScore(computeResultsScore(leaf.getResults(), leaf));
    }
    // The expectation is a leaf itself: the loop above already cleaned it
    if (leaves.contains(expectation)) {
      return leaves;
    }
    // Otherwise the parent may still hold a result of its own, written by a path not yet migrated
    // to the behaviors (legacy team validation, direct or propagated security platform result on
    // an asset). Its score is recomputed from its children by recomputeParentScores.
    removeResult(expectation, sourceId);
    List<BaseInjectExpectation> updated = new ArrayList<>(leaves);
    updated.add(expectation);
    return updated;
  }

  /**
   * Removes the result of a source from a leaf expectation. Defaults to removing that source only,
   * keeping the results of the other sources (e.g. one result per security platform).
   *
   * @param leaf the leaf expectation losing the result
   * @param sourceId the ID of the source whose result is removed
   */
  default void removeResultFromLeaf(BaseInjectExpectation leaf, String sourceId) {
    removeResult(leaf, sourceId);
  }

  /**
   * Returns {@code true} if the expectation is a leaf: its score comes from its own results, not
   * from children expectations.
   *
   * @param expectation the expectation to check
   */
  default boolean isLeaf(BaseInjectExpectation expectation) {
    return getLeaves(expectation).contains(expectation);
  }

  /**
   * Expires unanswered expectations with the verdict of the expiration collector. Only the leaves
   * receive the verdict: a parent (team with players, asset with agents, asset group) is derived
   * from its children by {@link #recomputeParentScores}, so it stays pending until they expire.
   *
   * @param expectations the expired expectations
   * @param collector the collector the verdict is attributed to
   * @return the leaf expectations that received the verdict
   */
  default List<? extends BaseInjectExpectation> expireLeaves(
      List<? extends BaseInjectExpectation> expectations, Collector collector) {
    List<? extends BaseInjectExpectation> leaves =
        expectations.stream().filter(this::isLeaf).toList();
    for (BaseInjectExpectation leaf : leaves) {
      ExpectationVerdict verdict = buildExpirationVerdict(leaf, collector);
      // The per-source rows still pending (e.g. one per security platform) get the verdict too
      expireEmptyResults(leaf.getResults(), verdict.score(), EXPIRED);
      addResultToLeaf(leaf, verdict, resolveResultLabel(leaf, verdict.score()));
      leaf.setScore(computeResultsScore(leaf.getResults(), leaf));
    }
    return leaves;
  }

  /**
   * Builds the expiration verdict of an unanswered leaf. Defaults to a failure: silence means
   * nothing was prevented, detected or validated within the window.
   *
   * @param leaf the expired, unanswered leaf expectation
   * @param collector the collector the verdict is attributed to
   * @return the expiration verdict
   */
  default ExpectationVerdict buildExpirationVerdict(
      BaseInjectExpectation leaf, Collector collector) {
    return ExpectationVerdict.fromCollector(collector, FAILED_SCORE_VALUE);
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

  /**
   * Batched variant of {@link #recomputeParentScores(BaseInjectExpectation)}: recomputes the
   * parents of every given expectation once.
   *
   * @param expectations the expectations that received an update
   * @return the distinct parent expectations that were modified
   */
  default List<? extends BaseInjectExpectation> recomputeParentScores(
      List<? extends BaseInjectExpectation> expectations) {
    return expectations.stream()
        .flatMap(expectation -> recomputeParentScores(expectation).stream())
        .distinct()
        .toList();
  }

  /**
   * Returns {@code true} if this behavior can build a template from a content-form expectation of
   * the given type.
   *
   * @param type the content-form expectation type
   */
  default boolean supportsFormExpectationType(BaseInjectExpectation.EXPECTATION_TYPE type) {
    return false;
  }

  /**
   * Returns {@code true} if this behavior handles the given content-form expectation for the given
   * inject. Defaults to a pure type match; inject-aware behaviors (e.g. phishing) override this to
   * discriminate on the inject type for a shared expectation type.
   *
   * @param formExpectation the content-form expectation
   * @param inject the inject the expectation is attached to
   */
  default boolean supportsFormExpectation(Expectation formExpectation, Inject inject) {
    return supportsFormExpectationType(formExpectation.getType());
  }

  /**
   * Converts a content-form expectation into a single, untargeted expectation template.
   *
   * <p>Target and context multiplication (teams/users, assets/agents, challenges/articles) is
   * deferred to {@link #initializeAndSaveInjectExpectationsFromExecutableInject}, so each behavior
   * expands the template into as many concrete expectations as its type requires.
   *
   * @param formExpectation the raw expectation declared in the inject content
   * @param exercise the exercise the inject belongs to
   * @param inject the inject the expectation is attached to
   * @return an untargeted expectation template
   */
  default T convertFormExpectationToBaseInjectExpectation(
      Expectation formExpectation, Exercise exercise, Inject inject) {
    throw new UnsupportedOperationException(
        "Behavior " + getClass().getSimpleName() + " does not support form conversion");
  }
}
