package io.openaev.service.attackpath;

import io.openaev.expectation.ExpectationType;
import java.util.Collection;

/**
 * The verdict semantics of a finding, in one place. A finding's verdict for each
 * bucket (prevention, detection, vulnerability) is derived from the status of its producing
 * execution rows.
 *
 * <p>Mapping is done against the {@link ExpectationType} labels of the SAME bucket
 * (case-insensitive), not a global string list: the definitive failure labels ({@code Not
 * Prevented} / {@code Not Detected} / {@code Vulnerable}) must map to {@code failed}, or a
 * confirmed-exploitable finding would render as {@code unknown}. The status vocabulary is open
 * (collectors post raw strings), so anything unrecognized maps to {@code unknown} as a fail-safe.
 *
 * <p>Aggregation across several producers is worst-of: a finding is never greener than its
 * producers. The {@code prevented => detected} rule is applied per execution, before aggregation.
 */
public final class AttackPathFindingVerdicts {

  /**
   * Legacy result string of the expectations expiration manager, duplicated here as a literal to
   * avoid coupling the verdict mapper to the collector package. Keep in sync with {@code
   * ExpectationsExpirationManagerService.EXPIRED}.
   */
  private static final String EXPIRED = "Expired";

  /** A finding's verdict for one bucket, in the front's vocabulary. */
  public enum Verdict {
    SUCCESS("success", 0),
    UNKNOWN("unknown", 1),
    FAILED("failed", 2);

    public final String label;
    private final int rank;

    Verdict(String label, int rank) {
      this.label = label;
      this.rank = rank;
    }
  }

  /** The three per-finding verdicts. */
  public record Verdicts(Verdict prevention, Verdict detection, Verdict vulnerability) {
    public static final Verdicts UNKNOWN =
        new Verdicts(Verdict.UNKNOWN, Verdict.UNKNOWN, Verdict.UNKNOWN);
  }

  private AttackPathFindingVerdicts() {}

  /**
   * The verdicts of a single producing execution, from its three status strings. Applies {@code
   * prevented => detected} (a prevented execution is at least detected, overriding an explicit
   * failed).
   */
  public static Verdicts ofExecution(
      String preventionStatus, String detectionStatus, String vulnerabilityStatus) {
    Verdict prevention = map(ExpectationType.PREVENTION, preventionStatus);
    Verdict detection = map(ExpectationType.DETECTION, detectionStatus);
    Verdict vulnerability = map(ExpectationType.VULNERABILITY, vulnerabilityStatus);
    if (prevention == Verdict.SUCCESS) {
      detection = Verdict.SUCCESS; // prevented => detected
    }
    return new Verdicts(prevention, detection, vulnerability);
  }

  /** Worst-of over a finding's producers, per bucket. Empty producers = all unknown. */
  public static Verdicts aggregate(Collection<Verdicts> producers) {
    Verdict prevention = Verdict.SUCCESS;
    Verdict detection = Verdict.SUCCESS;
    Verdict vulnerability = Verdict.SUCCESS;
    if (producers.isEmpty()) {
      return Verdicts.UNKNOWN;
    }
    for (Verdicts p : producers) {
      prevention = worst(prevention, p.prevention());
      detection = worst(detection, p.detection());
      vulnerability = worst(vulnerability, p.vulnerability());
    }
    return new Verdicts(prevention, detection, vulnerability);
  }

  private static Verdict worst(Verdict a, Verdict b) {
    return a.rank >= b.rank ? a : b;
  }

  private static Verdict map(ExpectationType type, String raw) {
    if (raw == null) {
      return Verdict.UNKNOWN;
    }
    String value = raw.trim();
    if (eq(value, type.successLabel)
        || eq(value, ExpectationType.HUMAN_RESPONSE.successLabel) // "Successful"
        || eq(value, ExpectationType.SUCCESS_ID)) { // legacy "SUCCESS"
      return Verdict.SUCCESS;
    }
    if (eq(value, type.failureLabel)
        || eq(value, type.partialLabel)
        || eq(value, ExpectationType.HUMAN_RESPONSE.partialLabel) // "Partial"
        || eq(value, ExpectationType.FAILED_ID) // legacy "FAILED" / "Failed" (case-insensitive)
        || eq(value, EXPIRED)) {
      return Verdict.FAILED;
    }
    return Verdict.UNKNOWN; // pendingLabel, null already handled, and any unrecognized string
  }

  private static boolean eq(String a, String b) {
    return a.equalsIgnoreCase(b);
  }
}
