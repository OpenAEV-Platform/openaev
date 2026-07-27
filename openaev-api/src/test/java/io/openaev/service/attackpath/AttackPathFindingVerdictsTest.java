package io.openaev.service.attackpath;

import static io.openaev.service.attackpath.AttackPathFindingVerdicts.Verdict.FAILED;
import static io.openaev.service.attackpath.AttackPathFindingVerdicts.Verdict.SUCCESS;
import static io.openaev.service.attackpath.AttackPathFindingVerdicts.Verdict.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.service.attackpath.AttackPathFindingVerdicts.Verdict;
import io.openaev.service.attackpath.AttackPathFindingVerdicts.Verdicts;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the per-finding verdict semantics (label mapping, prevented=>detected, worst-of).
 */
class AttackPathFindingVerdictsTest {

  @Test
  @DisplayName("success labels map to success, including the vulnerability inversion")
  void successLabels() {
    assertThat(ofOne("Prevented", "Detected", "Not vulnerable"))
        .isEqualTo(new Verdicts(SUCCESS, SUCCESS, SUCCESS));
  }

  @Test
  @DisplayName("CRITICAL: the definitive failure labels map to failed, not unknown")
  void failureLabelsMapToFailed() {
    // A confirmed-exploitable CVE ("Vulnerable") must be red, not grey. Prevention is not success
    // here, so the prevented=>detected rule does not mask the detection failure.
    assertThat(ofOne("Not Prevented", "Not Detected", "Vulnerable"))
        .isEqualTo(new Verdicts(FAILED, FAILED, FAILED));
  }

  @Test
  @DisplayName("partial labels map to failed")
  void partialLabels() {
    assertThat(ofOne("Partially Prevented", "Partially Detected", "Partially vulnerable"))
        .isEqualTo(new Verdicts(FAILED, FAILED, FAILED));
  }

  @Test
  @DisplayName("pending, null and unrecognized strings map to unknown")
  void unknownLabels() {
    assertThat(ofOne("Pending", null, "Quarantined"))
        .isEqualTo(new Verdicts(UNKNOWN, UNKNOWN, UNKNOWN));
  }

  @Test
  @DisplayName(
      "CRITICAL: a bucket rejects other buckets' labels (per-bucket slot matching, not a global set)")
  void crossBucketLabelsMapToUnknown() {
    // "Detected"/"Vulnerable"/"Prevented"/"Not Detected" are definitive verdicts in OTHER buckets;
    // fed to the wrong bucket they are unrecognized. A global label list would wrongly turn them
    // into success/failed here, silently making up a definitive verdict from cross-bucket data.
    assertThat(map("prevention", "Detected")).isEqualTo(UNKNOWN);
    assertThat(map("prevention", "Vulnerable")).isEqualTo(UNKNOWN);
    assertThat(map("detection", "Prevented")).isEqualTo(UNKNOWN);
    assertThat(map("vulnerability", "Not Detected")).isEqualTo(UNKNOWN);
  }

  @Test
  @DisplayName(
      "generic and legacy aliases: Successful/SUCCESS -> success, Failed/FAILED/Expired -> failed")
  void aliases() {
    assertThat(map("prevention", "success")).isEqualTo(SUCCESS);
    assertThat(map("prevention", "SUCCESS")).isEqualTo(SUCCESS);
    assertThat(map("prevention", "Successful")).isEqualTo(SUCCESS);
    // the raw graph value is case-insensitive, so the front's lowercase legacy aliases match too
    assertThat(map("detection", "detected")).isEqualTo(SUCCESS);
    assertThat(map("detection", "Failed")).isEqualTo(FAILED);
    assertThat(map("detection", "FAILED")).isEqualTo(FAILED);
    assertThat(map("vulnerability", "Expired")).isEqualTo(FAILED);
  }

