package io.openaev.service.expectation;

import static io.openaev.service.InjectExpectationUtils.*;
import static io.openaev.utils.AgentUtils.getActiveAgents;
import static io.openaev.utils.ExpectationSignatureUtils.convertToInjectExpectationSignatures;
import static io.openaev.utils.ExpectationUtils.*;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.setUpFromCollectors;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.expectation.ExpectationSignature;
import io.openaev.model.inject.form.Expectation;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.utils.ExpectationUtils;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/** Shared behavior for technical expectations (detection/prevention/vulnerability). */
@RequiredArgsConstructor
public abstract class AbstractTechnicalBehavior
    implements ExpectationBehavior<TechnicalInjectExpectation> {

  @Resource protected ExpectationPropertiesConfig expectationPropertiesConfig;

  protected final CollectorService collectorService;
  protected final InjectService injectService;
  protected final InjectExpectationRepository injectExpectationRepository;

  /** Builds the concrete technical expectation instance handled by this behavior. */
  protected abstract TechnicalInjectExpectation newTechnicalExpectation();

  /** {@inheritDoc} Builds an untargeted technical template carrying the expected platform types. */
  @Override
  public TechnicalInjectExpectation convertFormExpectationToBaseInjectExpectation(
      Expectation formExpectation, Exercise exercise, Inject inject) {
    TechnicalInjectExpectation expectation = newTechnicalExpectation();
    setCommonFields(
        expectation, formExpectation, exercise, inject, this.expectationPropertiesConfig);
    expectation.setExpectedSecurityPlatforms(formExpectation.getExpectedSecurityPlatformTypes());
    return expectation;
  }

  // -- INITIALIZE AND SAVE --

  /**
   * Creates and persists expectations for each asset target (agent → asset → asset-group). Results
   * and signatures are set on leaf expectations only (agent, or asset if agentless).
   *
   * <p>/!\ Does not support Caldera.
   */
  @Override
  public void initializeAndSaveInjectExpectationsFromExecutableInject(
      ExecutableInject executableInject,
      TechnicalInjectExpectation expectationTemplate,
      @Nullable String implantType) {

    if (!shouldInitializeExpectation(executableInject)) {
      return;
    }

    Inject inject = executableInject.getInjection().getInject();
    // Detection / prevention expectations can only ever be fulfilled by a security platform
    // collector: with none able to answer this template's expected platforms, nothing would fill
    // the expectation, so we create none (neither leaves nor parents). Vulnerability expectations
    // are fulfilled by the assessment injector itself (e.g. Nuclei), not a collector, so they are
    // always created regardless (see requiresCollectorToInitialize).
    List<Collector> collectors = resolveCollectors(inject.getTenant().getId(), expectationTemplate);
    if (requiresCollectorToInitialize() && collectors.isEmpty()) {
      return;
    }

    List<TechnicalInjectExpectation> allExpectations = new ArrayList<>();

    // Executors pre-cache the resolved assets; direct callers (e.g. atomic testing, chaining)
    // may not, so fall back to resolving them from the inject.
    List<AssetToExecute> assetsToExecute = executableInject.getAssetsToExecute();
    if (assetsToExecute == null) {
      assetsToExecute = injectService.resolveAllAssetsToExecute(inject);
    }

    assetsToExecute.forEach(
        assetToExecute -> {
          List<Agent> activeAgents = getActiveAgents(assetToExecute.asset(), inject);

          if (activeAgents.isEmpty()
              && !isAgentlessAssetExpectationNecessary(assetToExecute.asset(), inject)) {
            return;
          }

          if (assetToExecute.isDirectlyLinkedToInject()) {
            activeAgents.forEach(
                agent -> {
                  allExpectations.add(
                      buildExpectationForTarget(
                          expectationTemplate, null, assetToExecute.asset(), agent));
                });
            allExpectations.add(
                buildExpectationForTarget(expectationTemplate, null, assetToExecute.asset(), null));
          }

          assetToExecute
              .assetGroups()
              .forEach(
                  assetGroup -> {
                    activeAgents.forEach(
                        agent -> {
                          allExpectations.add(
                              buildExpectationForTarget(
                                  expectationTemplate, assetGroup, assetToExecute.asset(), agent));
                        });
                    allExpectations.add(
                        buildExpectationForTarget(
                            expectationTemplate, assetGroup, assetToExecute.asset(), null));
                    allExpectations.add(
                        buildExpectationForTarget(expectationTemplate, assetGroup, null, null));
                  });
        });

    allExpectations.stream()
        .filter(e -> !isAssetGroupExpectation(e))
        .filter(
            e ->
                isAgentExpectation(e) || isAgentlessAssetExpectationNecessary(e.getAsset(), inject))
        .forEach(
            e -> {
              initializeResults(e, collectors);
              String agentId = e.getAgent() != null ? e.getAgent().getId() : null;
              List<ExpectationSignature> expectationSignatures =
                  computeSignatures(
                      implantType,
                      inject.getId(),
                      e.getAsset(),
                      agentId,
                      injectService.getValueTargetedAssetMap(inject));
              e.setSignatures(convertToInjectExpectationSignatures(expectationSignatures, e));
            });
    injectExpectationRepository.saveAll(allExpectations);
  }

  private static TechnicalInjectExpectation buildExpectationForTarget(
      TechnicalInjectExpectation template,
      @Nullable AssetGroup assetGroup,
      Asset asset,
      @Nullable Agent agent) {
    TechnicalInjectExpectation expectation = template.clone();
    expectation.setAssetGroup(assetGroup);
    expectation.setAsset(asset);
    expectation.setAgent(agent);
    return expectation;
  }

  // --------- INITIALIZE RESULTS

  /** {@inheritDoc} Sets default results from collectors on leaf expectations. */
  @Override
  public void initializeResults(BaseInjectExpectation expectation) {
    initializeResults(
        expectation,
        collectorService.securityPlatformCollectors(expectation.getInject().getTenant().getId()));
  }

  /** Batch-friendly variant reusing tenant collectors already loaded by the caller. */
  protected void initializeResults(BaseInjectExpectation expectation, List<Collector> collectors) {
    List<InjectExpectationResult> defaults = buildDefaultResults(expectation, collectors);
    if (!defaults.isEmpty()) {
      expectation.setResults(defaults);
    }
  }

  private List<Collector> resolveCollectors(
      String tenantId, TechnicalInjectExpectation expectation) {
    List<Collector> tenantCollectors = collectorService.securityPlatformCollectors(tenantId);
    return filterCollectorsForExpectation(
        tenantCollectors, expectation.getExpectedSecurityPlatforms());
  }

  /**
   * Whether a security platform collector is required for this behavior to create expectations.
   * Detection / prevention are collector-fulfilled, so with no matching collector no expectation is
   * created. Vulnerability expectations are fulfilled by the assessment injector itself and
   * override this to {@code false}.
   */
  protected boolean requiresCollectorToInitialize() {
    return true;
  }

  /**
   * Provides the default result entries for leaf expectations: pending rows restricted to the
   * collectors matching the expectation's expected security platform types (empty/null = every
   * connected security platform), with the expiration floor guaranteeing that the real collectors
   * get to answer before the expiration manager - the same rules the legacy creation path applied.
   */
  protected List<InjectExpectationResult> buildDefaultResults(
      BaseInjectExpectation expectation, List<Collector> collectors) {
    if (!(expectation instanceof TechnicalInjectExpectation tech)) {
      return List.of();
    }
    applyExpirationOrderingGuarantee(tech, collectors);
    return setUpFromCollectors(collectors);
  }

  // ----- END INITIALIZE

  /** {@inheritDoc} Rejects update on asset-group expectations level. */
  @Override
  public void throwIfCannotUpdateThisExpectation(BaseInjectExpectation expectation) {
    if (!(expectation instanceof TechnicalInjectExpectation tech)) {
      throw new IllegalArgumentException(
          "Cannot update expectation of type " + expectation.getClass().getSimpleName());
    }
    if (isAssetGroupExpectation(tech)) {
      throw new IllegalArgumentException("Not possible to update Asset Group directly");
    }
  }

  /** {@inheritDoc} Resolves to agent expectations, or the asset itself if agentless. */
  @Override
  public List<? extends BaseInjectExpectation> getLeaves(BaseInjectExpectation expectation) {
    if (!(expectation instanceof TechnicalInjectExpectation tech)) {
      return List.of();
    }
    if (isAgentExpectation(tech)) {
      return List.of(tech);
    }
    if (isAssetGroupExpectation(tech)) {
      return getAssetsExpectationsOfAssetGroup(tech).stream()
          .flatMap(asset -> getLeafExpectationsForAsset(asset).stream())
          .toList();
    }
    if (isAssetExpectation(tech)) {
      return getLeafExpectationsForAsset(tech);
    }
    return List.of();
  }

  /** Returns agent expectations for the asset, or the asset itself if agentless. */
  private List<TechnicalInjectExpectation> getLeafExpectationsForAsset(
      TechnicalInjectExpectation assetExpectation) {
    List<TechnicalInjectExpectation> agentExpectations =
        getAgentsExpectationsForAsset(assetExpectation);
    return agentExpectations.isEmpty() ? List.of(assetExpectation) : agentExpectations;
  }

  // -- RECOMPUTE PARENT SCORE --

  /** {@inheritDoc} Recomputes asset-level then asset-group-level scores from their children. */
  @Override
  public List<? extends BaseInjectExpectation> recomputeParentScores(
      BaseInjectExpectation expectation) {
    if (!(expectation instanceof TechnicalInjectExpectation tech)) {
      return List.of();
    }
    Inject inject = tech.getInject();
    BaseInjectExpectation.EXPECTATION_TYPE type = tech.getType();

    List<TechnicalInjectExpectation> sameType =
        inject.getExpectations().stream()
            .filter(TechnicalInjectExpectation.class::isInstance)
            .map(TechnicalInjectExpectation.class::cast)
            .filter(e -> type.equals(e.getType()))
            .toList();

    Map<String, List<TechnicalInjectExpectation>> agentsByAssetId =
        sameType.stream()
            .filter(ExpectationUtils::isAgentExpectation)
            .filter(e -> e.getAsset() != null)
            .collect(Collectors.groupingBy(e -> e.getAsset().getId()));

    Map<String, List<TechnicalInjectExpectation>> assetsByAssetGroupId =
        sameType.stream()
            .filter(ExpectationUtils::isAssetExpectation)
            .filter(e -> e.getAssetGroup() != null)
            .collect(Collectors.groupingBy(e -> e.getAssetGroup().getId()));

    List<TechnicalInjectExpectation> updatedParents = new ArrayList<>();
    sameType.stream()
        .filter(ExpectationUtils::isAssetExpectation)
        .forEach(
            asset ->
                recomputeParent(
                        asset, agentsByAssetId.getOrDefault(asset.getAsset().getId(), List.of()))
                    .ifPresent(updatedParents::add));
    sameType.stream()
        .filter(ExpectationUtils::isAssetGroupExpectation)
        .forEach(
            group ->
                recomputeParent(
                        group,
                        assetsByAssetGroupId.getOrDefault(group.getAssetGroup().getId(), List.of()))
                    .ifPresent(updatedParents::add));
    return updatedParents;
  }

  private Optional<TechnicalInjectExpectation> recomputeParent(
      TechnicalInjectExpectation parent, List<TechnicalInjectExpectation> children) {
    if (children.isEmpty()) {
      return Optional.empty();
    }
    Double score =
        computeChildrenScore(parent.isExpectationGroup(), parent.getExpectedScore(), children);
    // A definitive direct VULNERABLE verdict written on the parent row (e.g. by an assessment
    // injector such as Nuclei) must survive the children rollup.
    parent.setScore(reconcileWithDirectVulnerableVerdict(parent, score));
    return Optional.of(parent);
  }

  // -- END RECOMPUTE PARENT SCORE

}
