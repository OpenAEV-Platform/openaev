package io.openaev.utils.object;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.audit.EntityDiffContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
          new EntityDiffContext.EntitySnapshot(
              "User", "UPDATE", Map.of("name", "Alice"), Map.of("name", "Bob"));
      Map<String, EntityDiffContext.EntitySnapshot> snapshots = Map.of("user-1", snapshot);

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
    }

    @Test
    @DisplayName("Should return a JSON array with multiple entries for multiple snapshots")
    void given_multipleSnapshots_should_returnJsonArrayWithMultipleEntries() {
      // -- ARRANGE --
      Map<String, EntityDiffContext.EntitySnapshot> snapshots = new LinkedHashMap<>();
      snapshots.put(
          "user-1",
          new EntityDiffContext.EntitySnapshot(
              "User", "UPDATE", Map.of("name", "Alice"), Map.of("name", "Bob")));
      snapshots.put(
          "org-1",
          new EntityDiffContext.EntitySnapshot(
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
      var snapshot = new EntityDiffContext.EntitySnapshot("User", "UPDATE", state, state);
      Map<String, EntityDiffContext.EntitySnapshot> snapshots = Map.of("user-1", snapshot);

      // -- ACT --
      JsonNode result = ObjectDiffUtils.computeEntityDiffsNode(snapshots, objectMapper);

      // -- ASSERT --
      assertThat(result).isNotNull();
      assertThat(result.get(0).get("changes").size()).isEqualTo(0);
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
      assertThat(changes).hasSize(2);
      assertThat(changes).allSatisfy(c -> assertThat(c.oldValue()).isNull());
      assertThat(changes)
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
      assertThat(changes).hasSize(2);
      assertThat(changes).allSatisfy(c -> assertThat(c.newValue()).isNull());
      assertThat(changes)
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
      ObjectDiffUtils.FieldChange change = changes.get(0);
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
      assertThat(changes.get(0).field()).isEqualTo("email");
      assertThat(changes.get(0).oldValue()).isNull();
      assertThat(changes.get(0).newValue()).isEqualTo("alice@example.com");
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
      assertThat(changes.get(0).field()).isEqualTo("email");
      assertThat(changes.get(0).oldValue()).isEqualTo("alice@example.com");
      assertThat(changes.get(0).newValue()).isNull();
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
      assertThat(changes.get(0).field()).isEqualTo("roles");
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
      assertThat(norm1).isEqualTo(norm2).isEqualTo("apple,banana,cherry");
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
    @DisplayName("Should normalize a map to key=value pairs sorted by key")
    void given_mapValue_should_returnKeyValuePairsSortedByKey() {
      // -- ARRANGE --
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("z", "last");
      map.put("a", "first");
      map.put("m", "middle");

      // -- ACT --
      String result = ObjectDiffUtils.normalizeForComparison(map);

      // -- ASSERT --
      assertThat(result).isEqualTo("a=first|m=middle|z=last");
    }

    @Test
    @DisplayName(
        "Should return the same normalized string for maps with same entries in different insertion order")
    void given_mapsWithDifferentInsertionOrder_should_returnSameNormalizedString() {
      // -- ARRANGE --
      Map<String, Object> map1 = new LinkedHashMap<>();
      map1.put("b", "2");
      map1.put("a", "1");
      Map<String, Object> map2 = new LinkedHashMap<>();
      map2.put("a", "1");
      map2.put("b", "2");

      // -- ACT --
      String norm1 = ObjectDiffUtils.normalizeForComparison(map1);
      String norm2 = ObjectDiffUtils.normalizeForComparison(map2);

      // -- ASSERT --
      assertThat(norm1).isEqualTo(norm2);
    }

    @Test
    @DisplayName("Should handle nested map values recursively")
    void given_mapWithNestedMapValue_should_normalizeRecursively() {
      // -- ARRANGE --
      Map<String, Object> inner = new LinkedHashMap<>();
      inner.put("x", "10");
      inner.put("y", "20");
      Map<String, Object> outer = new LinkedHashMap<>();
      outer.put("nested", inner);

      // -- ACT --
      String result = ObjectDiffUtils.normalizeForComparison(outer);

      // -- ASSERT --
      assertThat(result).isEqualTo("nested=x=10|y=20");
    }

    @Test
    @DisplayName("Should handle an empty collection")
    void given_emptyCollection_should_returnEmptyString() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.normalizeForComparison(List.of())).isEqualTo("");
    }

    @Test
    @DisplayName("Should handle an empty map")
    void given_emptyMap_should_returnEmptyString() {
      // -- ACT & ASSERT --
      assertThat(ObjectDiffUtils.normalizeForComparison(Map.of())).isEqualTo("");
    }
  }
}
