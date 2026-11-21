package io.openaev.service.exposure;

import static io.openaev.helper.InjectExpectationHelper.computeStatus;

import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.service.exposure.ExposureResult.AssetVerdict;
import io.openaev.service.exposure.ExposureResult.Condition;
import io.openaev.service.exposure.ExposureResult.Confidence;
import io.openaev.service.exposure.ExposureResult.ReachedAsset;
import io.openaev.service.exposure.ExposureResult.Subject;
import io.openaev.service.exposure.ExposureResult.Summary;
import io.openaev.service.exposure.ExposureResult.Verdict;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure computation of an {@link ExposureResult} from the expectations of one run. */
public final class ExposureResultBuilder {

  private ExposureResultBuilder() {}

  /**
   * What a subject is: a vulnerability, a technique or, when the contract names none, the inject.
   */
  public record SubjectRef(String type, String externalId, String name) {}

  /** One asset-level expectation of the run, flattened from the entities. */
  public record Observation(
      String injectId,
      String injectTitle,
      List<SubjectRef> subjects,
      String assetId,
      String assetName,
      String criticality,
      EXPECTATION_TYPE type,
      Double expectedScore,
      Double score) {}

  /** An asset node of the run's attack path. */
  public record PathAsset(String assetId, String assetName, String criticality) {}

  public static ExposureResult build(
      String simulationId,
      String simulationName,
      String coverageExternalId,
      List<Observation> observations,
      List<PathAsset> pathAssets,
      Instant computedAt) {
    // subject key -> asset id -> observations, insertion ordered for a stable output
    Map<SubjectRef, Map<String, List<Observation>>> bySubject = new LinkedHashMap<>();
    for (Observation observation : observations) {
      for (SubjectRef subject : observation.subjects()) {
        bySubject
            .computeIfAbsent(subject, k -> new LinkedHashMap<>())
            .computeIfAbsent(observation.assetId(), k -> new ArrayList<>())
            .add(observation);
      }
    }

    Map<String, Verdict> worstByAsset = new LinkedHashMap<>();
    List<Subject> subjects = new ArrayList<>();
    bySubject.forEach(
        (subject, byAsset) -> {
          List<AssetVerdict> assets = new ArrayList<>();
          byAsset.forEach(
              (assetId, assetObservations) -> {
                AssetVerdict verdict = assetVerdict(assetObservations);
                worstByAsset.merge(assetId, verdict.verdict(), Verdict::worst);
                assets.add(verdict);
              });
          subjects.add(
              new Subject(
                  subject.type(), subject.externalId(), subject.name(), List.copyOf(assets)));
        });

    List<ReachedAsset> reached = reachedAssets(pathAssets, worstByAsset.keySet());
    return new ExposureResult(
        ExposureResult.SCHEMA_VERSION,
        simulationId,
        simulationName,
        coverageExternalId,
        computedAt,
        summary(worstByAsset, reached),
        List.copyOf(subjects),
        reached);
  }

  static AssetVerdict assetVerdict(List<Observation> observations) {
    Observation first = observations.getFirst();
    boolean vulnerable = false;
    boolean notVulnerable = false;
    boolean prevented = false;
    boolean detected = false;
    boolean missed = false;
    int scored = 0;
    List<Condition> conditions = new ArrayList<>();
    for (Observation observation : observations) {
      EXPECTATION_STATUS status = computeStatus(observation.score(), observation.expectedScore());
      boolean success = status == EXPECTATION_STATUS.SUCCESS;
      boolean failed = status == EXPECTATION_STATUS.FAILED;
      if (success || failed) {
        scored++;
      }
      switch (observation.type()) {
        case VULNERABILITY -> {
          vulnerable |= failed;
          notVulnerable |= success;
        }
        case PREVENTION -> {
          prevented |= success;
          missed |= failed;
        }
        case DETECTION -> {
          detected |= success;
          missed |= failed;
        }
        default -> {
          // other expectation types do not describe an exposure
        }
      }
      conditions.add(
          new Condition(
              observation.injectId(),
              observation.injectTitle(),
              observation.type().name(),
              observation.expectedScore(),
              observation.score(),
              status.name()));
    }
    Verdict verdict;
    if (vulnerable) {
      verdict = Verdict.EXPOSED;
    } else if (prevented) {
      verdict = Verdict.PREVENTED;
    } else if (detected) {
      verdict = Verdict.DETECTED;
    } else if (missed) {
      // the technique ran and was neither prevented nor detected
      verdict = Verdict.EXPOSED;
    } else if (notVulnerable) {
      verdict = Verdict.NOT_EXPOSED;
    } else {
      verdict = Verdict.NOT_TESTED;
    }
    Confidence confidence =
        scored == observations.size()
            ? Confidence.HIGH
            : scored > 0 ? Confidence.MEDIUM : Confidence.LOW;
    return new AssetVerdict(
        first.assetId(),
        first.assetName(),
        first.criticality(),
        verdict,
        confidence,
        List.copyOf(conditions));
  }

  static List<ReachedAsset> reachedAssets(List<PathAsset> pathAssets, Set<String> testedAssetIds) {
    Set<String> seen = new HashSet<>(testedAssetIds);
    List<ReachedAsset> reached = new ArrayList<>();
    for (PathAsset asset : pathAssets) {
      if (asset.assetId() == null || !seen.add(asset.assetId())) {
        continue;
      }
      reached.add(
          new ReachedAsset(
              asset.assetId(), asset.assetName(), asset.criticality(), isCritical(asset)));
    }
    return List.copyOf(reached);
  }

  static boolean isCritical(PathAsset asset) {
    return AssetCriticality.VERY_HIGH.name().equals(asset.criticality())
        || AssetCriticality.HIGH.name().equals(asset.criticality());
  }

  private static Summary summary(Map<String, Verdict> worstByAsset, List<ReachedAsset> reached) {
    Map<Verdict, Integer> counts = new LinkedHashMap<>();
    worstByAsset.values().forEach(verdict -> counts.merge(verdict, 1, Integer::sum));
    return new Summary(
        worstByAsset.size(),
        counts.getOrDefault(Verdict.EXPOSED, 0),
        counts.getOrDefault(Verdict.DETECTED, 0),
        counts.getOrDefault(Verdict.PREVENTED, 0),
        counts.getOrDefault(Verdict.NOT_EXPOSED, 0),
        counts.getOrDefault(Verdict.NOT_TESTED, 0),
        (int) reached.stream().filter(ReachedAsset::critical).count());
  }
}
