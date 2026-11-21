package io.openaev.service.exposure;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.service.exposure.ExposureResult.AssetVerdict;
import io.openaev.service.exposure.ExposureResult.Confidence;
import io.openaev.service.exposure.ExposureResult.Subject;
import io.openaev.service.exposure.ExposureResult.Verdict;
import io.openaev.service.exposure.ExposureResultBuilder.Observation;
import io.openaev.service.exposure.ExposureResultBuilder.PathAsset;
import io.openaev.service.exposure.ExposureResultBuilder.SubjectRef;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExposureResultBuilderTest {

  private static final SubjectRef CVE = new SubjectRef("vulnerability", "CVE-2026-1", "CVE-2026-1");
  private static final SubjectRef T1190 =
      new SubjectRef("technique", "T1190", "Exploit Public-Facing Application");

  private static Observation obs(
      String assetId,
      EXPECTATION_TYPE type,
      Double expected,
      Double score,
      SubjectRef... subjects) {
    return new Observation(
        "inject-1",
        "Check",
        List.of(subjects),
        assetId,
        "host-" + assetId,
        "MEDIUM",
        type,
        expected,
        score);
  }

  @Test
  @DisplayName("one of three assets vulnerable: exactly that asset is exposed, with its conditions")
  void oneOfThreeExposed() {
    List<Observation> observations =
        List.of(
            obs("a1", EXPECTATION_TYPE.VULNERABILITY, 100.0, 0.0, CVE),
            obs("a2", EXPECTATION_TYPE.VULNERABILITY, 100.0, 100.0, CVE),
            obs("a3", EXPECTATION_TYPE.VULNERABILITY, 100.0, 100.0, CVE));

    ExposureResult result =
        ExposureResultBuilder.build("sim", "Sim", "cov-1", observations, List.of(), Instant.now());

    assertThat(result.schemaVersion()).isEqualTo(ExposureResult.SCHEMA_VERSION);
    assertThat(result.coverageExternalId()).isEqualTo("cov-1");
    Subject subject = result.subjects().getFirst();
    assertThat(subject.externalId()).isEqualTo("CVE-2026-1");
    List<AssetVerdict> exposed =
        subject.assets().stream().filter(a -> a.verdict() == Verdict.EXPOSED).toList();
    assertThat(exposed).extracting(AssetVerdict::assetId).containsExactly("a1");
    assertThat(exposed.getFirst().conditions())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.expectationType()).isEqualTo("VULNERABILITY");
              assertThat(c.status()).isEqualTo("FAILED");
            });
    assertThat(result.summary().assetsTested()).isEqualTo(3);
    assertThat(result.summary().assetsExposed()).isEqualTo(1);
    assertThat(result.summary().assetsNotExposed()).isEqualTo(2);
  }

  @Test
  @DisplayName("technique verdicts: prevented wins over detected, a miss on both is exposed")
  void techniqueVerdicts() {
    AssetVerdict prevented =
        ExposureResultBuilder.assetVerdict(
            List.of(
                obs("a", EXPECTATION_TYPE.PREVENTION, 100.0, 100.0, T1190),
                obs("a", EXPECTATION_TYPE.DETECTION, 100.0, 100.0, T1190)));
    AssetVerdict detected =
        ExposureResultBuilder.assetVerdict(
            List.of(
                obs("a", EXPECTATION_TYPE.PREVENTION, 100.0, 0.0, T1190),
                obs("a", EXPECTATION_TYPE.DETECTION, 100.0, 100.0, T1190)));
    AssetVerdict missed =
        ExposureResultBuilder.assetVerdict(
            List.of(
                obs("a", EXPECTATION_TYPE.PREVENTION, 100.0, 0.0, T1190),
                obs("a", EXPECTATION_TYPE.DETECTION, 100.0, 0.0, T1190)));

    assertThat(prevented.verdict()).isEqualTo(Verdict.PREVENTED);
    assertThat(detected.verdict()).isEqualTo(Verdict.DETECTED);
    assertThat(missed.verdict()).isEqualTo(Verdict.EXPOSED);
    assertThat(missed.confidence()).isEqualTo(Confidence.HIGH);
  }

  @Test
  @DisplayName("nothing scored yet: not tested, low confidence")
  void pendingIsNotTested() {
    AssetVerdict verdict =
        ExposureResultBuilder.assetVerdict(
            List.of(obs("a", EXPECTATION_TYPE.VULNERABILITY, 100.0, null, CVE)));

    assertThat(verdict.verdict()).isEqualTo(Verdict.NOT_TESTED);
    assertThat(verdict.confidence()).isEqualTo(Confidence.LOW);
  }

  @Test
  @DisplayName("reached assets exclude tested ones; high and very high count as critical, once")
  void reachedAssets() {
    List<Observation> observations =
        List.of(obs("a1", EXPECTATION_TYPE.VULNERABILITY, 100.0, 0.0, CVE));
    List<PathAsset> path =
        List.of(
            new PathAsset("a1", "entry", "LOW"),
            new PathAsset("db", "tenant-db", "VERY_HIGH"),
            new PathAsset("db", "tenant-db", "VERY_HIGH"),
            new PathAsset("web", "web", "HIGH"),
            new PathAsset("dev", "dev", "MEDIUM"),
            new PathAsset(null, "discovered", null));

    ExposureResult result =
        ExposureResultBuilder.build("sim", "Sim", null, observations, path, Instant.now());

    assertThat(result.reachedAssets())
        .extracting(ExposureResult.ReachedAsset::assetId)
        .containsExactly("db", "web", "dev");
    assertThat(result.summary().criticalAssetsReached()).isEqualTo(2);
  }

  @Test
  @DisplayName("an asset counts once in the summary, under its most severe verdict")
  void summaryCountsWorstVerdict() {
    List<Observation> observations =
        List.of(
            obs("a1", EXPECTATION_TYPE.VULNERABILITY, 100.0, 100.0, CVE),
            obs("a1", EXPECTATION_TYPE.PREVENTION, 100.0, 0.0, T1190),
            obs("a1", EXPECTATION_TYPE.DETECTION, 100.0, 0.0, T1190));

    ExposureResult result =
        ExposureResultBuilder.build("sim", "Sim", null, observations, List.of(), Instant.now());

    assertThat(result.subjects()).hasSize(2);
    assertThat(result.summary().assetsTested()).isEqualTo(1);
    assertThat(result.summary().assetsExposed()).isEqualTo(1);
    assertThat(result.summary().assetsNotExposed()).isZero();
  }
}
