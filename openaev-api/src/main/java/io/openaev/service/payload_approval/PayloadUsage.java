package io.openaev.service.payload_approval;

import io.openaev.database.raw.RawPayloadUsageItem;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.StringJoiner;

/**
 * Where a payload is used: atomic testings, scenarios, and simulations still to run. The lists hold
 * the first items by name, or are null when the viewer may not see those resources (counts only).
 */
public record PayloadUsage(
    long atomicTestingsCount,
    long scenariosCount,
    long simulationsCount,
    @Nullable List<RawPayloadUsageItem> atomicTestings,
    @Nullable List<RawPayloadUsageItem> scenarios,
    @Nullable List<RawPayloadUsageItem> simulations) {

  public boolean isUsed() {
    return atomicTestingsCount + scenariosCount + simulationsCount > 0;
  }

  /** "2 atomic testings, 1 scenario", listing only the categories in use. */
  public String describe() {
    StringJoiner joiner = new StringJoiner(", ");
    append(joiner, atomicTestingsCount, "atomic testing", "atomic testings");
    append(joiner, scenariosCount, "scenario", "scenarios");
    append(joiner, simulationsCount, "simulation", "simulations");
    return joiner.toString();
  }

  private static void append(StringJoiner joiner, long count, String singular, String plural) {
    if (count > 0) {
      joiner.add(count + " " + (count == 1 ? singular : plural));
    }
  }
}
