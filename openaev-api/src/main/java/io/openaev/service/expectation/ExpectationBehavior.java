package io.openaev.service.expectation;

import static io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService.EXPIRED;
import static io.openaev.service.InjectExpectationUtils.FAILED_SCORE_VALUE;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.*;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.repository.InjectExpectationResultRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.model.inject.form.Expectation;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Strategy interface for the inject expectation lifecycle.
 *
 * @param <T> the concrete expectation type this behavior builds templates for (technical or
 *     table-top)
 */
public abstract class ExpectationBehavior<T extends BaseInjectExpectation> {

  private final InjectExpectationResultRepository injectExpectationResultRepository;

  public ExpectationBehavior(InjectExpectationResultRepository injectExpectationResultRepository) {
    this.injectExpectationResultRepository = injectExpectationResultRepository;
  }

  /**
   * Returns {@code true} if this behavior handles the given expectation type.
   *
   * @param expectation the expectation to check
   */
  public abstract boolean supports(BaseInjectExpectation expectation);

  /**
   * Creates and persists inject expectations for each target from a template expectation, reusing
   * asset targets already resolved by the caller when provided.
   *
   * @param executableInject the executable inject containing targets
   * @param expectationTemplate the expectation template to apply on each target
   * @param implantType the implant/injector type used to compute signatures when relevant
   */
  public abstract void initializeAndSaveInjectExpectationsFromExecutableInject(
      ExecutableInject executableInject, T expectationTemplate, @Nullable String implantType);

  /**
   * Initialize expectation result.
   *
   * @param expectation the inject expectation to initialize
   */
  public abstract void initializeResults(BaseInjectExpectation expectation);

  /**
   * Retrieve the leaf-level expectations.
   *
   * <p>For table-top expectations, leaves are player expectations. For technical expectations,
   * leaves are agent expectations (or the endpoint if agentless).
   *
   * @param expectation the expectation whose leaves should be resolved
   * @return the list of leaf expectations to update (empty if the expectation type does not match)
   */
  public abstract List<? extends BaseInjectExpectation> getLeaves(
      BaseInjectExpectation expectation);

  /**
   * Validates that the given expectation can be answered directly by a user; throws otherwise.
   *
   * @param expectation the expectation to validate
   * @throws IllegalArgumentException if direct update is not allowed
   */
  public void throwIfCannotUpdateThisExpectation(BaseInjectExpectation expectation) {}

  /**
   * Applies a result to every leaf expectation and recomputes their score.
   *
   * @param expectation the expectation that received the update
   * @param verdict the verdict carrying the source and the new score
   * @return the list of leaf expectations that were modified
   */
  public List<? extends BaseInjectExpectation> applyResultToLeaves(
      BaseInjectExpectation expectation, ExpectationVerdict verdict) {
    if (verdict.score() == null) {
      throw new IllegalArgumentException("Expectation score cannot be null");
    }
    List<? extends BaseInjectExpectation> leaves = getLeaves(expectation);
    for (BaseInjectExpectation leaf : leaves) {
      addResultToLeaf(leaf, verdict, resolveResultLabel(leaf, verdict.score()));
      leaf.setScore(computeLeafScore(leaf));
    }
    return leaves;
  }

  /**
   * Computes the score of a leaf expectation from its results. Defaults to the highest score once
   * every source answered (see {@link
   * io.openaev.utils.inject_expectation_result.ExpectationResultBuilder#computeResultsScore}).
   *
   * @param leaf the leaf expectation whose score is computed
   * @return the score of the leaf, or {@code null} while it is still pending
   */
  public Double computeLeafScore(BaseInjectExpectation leaf) {
    return computeResultsScore(leaf.getResults(), leaf);
  }