  @Test
  @DisplayName(
      "prevented => detected: a prevented execution's detection is success, overriding failed")
  void preventedImpliesDetected() {
    assertThat(ofOne("Prevented", "Not Detected", "Not vulnerable"))
        .isEqualTo(new Verdicts(SUCCESS, SUCCESS, SUCCESS));
  }

  @Test
  @DisplayName("Partially Prevented does NOT lift detection")
  void partiallyPreventedDoesNotLiftDetection() {
    assertThat(ofOne("Partially Prevented", "Not Detected", "Not vulnerable"))
        .isEqualTo(new Verdicts(FAILED, FAILED, SUCCESS));
  }

  @Test
  @DisplayName("prevented => detected is folded per execution, before aggregation (order matters)")
  void preventedImpliesDetectedFoldedPerExecution() {
    // Producer A is prevented (so detected) yet reports "Not Detected"; producer B is not prevented
    // but detected. Folding per execution lifts A's detection to success, so worst-of detection is
    // success. Folding on the aggregate instead (prevention aggregate = failed, so no lift) would
    // wrongly give detection = failed. This pins the fold-then-aggregate order.
    Verdicts a = ofOne("Prevented", "Not Detected", "Not vulnerable");
    Verdicts b = ofOne("Not Prevented", "Detected", "Not vulnerable");
    assertThat(AttackPathFindingVerdicts.aggregate(List.of(a, b)))
        .isEqualTo(new Verdicts(FAILED, SUCCESS, SUCCESS));
  }

  @Test
  @DisplayName(
      "worst-of aggregation: any failed wins; success only if all success; empty is unknown")
  void worstOfAggregation() {
    Verdicts prevented = ofOne("Prevented", "Detected", "Not vulnerable");
    Verdicts notPrevented = ofOne("Not Prevented", "Not Detected", "Vulnerable");
    Verdicts noStatus = ofOne(null, null, null);

    assertThat(AttackPathFindingVerdicts.aggregate(List.of(prevented, notPrevented)))
        .as("success + failed -> failed")
        .isEqualTo(new Verdicts(FAILED, FAILED, FAILED));
    assertThat(AttackPathFindingVerdicts.aggregate(List.of(notPrevented, noStatus)))
        .as("failed + unknown -> failed (failed outranks unknown)")
        .isEqualTo(new Verdicts(FAILED, FAILED, FAILED));
    assertThat(AttackPathFindingVerdicts.aggregate(List.of(prevented, noStatus)))
        .as("success + unknown -> unknown (a finding is never greener than its producers)")
        .isEqualTo(new Verdicts(UNKNOWN, UNKNOWN, UNKNOWN));
    assertThat(AttackPathFindingVerdicts.aggregate(List.of(prevented, prevented)))
        .as("all success -> success")
        .isEqualTo(new Verdicts(SUCCESS, SUCCESS, SUCCESS));
    assertThat(AttackPathFindingVerdicts.aggregate(List.of()))
        .as("no producers -> all unknown")
        .isEqualTo(Verdicts.UNKNOWN);
  }

  @Test
  @DisplayName("the verdict labels are the front's lowercase vocabulary")
  void labels() {
    assertThat(SUCCESS.label).isEqualTo("success");
    assertThat(FAILED.label).isEqualTo("failed");
    assertThat(UNKNOWN.label).isEqualTo("unknown");
  }

  private static Verdicts ofOne(String prevention, String detection, String vulnerability) {
    return AttackPathFindingVerdicts.ofExecution(prevention, detection, vulnerability);
  }

  // Maps a single bucket by routing through ofExecution and reading the matching field, so the test
  // never re-implements the mapping.
  private static Verdict map(String bucket, String raw) {
    return switch (bucket) {
      case "prevention" -> AttackPathFindingVerdicts.ofExecution(raw, null, null).prevention();
      case "detection" -> AttackPathFindingVerdicts.ofExecution(null, raw, null).detection();
      default -> AttackPathFindingVerdicts.ofExecution(null, null, raw).vulnerability();
    };
  }
}
