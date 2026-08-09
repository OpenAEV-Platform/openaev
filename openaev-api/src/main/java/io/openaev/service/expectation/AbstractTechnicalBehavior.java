package io.openaev.service.expectation;

import static io.openaev.service.InjectExpectationUtils.applyExpirationOrderingGuarantee;
import static io.openaev.service.InjectExpectationUtils.computeChildrenScore;
import static io.openaev.service.InjectExpectationUtils.filterCollectorsForExpectation;
import static io.openaev.service.InjectExpectationUtils.reconcileWithDirectVulnerableVerdict;
import static io.openaev.utils.AgentUtils.getActiveAgents;
import static io.openaev.utils.ExpectationSignatureUtils.convertToInjectExpectationSignatures;
import static io.openaev.utils.ExpectationUtils.*;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.setUpFromCollectors;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.ExpectationSignature;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.utils.ExpectationUtils;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;

/** Shared behavior for technical expectations (detection/prevention/vulnerability). */
@RequiredArgsConstructor
public abstract class AbstractTechnicalBehavior
    implements ExpectationBehavior<TechnicalInjectExpectation> {

  protected final CollectorService collectorService;
  protected final InjectService injectService;
  protected final InjectExpectationRepository injectExpectationRepository;

  // -- INITIALIZE AND SAVE --

  /** {@inheritDoc} Resolves asset targets itself before delegating. */
  @Override
  public void initializeAndSaveInjectExpectationsFromExecutableInject(
      ExecutableInject executableInject,
      TechnicalInjectExpectation expectationTemplate,
      String implantType) {
    initializeAndSaveInjectExpectationsFromExecutableInject(
        executableInject, expectationTemplate, implantType, null);
  }

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
      @Nullable String implantType,
      @Nullable List<AssetToExecute> preResolvedAssets) {
    Inject inject = executableInject.getInjection().getInject();
    List<AssetToExecute> assetToExecutes =
        preResolvedAssets != null
            ? preResolvedAssets
            : this.injectService.resolveAllAssetsToExecute(inject);

    List<TechnicalInjectExpectation> allExpectations = new ArrayList<>();

    assetToExecutes.forEach(
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

    List<TechnicalInjectExpectation> leafExpectations =
        allExpectations.stream()
            .filter(e -> !isAssetGroupExpectation(e))
            .filter(
                e ->
                    isAgentExpectation(e)
                        || isAgentlessAssetExpectationNecessary(e.getAsset(), inject))
            .toList();
    if (!leafExpectations.isEmpty()) {
      // Fetched once per invocation (not per leaf), and only when a leaf actually needs them.
      List<Collector> tenantCollectors =
          collectorService.securityPlatformCollectors(inject.getTenant().getId());
      Map<String, Endpoint> valueTargetedAssetsMap = injectService.getValueTargetedAssetMap(inject);
      leafExpectations.forEach(
          e -> {
            initializeResults(e, tenantCollectors);
            String agentId = e.getAgent() != null ? e.getAgent().getId() : null;
            List<ExpectationSignature> expectationSignatures =
                computeSignatures(
                    implantType, inject.getId(), e.getAsset(), agentId, valueTargetedAssetsMap);
            e.setSignatures(convertToInjectExpectationSignatures(expectationSignatures, e));
          });
    }
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
  protected void initializeResults(
      BaseInjectExpectation expectation, List<Collector> tenantCollectors) {
    List<InjectExpectationResult> defaults = buildDefaultResults(expectation, tenantCollectors);
    if (!defaults.isEmpty()) {
      expectation.setResults(defaults);
    }
  }

  /**
   * Provides the default result entries for leaf expectations: pending rows restricted to the
   * collectors matching the expectation's expected security platform types (empty/null = every
   * connected security platform), with the expiration floor guaranteeing that the real collectors
   * get to answer before the expiration manager - the same rules the legacy creation path applied.
   */
  protected List<InjectExpectationResult> buildDefaultResults(
      BaseInjectExpectation expectation, List<Collector> tenantCollectors) {
    if (!(expectation instanceof TechnicalInjectExpectation tech)) {
      return List.of();
    }
    List<Collector> expectedCollectors =
        filterCollectorsForExpectation(tenantCollectors, tech.getExpectedSecurityPlatforms());
    applyExpirationOrderingGuarantee(tech, expectedCollectors);
    return setUpFromCollectors(expectedCollectors);
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
    Inject inject = expectation.getInject();
    BaseInjectExpectation.EXPECTATION_TYPE type = expectation.getType();

    List<TechnicalInjectExpectation> updatedParents = new ArrayList<>();
    updatedParents.addAll(
        recomputeLevel(
            getAssetsExpectationsByInjectAndType(inject, type),
            ExpectationUtils::getAgentsExpectationsForAsset));
    updatedParents.addAll(
        recomputeLevel(
            getAssetGroupsExpectationsByInjectAndType(inject, type),
            ExpectationUtils::getAssetsExpectationsOfAssetGroup));
    return updatedParents;
  }

  private List<TechnicalInjectExpectation> recomputeLevel(
      List<TechnicalInjectExpectation> parents,
      Function<TechnicalInjectExpectation, List<TechnicalInjectExpectation>> childrenResolver) {
    List<TechnicalInjectExpectation> updated = new ArrayList<>();
    for (TechnicalInjectExpectation parent : parents) {
      List<TechnicalInjectExpectation> children = childrenResolver.apply(parent);
      if (!children.isEmpty()) {
        Double score =
            computeChildrenScore(parent.isExpectationGroup(), parent.getExpectedScore(), children);
        // A definitive direct VULNERABLE verdict written on the parent row (e.g. by an assessment
        // injector such as Nuclei) must survive the children rollup.
        parent.setScore(reconcileWithDirectVulnerableVerdict(parent, score));
        updated.add(parent);
      }
    }
    return updated;
  }

  // -- END RECOMPUTE PARENT SCORE

}
