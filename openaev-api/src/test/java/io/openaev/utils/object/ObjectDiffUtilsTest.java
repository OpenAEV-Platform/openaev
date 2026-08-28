package io.openaev.utils.object;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.audit.AuditLogContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ObjectDiffUtilsTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  // ---------------------------------------------------------------------------
  // computeEntityDiffsNode
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("computeEntityDiffsNode")
  class ComputeEntityDiffsNode {

    @Test
    @DisplayName("Should return null when snapshots map is null")
    void given_nullSnapshots_should_returnNull() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.computeEntityDiffsNode(null, objectMapper)).isNull();
    }

    @Test
    @DisplayName("Should return null when snapshots map is empty")
    void given_emptySnapshots_should_returnNull() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.computeEntityDiffsNode(Map.of(), objectMapper)).isNull();
    }

    @Test
    @DisplayName("Should return a JSON array with one entry for a single snapshot")
    void given_singleSnapshot_should_returnJsonArrayWithOneEntry() {
      // -- ARRANGE --
      var snapshot =
          new AuditLogContext.EntitySnapshot(
              "User", "UPDATE", Map.of("name", "Alice"), Map.of("name", "Bob"));
      Map<String, AuditLogContext.EntitySnapshot> snapshots = Map.of("user-1", snapshot);

      // -- ACT --
      JsonNode result = ObjectDiffUtils.computeEntityDiffsNode(snapshots, objectMapper);

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.isArray()).isTrue();
      assertThat(result.size()).isEqualTo(1);

      JsonNode entry = result.get(0);
      assertThat(entry.get("id").asText()).isEqualTo("user-1");
      assertThat(entry.get("entity_type").asText()).isEqualTo("User");
      assertThat(entry.get("operation").asText()).isEqualTo("UPDATE");
      assertThat(entry.get("changes").isArray()).isTrue();
      assertThat(entry.get("changes").size()).isEqualTo(1);

      JsonNode firstChange = entry.get("changes").get(0);
      assertThat(firstChange.get("old_value").asText()).isEqualTo("Alice");
      assertThat(firstChange.get("new_value").asText()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("Should return a JSON array with multiple entries for multiple snapshots")
    void given_multipleSnapshots_should_returnJsonArrayWithMultipleEntries() {
      // -- ARRANGE --
      Map<String, AuditLogContext.EntitySnapshot> snapshots = new LinkedHashMap<>();
      snapshots.put(
          "user-1",
          new AuditLogContext.EntitySnapshot(
              "User", "UPDATE", Map.of("name", "Alice"), Map.of("name", "Bob")));
      snapshots.put(
          "org-1",
          new AuditLogContext.EntitySnapshot(
              "Organization", "DELETE", Map.of("orgName", "Acme"), null));

      // -- ACT --
      JsonNode result = ObjectDiffUtils.computeEntityDiffsNode(snapshots, objectMapper);

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.isArray()).isTrue();
      assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should include an empty changes array when before equals after")
    void given_unchangedSnapshot_should_returnEmptyChangesArray() {
      // -- ARRANGE --
      Map<String, Object> state = Map.of("name", "Alice", "age", 30);
      var snapshot = new AuditLogContext.EntitySnapshot("User", "UPDATE", state, state);
      Map<String, AuditLogContext.EntitySnapshot> snapshots = Map.of("user-1", snapshot);

      // -- ACT --
      JsonNode result = ObjectDiffUtils.computeEntityDiffsNode(snapshots, objectMapper);

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.get(0).get("changes").size()).isZero();
    }
  }

  // ---------------------------------------------------------------------------
  // computeFieldChanges
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("computeFieldChanges")
  class ComputeFieldChanges {

    @Test
    @DisplayName("Should return empty list when both before and after are null")
    void given_bothNull_should_returnEmptyList() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.computeFieldChanges(null, null)).isEmpty();
    }

    @Test
    @DisplayName("Should return all after fields as additions when before is null")
    void given_beforeNull_should_returnAllAfterFieldsAsAdditions() {
      // -- ARRANGE --
      Map<String, Object> after = Map.of("name", "Alice", "age", 30);

      // -- ACT --
      List<ObjectDiffUtils.FieldChange> changes = ObjectDiffUtils.computeFieldChanges(null, after);

      // -- ASSERT --
      assertThat(changes)
          .hasSize(2)
          .allSatisfy(c -> assertThat(c.oldValue()).isNull())
          .extracting(ObjectDiffUtils.FieldChange::field)
          .containsExactlyInAnyOrder("name", "age");
    }

    @Test
    @DisplayName("Should return all before fields as removals when after is null")
    void given_afterNull_should_returnAllBeforeFieldsAsRemovals() {
      // -- ARRANGE --
      Map<String, Object> before = Map.of("name", "Alice", "age", 30);

      // -- ACT --
      List<ObjectDiffUtils.FieldChange> changes = ObjectDiffUtils.computeFieldChanges(before, null);

      // -- ASSERT --
      assertThat(changes)
          .hasSize(2)
          .allSatisfy(c -> assertThat(c.newValue()).isNull())
          .extracting(ObjectDiffUtils.FieldChange::field)
          .containsExactlyInAnyOrder("name", "age");
    }

    @Test
    @DisplayName("Should return empty list when before and after are identical")
    void given_identicalMaps_should_returnEmptyList() {
      // -- ARRANGE --
      Map<String, Object> state = Map.of("name", "Alice", "age", 30);

      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.computeFieldChanges(state, state)).isEmpty();
    }

    @Test
    @DisplayName("Should detect a modified field")
    void given_modifiedField_should_returnOneChange() {
      // -- ARRANGE --
      Map<String, Object> before = Map.of("name", "Alice");
      Map<String, Object> after = Map.of("name", "Bob");

      // -- ACT --
      List<ObjectDiffUtils.FieldChange> changes =
          ObjectDiffUtils.computeFieldChanges(before, after);

      // -- ASSERT --
      assertThat(changes).hasSize(1);
      ObjectDiffUtils.FieldChange change = changes.getFirst();
      assertThat(change.field()).isEqualTo("name");
      assertThat(change.oldValue()).isEqualTo("Alice");
      assertThat(change.newValue()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("Should detect a newly added field")
    void given_newFieldInAfter_should_includeAdditionChange() {
      // -- ARRANGE --
      Map<String, Object> before = Map.of("name", "Alice");
      Map<String, Object> after = new LinkedHashMap<>();
      after.put("name", "Alice");
      after.put("email", "alice@example.com");

      // -- ACT --
      List<ObjectDiffUtils.FieldChange> changes =
          ObjectDiffUtils.computeFieldChanges(before, after);

      // -- ASSERT --
      assertThat(changes).hasSize(1);
      assertThat(changes.getFirst().field()).isEqualTo("email");
      assertThat(changes.getFirst().oldValue()).isNull();
      assertThat(changes.getFirst().newValue()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Should detect a removed field")
    void given_fieldRemovedInAfter_should_includeRemovalChange() {
      // -- ARRANGE --
      Map<String, Object> before = new LinkedHashMap<>();
      before.put("name", "Alice");
      before.put("email", "alice@example.com");
      Map<String, Object> after = Map.of("name", "Alice");

      // -- ACT --
      List<ObjectDiffUtils.FieldChange> changes =
          ObjectDiffUtils.computeFieldChanges(before, after);

      // -- ASSERT --
      assertThat(changes).hasSize(1);
      assertThat(changes.getFirst().field()).isEqualTo("email");
      assertThat(changes.getFirst().oldValue()).isEqualTo("alice@example.com");
      assertThat(changes.getFirst().newValue()).isNull();
    }

    @Test
    @DisplayName("Should not report a change when collection fields differ only in order")
    void given_collectionFieldsWithDifferentOrder_should_notReportChange() {
      // -- ARRANGE --
      Map<String, Object> before = new LinkedHashMap<>();
      before.put("roles", new ArrayList<>(List.of("ADMIN", "USER")));
      Map<String, Object> after = new LinkedHashMap<>();
      after.put("roles", new ArrayList<>(List.of("USER", "ADMIN")));

      // -- ACT --
      List<ObjectDiffUtils.FieldChange> changes =
          ObjectDiffUtils.computeFieldChanges(before, after);

      // -- ASSERT --
      assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("Should report a change when collection fields have different elements")
    void given_collectionFieldsWithDifferentElements_should_reportChange() {
      // -- ARRANGE --
      Map<String, Object> before = Map.of("roles", List.of("ADMIN"));
      Map<String, Object> after = Map.of("roles", List.of("USER"));

      // -- ACT --
      List<ObjectDiffUtils.FieldChange> changes =
          ObjectDiffUtils.computeFieldChanges(before, after);

      // -- ASSERT --
      assertThat(changes).hasSize(1);
      assertThat(changes.getFirst().field()).isEqualTo("roles");
    }
  }

  // ---------------------------------------------------------------------------
  // normalizeForComparison
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("normalizeForComparison")
  class NormalizeForComparison {

    @Test
    @DisplayName("Should return null when value is null")
    void given_nullValue_should_returnNull() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.normalizeForComparison(null)).isNull();
    }

    @Test
    @DisplayName("Should return toString representation for a plain string value")
    void given_plainStringValue_should_returnSameString() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.normalizeForComparison("hello")).isEqualTo("hello");
    }

    @Test
    @DisplayName("Should return toString representation for a numeric value")
    void given_numericValue_should_returnStringRepresentation() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.normalizeForComparison(42)).isEqualTo("42");
    }

    @Test
    @DisplayName("Should return the same normalized string for collections differing only in order")
    void given_collectionsWithDifferentOrder_should_returnSameNormalizedString() {
      // -- ARRANGE --
      List<String> list1 = List.of("banana", "apple", "cherry");
      List<String> list2 = List.of("cherry", "banana", "apple");

      // -- ACT --
      String norm1 = ObjectDiffUtils.normalizeForComparison(list1);
      String norm2 = ObjectDiffUtils.normalizeForComparison(list2);

      // -- ASSERT --
      assertThat(norm1).isEqualTo(norm2);
    }

    @Test
    @DisplayName(
        "Should return different normalized strings for collections with different elements")
    void given_collectionsWithDifferentElements_should_returnDifferentNormalizedStrings() {
      // -- ARRANGE --
      List<String> list1 = List.of("apple", "banana");
      List<String> list2 = List.of("apple", "mango");

      // -- ACT --
      String norm1 = ObjectDiffUtils.normalizeForComparison(list1);
      String norm2 = ObjectDiffUtils.normalizeForComparison(list2);

      // -- ASSERT --
      assertThat(norm1).isNotEqualTo(norm2);
    }

    @Test
    @DisplayName(
        "Should return the same normalized string for maps with same entries in different insertion order")
    void given_mapsWithDifferentInsertionOrder_should_returnSameNormalizedString() {
      // -- ARRANGE --
      Map<String, Object> map1 = new LinkedHashMap<>();
      map1.put("z", "last");
      map1.put("a", "first");
      map1.put("m", "middle");
      Map<String, Object> map2 = new LinkedHashMap<>();
      map2.put("a", "first");
      map2.put("m", "middle");
      map2.put("z", "last");

      // -- ACT --
      String norm1 = ObjectDiffUtils.normalizeForComparison(map1);
      String norm2 = ObjectDiffUtils.normalizeForComparison(map2);

      // -- ASSERT --
      assertThat(norm1).isEqualTo(norm2);
    }

    @Test
    @DisplayName(
        "Should return the same normalized string for nested maps with same entries in different insertion order")
    void given_nestedMapsWithDifferentInsertionOrder_should_returnSameNormalizedString() {
      // -- ARRANGE --
      Map<String, Object> inner1 = new LinkedHashMap<>();
      inner1.put("y", "20");
      inner1.put("x", "10");
      Map<String, Object> outer1 = new LinkedHashMap<>();
      outer1.put("nested", inner1);

      Map<String, Object> inner2 = new LinkedHashMap<>();
      inner2.put("x", "10");
      inner2.put("y", "20");
      Map<String, Object> outer2 = new LinkedHashMap<>();
      outer2.put("nested", inner2);

      // -- ACT --
      String norm1 = ObjectDiffUtils.normalizeForComparison(outer1);
      String norm2 = ObjectDiffUtils.normalizeForComparison(outer2);

      // -- ASSERT --
      assertThat(norm1).isEqualTo(norm2);
    }

    @Test
    @DisplayName("Should handle an empty collection")
    void given_emptyCollection_should_returnEmptyString() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.normalizeForComparison(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Should handle an empty map")
    void given_emptyMap_should_returnEmptyString() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.normalizeForComparison(Map.of())).isEmpty();
    }
  }
}
