package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WorkflowStateEntries — generateCorrelatedForRecipe")
class WorkflowStateEntriesCorrelationTest {

  private static final List<String> CREDENTIALS_RECIPE = List.of("Username", "Password");

  private WorkflowStateEntries createEntries() {
    return new WorkflowStateEntries(
        new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
  }

  /**
   * Convenience: adds values under a given key without triggering correlation, simulating
   * previously recorded scalars.
   */
  private void seedInput(WorkflowStateEntries entries, String key, String... values) {
    WorkflowStateEntries.Input input = entries.getInputByKey(key);
    input.getValues().addAll(Arrays.asList(values));
  }

  private Set<Set<WorkflowStateEntries.Pair>> allCorrelatedPairSets(WorkflowStateEntries entries) {
    Set<Set<WorkflowStateEntries.Pair>> result = new HashSet<>();
    for (WorkflowStateEntries.Correlated c : entries.getCorrelated()) {
      result.add(c.getValues());
    }
    return result;
  }

  @Nested
  @DisplayName("Basic cartesian generation")
  class BasicCartesian {

    @Test
    @DisplayName("2 usernames + 1 password → 2 Correlated")
    void given_twoUsernamesOnePassword_should_generateTwoCorrelated() {
      WorkflowStateEntries entries = createEntries();

      // Simulate: u1 arrives, no password yet → nothing
      seedInput(entries, "Username", "u1");
      entries.generateCorrelatedForRecipe("Username", "u1", CREDENTIALS_RECIPE, "Credentials");
      assertThat(entries.getCorrelated()).isEmpty();

      // Simulate: u2 arrives, still no password → nothing
      entries.getInputByKey("Username").getValues().add("u2");
      entries.generateCorrelatedForRecipe("Username", "u2", CREDENTIALS_RECIPE, "Credentials");
      assertThat(entries.getCorrelated()).isEmpty();

      // Simulate: p1 arrives → 2 Correlated (u1×p1, u2×p1)
      seedInput(entries, "Password", "p1");
      entries.generateCorrelatedForRecipe("Password", "p1", CREDENTIALS_RECIPE, "Credentials");

      assertThat(entries.getCorrelated()).hasSize(2);
      Set<Set<WorkflowStateEntries.Pair>> pairSets = allCorrelatedPairSets(entries);
      assertThat(pairSets)
          .containsExactlyInAnyOrder(
              Set.of(
                  new WorkflowStateEntries.Pair("Username", "u1"),
                  new WorkflowStateEntries.Pair("Password", "p1")),
              Set.of(
                  new WorkflowStateEntries.Pair("Username", "u2"),
                  new WorkflowStateEntries.Pair("Password", "p1")));
    }

    @Test
    @DisplayName("2 usernames + 2 passwords → 4 Correlated total (incremental)")
    void given_twoUsernamesTwoPasswords_should_generateFourCorrelatedIncrementally() {
      WorkflowStateEntries entries = createEntries();
      seedInput(entries, "Username", "u1", "u2");

      // p1 arrives → 2 Correlated
      seedInput(entries, "Password", "p1");
      entries.generateCorrelatedForRecipe("Password", "p1", CREDENTIALS_RECIPE, "Credentials");
      assertThat(entries.getCorrelated()).hasSize(2);

      // p2 arrives → +2 Correlated = 4 total
      entries.getInputByKey("Password").getValues().add("p2");
      entries.generateCorrelatedForRecipe("Password", "p2", CREDENTIALS_RECIPE, "Credentials");
      assertThat(entries.getCorrelated()).hasSize(4);

      Set<Set<WorkflowStateEntries.Pair>> pairSets = allCorrelatedPairSets(entries);
      assertThat(pairSets)
          .containsExactlyInAnyOrder(
              Set.of(
                  new WorkflowStateEntries.Pair("Username", "u1"),
                  new WorkflowStateEntries.Pair("Password", "p1")),
              Set.of(
                  new WorkflowStateEntries.Pair("Username", "u2"),
                  new WorkflowStateEntries.Pair("Password", "p1")),
              Set.of(
                  new WorkflowStateEntries.Pair("Username", "u1"),
                  new WorkflowStateEntries.Pair("Password", "p2")),
              Set.of(
                  new WorkflowStateEntries.Pair("Username", "u2"),
                  new WorkflowStateEntries.Pair("Password", "p2")));
    }
  }

  @Nested
  @DisplayName("Deduplication")
  class Deduplication {

    @Test
    @DisplayName("Re-receiving same value does not duplicate Correlated")
    void given_duplicateValue_should_notDuplicateCorrelated() {
      WorkflowStateEntries entries = createEntries();
      seedInput(entries, "Username", "u1");
      seedInput(entries, "Password", "p1");

      // First generation
      entries.generateCorrelatedForRecipe("Password", "p1", CREDENTIALS_RECIPE, "Credentials");
      assertThat(entries.getCorrelated()).hasSize(1);

      // Re-trigger with same value — no new Correlated
      entries.generateCorrelatedForRecipe("Password", "p1", CREDENTIALS_RECIPE, "Credentials");
      assertThat(entries.getCorrelated()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Recipe boundary")
  class RecipeBoundary {

    @Test
    @DisplayName("Unrelated primitive type not mixed into Credentials recipe")
    void given_unrelatedKey_should_notGenerateCorrelated() {
      WorkflowStateEntries entries = createEntries();
      seedInput(entries, "Username", "u1");
      seedInput(entries, "Password", "p1");
      seedInput(entries, "Port", "22");

      // Port is NOT in the Credentials recipe → no correlation triggered
      entries.generateCorrelatedForRecipe("Port", "22", CREDENTIALS_RECIPE, "Credentials");
      assertThat(entries.getCorrelated()).isEmpty();
    }

    @Test
    @DisplayName("Credentials recipe does not include Port values in Correlated pairs")
    void given_portInInputs_should_notAppearInCredentialsCorrelated() {
      WorkflowStateEntries entries = createEntries();
      seedInput(entries, "Username", "u1");
      seedInput(entries, "Password", "p1");
      seedInput(entries, "Port", "22");

      entries.generateCorrelatedForRecipe("Username", "u1", CREDENTIALS_RECIPE, "Credentials");

      assertThat(entries.getCorrelated()).hasSize(1);
      Set<WorkflowStateEntries.Pair> pairs = entries.getCorrelated().getFirst().getValues();
      // Only Username + Password, no Port
      assertThat(pairs)
          .containsExactlyInAnyOrder(
              new WorkflowStateEntries.Pair("Username", "u1"),
              new WorkflowStateEntries.Pair("Password", "p1"));
    }
  }

  @Nested
  @DisplayName("Unsatisfiable recipe")
  class UnsatisfiableRecipe {

    @Test
    @DisplayName("Missing recipe peer → nothing generated")
    void given_missingPassword_should_generateNothing() {
      WorkflowStateEntries entries = createEntries();
      seedInput(entries, "Username", "u1");

      entries.generateCorrelatedForRecipe("Username", "u1", CREDENTIALS_RECIPE, "Credentials");

      assertThat(entries.getCorrelated()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Type stamping and Gson round-trip")
  class TypeStamping {

    @Test
    @DisplayName("Correlated from scalar recipe carries the business type name")
    void given_credentialsRecipe_should_stampType() {
      WorkflowStateEntries entries = createEntries();
      seedInput(entries, "Username", "u1");
      seedInput(entries, "Password", "p1");

      entries.generateCorrelatedForRecipe("Password", "p1", CREDENTIALS_RECIPE, "Credentials");

      assertThat(entries.getCorrelated()).hasSize(1);
      assertThat(entries.getCorrelated().getFirst().getType()).isEqualTo("Credentials");
    }

    @Test
    @DisplayName("Gson round-trip preserves the type field on Correlated")
    void given_serializedEntries_should_preserveTypeOnRoundTrip() {
      Gson gson = new Gson();
      WorkflowStateEntries entries = createEntries();
      seedInput(entries, "Username", "u1");
      seedInput(entries, "Password", "p1");

      entries.generateCorrelatedForRecipe("Password", "p1", CREDENTIALS_RECIPE, "Credentials");
      assertThat(entries.getCorrelated()).hasSize(1);

      // Serialize and deserialize
      String json = gson.toJson(entries);
      WorkflowStateEntries deserialized = gson.fromJson(json, WorkflowStateEntries.class);

      assertThat(deserialized.getCorrelated()).hasSize(1);
      assertThat(deserialized.getCorrelated().getFirst().getType()).isEqualTo("Credentials");
      assertThat(deserialized.getCorrelated().getFirst().getValues())
          .containsExactlyInAnyOrder(
              new WorkflowStateEntries.Pair("Username", "u1"),
              new WorkflowStateEntries.Pair("Password", "p1"));
    }
  }
}
