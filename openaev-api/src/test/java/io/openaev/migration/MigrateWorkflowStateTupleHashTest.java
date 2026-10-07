package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.WorkflowStateEntries.Pair;
import io.openaev.service.chaining.ChainingHashUtils;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The tuple hash frozen in {@link
 * V6_20261007140000000__Migrate_workflow_state_to_normalized_entries} must stay identical to {@link
 * ChainingHashUtils#hashTuple}: tuples converted at deployment and tuples written at runtime are
 * deduplicated against each other by this hash.
 */
@DisplayName("Migration tuple hash")
class MigrateWorkflowStateTupleHashTest {

  static List<List<Pair>> tuples() {
    return List.of(
        List.of(new Pair("IPv4", "10.0.0.1"), new Pair("Port", "22")),
        List.of(new Pair("Port", "22"), new Pair("IPv4", "10.0.0.1")),
        List.of(new Pair("IPv4", "10.0.0.1"), new Pair("IPv4", "10.0.0.2")),
        List.of(new Pair("IPv4", "x|Port=y"), new Pair("Text", "z")),
        List.of(new Pair("Username", "admin"), new Pair("Password", "p@ss:3:wörd|=")),
        List.of(new Pair("Text", "")));
  }

  @Nested
  @DisplayName("hashTuple")
  class HashTuple {

    @ParameterizedTest
    @MethodSource("io.openaev.migration.MigrateWorkflowStateTupleHashTest#tuples")
    @DisplayName("the frozen migration copy matches the runtime hash")
    void given_tuple_should_hashLikeTheRuntime(List<Pair> tuple) {
      // Arrange
      List<String[]> migrationPairs =
          tuple.stream().map(pair -> new String[] {pair.key(), pair.value()}).toList();

      // Act
      String migrationHash =
          V6_20261007140000000__Migrate_workflow_state_to_normalized_entries.hashTuple(
              migrationPairs);

      // Assert
      assertThat(migrationHash).isEqualTo(ChainingHashUtils.hashTuple(tuple));
    }

    @Test
    @DisplayName("values containing separators cannot make two tuples share a hash")
    void given_valueForgingAFieldBoundary_should_produceDifferentHashes() {
      // Arrange
      List<String[]> forged =
          List.of(new String[] {"IPv4", "x|Port=y"}, new String[] {"Text", "z"});
      List<String[]> genuine =
          List.of(
              new String[] {"IPv4", "x"}, new String[] {"Port", "y"}, new String[] {"Text", "z"});

      // Act + Assert
      assertThat(
              V6_20261007140000000__Migrate_workflow_state_to_normalized_entries.hashTuple(forged))
          .isNotEqualTo(
              V6_20261007140000000__Migrate_workflow_state_to_normalized_entries.hashTuple(
                  genuine));
    }
  }
}
