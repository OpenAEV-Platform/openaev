package io.openaev.service.chaining;

import com.google.common.hash.Hashing;
import io.openaev.database.model.WorkflowStateEntries;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic fingerprints used by the chaining engine state (ADR-011): MurmurHash3, 128-bit,
 * hex-encoded.
 *
 * <p>The hashed values come from the outputs of targeted machines, so they may be influenced by an
 * adversary. A non-cryptographic hash is accepted because of what a collision can do: every lookup
 * is scoped to one workflow state; a tuple collision can only recombine values that each passed
 * scope validation; an execution-hash collision makes a combination look already executed, so it
 * suppresses an execution and never causes a replay.
 */
public final class ChainingHashUtils {

  private ChainingHashUtils() {}

  /**
   * Fingerprint of an executed input combination (anti-replay hash).
   *
   * <p>The format must never change: committed hashes are compared against freshly computed ones
   * for the whole life of a run.
   */
  public static String hashCombo(Map<String, String> combo) {
    // Canonicalize key order so hash is stable regardless of map implementation.
    StringBuilder sb = new StringBuilder();
    new TreeMap<>(combo).forEach((k, v) -> sb.append(k).append("=").append(v).append("|"));
    return murmur3(sb.toString());
  }

  /**
   * Fingerprint of a correlated tuple, identifying it by content. Pairs are sorted by key then
   * value, so the hash does not depend on iteration order, and a tuple holding several pairs with
   * the same key (e.g. two IPv4 fields) keeps all of them.
   *
   * <p>Each key and value is length-prefixed ({@code <length>:<text>}) rather than joined with
   * separators: values come from the outputs of targeted machines and may contain any character, so
   * a separator-based encoding would let a value forge a field boundary and make two different
   * tuples share a hash (e.g. {@code [(IPv4, "x|Port=y")]} vs {@code [(IPv4, "x"), (Port, "y")]}).
   *
   * <p>Must stay identical to the copy frozen in the migration that converted the legacy JSONB
   * state ({@code V6_20261005160000000}).
   */
  public static String hashTuple(Collection<WorkflowStateEntries.Pair> pairs) {
    StringBuilder sb = new StringBuilder();
    pairs.stream()
        .sorted(
            Comparator.comparing(
                    WorkflowStateEntries.Pair::key,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(
                    WorkflowStateEntries.Pair::value,
                    Comparator.nullsFirst(Comparator.naturalOrder())))
        .forEach(
            p -> {
              appendField(sb, p.key());
              appendField(sb, p.value());
            });
    return murmur3(sb.toString());
  }

  /** Appends {@code <length>:<text>}, or {@code -1:} for {@code null}. */
  private static void appendField(StringBuilder sb, String field) {
    if (field == null) {
      sb.append("-1:");
    } else {
      sb.append(field.length()).append(':').append(field);
    }
  }

  private static String murmur3(String canonical) {
    return Hashing.murmur3_128().hashString(canonical, StandardCharsets.UTF_8).toString();
  }
}
