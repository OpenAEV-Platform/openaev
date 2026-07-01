package io.openaev.utils.object;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.audit.AuditLogContext;
import java.util.*;
import java.util.stream.Collectors;

/** Utility class for computing field-level diffs between entity snapshots. */
public class ObjectDiffUtils {

  private ObjectDiffUtils() {}

  /**
   * Computes field-level diffs from raw before/after snapshots and serializes them to a {@link
   * JsonNode} array.
   *
   * @return a JsonNode array of diff entries, or {@code null} if snapshots is null/empty
   */
  public static JsonNode computeEntityDiffsNode(
      Map<String, AuditLogContext.EntitySnapshot> snapshots, ObjectMapper objectMapper) {
    if (snapshots == null || snapshots.isEmpty()) return null;

    List<EntityDiffEntry> entries =
        snapshots.entrySet().stream()
            .map(
                e -> {
                  AuditLogContext.EntitySnapshot s = e.getValue();
                  List<FieldChange> changes = computeFieldChanges(s.before(), s.after());
                  return new EntityDiffEntry(e.getKey(), s.entityType(), s.operation(), changes);
                })
            .toList();
    return objectMapper.valueToTree(entries);
  }

  /**
   * Computes a field-level change list between two snapshots.
   *
   * @return a list of changes containing only modified fields
   */
  public static List<FieldChange> computeFieldChanges(
      Map<String, Object> before, Map<String, Object> after) {
    if (before == null && after == null) return List.of();
    if (before == null) {
      return after.entrySet().stream()
          .map(e -> new FieldChange(e.getKey(), null, e.getValue()))
          .toList();
    }
    if (after == null) {
      return before.entrySet().stream()
          .map(e -> new FieldChange(e.getKey(), e.getValue(), null))
          .toList();
    }

    Set<String> allKeys = new LinkedHashSet<>(after.keySet());
    allKeys.addAll(before.keySet());
    return allKeys.stream()
        .filter(
            key ->
                !Objects.equals(
                    normalizeForComparison(before.get(key)),
                    normalizeForComparison(after.get(key))))
        .map(key -> new FieldChange(key, before.get(key), after.get(key)))
        .toList();
  }

  /**
   * Normalizes a snapshot value for equality comparison. Lists are sorted to avoid false positives
   * caused by insertion-order differences.
   */
  static String normalizeForComparison(Object val) {
    if (val == null) return null;
    if (val instanceof Collection<?> collection) {
      return collection.stream().map(Object::toString).sorted().collect(Collectors.joining(","));
    }
    if (val instanceof Map<?, ?> map) {
      return map.entrySet().stream()
          .sorted(Map.Entry.comparingByKey(Comparator.comparing(Object::toString)))
          .map(entry -> entry.getKey() + "=" + normalizeForComparison(entry.getValue()))
          .collect(Collectors.joining("|"));
    }
    return val.toString();
  }

  // -- Value types --

  /** Per-entity diff entry for audit serialization. */
  public record EntityDiffEntry(
      String id,
      @JsonProperty("entity_type") String entityType,
      String operation,
      List<FieldChange> changes) {}

  /** Single changed field entry in audit-friendly format. */
  public record FieldChange(
      String field,
      @JsonProperty("old_value") Object oldValue,
      @JsonProperty("new_value") Object newValue) {}
}
