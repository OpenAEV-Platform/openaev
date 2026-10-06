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
 * hex-encoded. Non-cryptographic by design — values come from internal engine outputs, not from
 * adversary-controlled input.
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
        .forEach(p -> sb.append(p.key()).append("=").append(p.value()).append("|"));
    return murmur3(sb.toString());
  }

  private static String murmur3(String canonical) {
    return Hashing.murmur3_128().hashString(canonical, StandardCharsets.UTF_8).toString();
  }
}
