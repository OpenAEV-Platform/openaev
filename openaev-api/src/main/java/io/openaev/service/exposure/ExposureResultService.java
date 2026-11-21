package io.openaev.service.exposure;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import io.openaev.service.exposure.ExposureResultBuilder.Observation;
import io.openaev.service.exposure.ExposureResultBuilder.PathAsset;
import io.openaev.service.exposure.ExposureResultBuilder.SubjectRef;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@link ExposureResult} of a simulation run from its asset-level expectations and, when
 * the run carries attack path data, from the assets of its attack path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExposureResultService {

  static final String SUBJECT_VULNERABILITY = "vulnerability";
  static final String SUBJECT_TECHNIQUE = "technique";
  static final String SUBJECT_INJECT = "inject";
  private static final String ASSET_NODE = "ASSET";

  private final ExerciseService exerciseService;
  private final InjectExpectationRepository injectExpectationRepository;
  private final AttackPathGraphService attackPathGraphService;

  /** The exposure result of a simulation read inside the request's tenant scope. */
  @Transactional(readOnly = true)
  public ExposureResult exposureResult(final TxCtx ctx, @NotBlank final String simulationId) {
    Exercise simulation =
        exerciseService
            .findByIdInScope(ctx, simulationId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException("Simulation not found with id: " + simulationId));
    return compute(simulation);
  }

  /**
   * Computes the result of a simulation already loaded inside a tenant-scoped transaction (the
   * request's, or the one the notification engine opens for a background event).
   */
  public ExposureResult compute(final Exercise simulation) {
    List<Observation> observations = new ArrayList<>();
    for (BaseInjectExpectation expectation :
        injectExpectationRepository.findAllForExercise(simulation.getId())) {
      observation(expectation).ifPresent(observations::add);
    }
    SecurityCoverage coverage = simulation.getSecurityCoverage();
    return ExposureResultBuilder.build(
        simulation.getId(),
        simulation.getName(),
        coverage != null ? coverage.getExternalId() : null,
        observations,
        pathAssets(simulation.getId()),
        Instant.now());
  }

  /** Asset-level technical expectations only: a group-level row aggregates its assets' rows. */
  static Optional<Observation> observation(final BaseInjectExpectation expectation) {
    if (!(expectation instanceof TechnicalInjectExpectation technical)
        || technical.getAsset() == null
        || technical.getAssetGroup() != null) {
      return Optional.empty();
    }
    Inject inject = technical.getInject();
    Asset asset = technical.getAsset();
    return Optional.of(
        new Observation(
            inject != null ? inject.getId() : null,
            inject != null ? inject.getTitle() : null,
            subjects(inject),
            asset.getId(),
            asset.getName(),
            asset.getCriticality() != null ? asset.getCriticality().name() : null,
            technical.getType(),
            technical.getExpectedScore(),
            technical.getScore()));
  }

  /** The CVEs and ATT&CK techniques the inject's contract tests, else the inject itself. */
  static List<SubjectRef> subjects(final Inject inject) {
    List<SubjectRef> subjects = new ArrayList<>();
    Optional<InjectorContract> contract =
        inject != null ? inject.getInjectorContract() : Optional.empty();
    if (contract.isPresent()) {
      for (Vulnerability vulnerability : contract.get().getVulnerabilities()) {
        subjects.add(
            new SubjectRef(
                SUBJECT_VULNERABILITY,
                vulnerability.getExternalId(),
                vulnerability.getExternalId()));
      }
      for (AttackPattern attackPattern : contract.get().getAttackPatterns()) {
        subjects.add(
            new SubjectRef(
                SUBJECT_TECHNIQUE, attackPattern.getExternalId(), attackPattern.getName()));
      }
    }
    if (subjects.isEmpty() && inject != null) {
      subjects.add(new SubjectRef(SUBJECT_INJECT, inject.getId(), inject.getTitle()));
    }
    return subjects;
  }

  /** Asset nodes of the run's attack path; empty when the run has no attack path data. */
  private List<PathAsset> pathAssets(final String simulationId) {
    try {
      AttackPathDTO graph = attackPathGraphService.buildGraph(simulationId);
      if (graph == null || graph.attackPathNodes() == null) {
        return List.of();
      }
      List<PathAsset> assets = new ArrayList<>();
      for (AttackPathNodeDTO node : graph.attackPathNodes()) {
        if (ASSET_NODE.equals(node.getType()) && node.getEntityKind() == null) {
          assets.add(new PathAsset(node.getRef(), node.getHostname(), node.getCriticality()));
        }
      }
      return assets;
    } catch (RuntimeException e) {
      // The attack path is an enrichment: a run without it still has its verdicts.
      log.warn("Attack path unavailable for simulation {}", simulationId, e);
      return List.of();
    }
  }
}