  /**
   * Adds the verdict to a leaf expectation. Defaults to an upsert on the verdict source, keeping
   * the results of the other sources (e.g. one result per security platform).
   *
   * @param leaf the leaf expectation receiving the result
   * @param verdict the verdict carrying the source and the score
   * @param resultLabel the success/failure label of the result
   */
  public void addResultToLeaf(
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
  public List<? extends BaseInjectExpectation> deleteResultFromLeaves(
      BaseInjectExpectation expectation, String sourceId) {
    List<? extends BaseInjectExpectation> leaves = getLeaves(expectation);
    for (BaseInjectExpectation leaf : leaves) {
      List<InjectExpectationResult> removed = removeResultFromLeaf(leaf, sourceId);
      if (!removed.isEmpty()) {
        injectExpectationResultRepository.deleteAll(removed);
      }
      leaf.setScore(computeLeafScore(leaf));
    }
    // The expectation is a leaf itself: the loop above already cleaned it
    if (leaves.contains(expectation)) {
      return leaves;
    }
    // Otherwise the parent may still hold a result of its own, written by a path not yet migrated
    // to the behaviors (legacy team validation, direct or propagated security platform result on
    // an asset). Its score is recomputed from its children by recomputeParentScores.
    List<InjectExpectationResult> removed = removeResultsFromSource(expectation, sourceId);
    if (!removed.isEmpty()) {
      injectExpectationResultRepository.deleteAll(removed);
    }
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
  public List<InjectExpectationResult> removeResultFromLeaf(
      BaseInjectExpectation leaf, String sourceId) {
    return removeResultsFromSource(leaf, sourceId);
  }

  /**
   * Returns {@code true} if the expectation is a leaf: its score comes from its own results, not
   * from children expectations.
   *
   * @param expectation the expectation to check
   */
  public boolean isLeaf(BaseInjectExpectation expectation) {
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
  public List<? extends BaseInjectExpectation> expireLeaves(
      List<? extends BaseInjectExpectation> expectations, Collector collector) {
    List<? extends BaseInjectExpectation> leaves =
        expectations.stream().filter(this::isLeaf).toList();
    for (BaseInjectExpectation leaf : leaves) {
      ExpectationVerdict verdict = buildExpirationVerdict(leaf, collector);
      // The per-source rows still pending (e.g. one per security platform) get the verdict too
      expireEmptyResults(leaf.getResults(), verdict.score(), EXPIRED);
      addResultToLeaf(leaf, verdict, resolveResultLabel(leaf, verdict.score()));
      leaf.setScore(computeLeafScore(leaf));
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
  public ExpectationVerdict buildExpirationVerdict(
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
  public String resolveResultLabel(BaseInjectExpectation expectation, @NotNull double actualScore) {
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
  public List<? extends BaseInjectExpectation> recomputeParentScores(
      BaseInjectExpectation expectation) {
    return recomputeParentScores(List.of(expectation));
  }

  /**
   * Batched variant of {@link #recomputeParentScores(BaseInjectExpectation)}: the expectations are
   * grouped by inject and type, then the parents of each group are recomputed once by {@link
   * #recomputeAffectedParents}.
   *
   * @param expectations the expectations that received an update
   * @return the parent expectations that were modified
   */
  public List<? extends BaseInjectExpectation> recomputeParentScores(
      List<? extends BaseInjectExpectation> expectations) {
    Map<InjectAndType, List<T>> expectationsByInjectAndType =
        expectations.stream()
            .filter(this::supports)
            .map(expectation -> (T) expectation)
            .collect(
                Collectors.groupingBy(
                    e -> new InjectAndType(e.getInject(), e.getType()),
                    LinkedHashMap::new,
                    Collectors.toList()));
    return expectationsByInjectAndType.entrySet().stream()
        .flatMap(
            entry ->
                recomputeAffectedParents(
                    entry.getKey().inject(), entry.getKey().type(), entry.getValue())
                    .stream())
        .toList();
  }

  record InjectAndType(Inject inject, BaseInjectExpectation.EXPECTATION_TYPE type) {}

  /**
   * Recomputes only the parents affected by the given expectations, each once. The other parents of
   * the inject are left untouched.
   *
   * @param inject the inject the expectations belong to
   * @param type the type of the expectations
   * @param expectations the expectations whose parents are recomputed, all of the given inject and
   *     type
   * @return the parent expectations that were modified
   */
  public abstract List<T> recomputeAffectedParents(
      Inject inject, BaseInjectExpectation.EXPECTATION_TYPE type, List<T> expectations);

  /**
   * Returns {@code true} if this behavior can build a template from a content-form expectation of
   * the given type.
   *
   * @param type the content-form expectation type
   */
  public boolean supportsFormExpectationType(BaseInjectExpectation.EXPECTATION_TYPE type) {
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
  public boolean supportsFormExpectation(Expectation formExpectation, Inject inject) {
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
  public T convertFormExpectationToBaseInjectExpectation(
      Expectation formExpectation, Exercise exercise, Inject inject) {
    throw new UnsupportedOperationException(
        "Behavior " + getClass().getSimpleName() + " does not support form conversion");
  }
}
