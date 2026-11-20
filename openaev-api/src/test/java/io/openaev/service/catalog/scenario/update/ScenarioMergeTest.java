package io.openaev.service.catalog.scenario.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.service.catalog.scenario.update.ScenarioMerge.Change;
import io.openaev.service.catalog.scenario.update.ScenarioMerge.Choice;
import io.openaev.service.catalog.scenario.update.ScenarioMerge.Result;
import io.openaev.service.catalog.scenario.update.ScenarioMerge.Status;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario merge")
class ScenarioMergeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String INJECT = "inject:a";

  private static ObjectNode content(String json) {
    try {
      return (ObjectNode) MAPPER.readTree(json.replace('\'', '"'));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static ObjectNode inject(String title, String command) {
    return content(
        "{'inject:a': {'title': '" + title + "', 'content.command': '" + command + "'}}");
  }

  private static JsonNode merged(Result result, String field) {
    return result.merged().path(INJECT).path(field);
  }

  @Nested
  @DisplayName("Fields")
  class Fields {

    @Test
    @DisplayName("takes what only the applied content changed")
    void given_onlyAppliedChanged_should_takeIt() {
      // Arrange
      ObjectNode base = inject("Scan", "nmap");

      // Act
      Result result =
          ScenarioMerge.merge(base, inject("Scan", "nmap -sV"), inject("Scan", "nmap"), Map.of());

      // Assert
      assertThat(merged(result, "content.command").asText()).isEqualTo("nmap -sV");
      assertThat(result.changes())
          .extracting(Change::id, Change::status)
          .containsExactly(tuple(INJECT + "/content.command", Status.TAKE_APPLIED));
      assertThat(result.resolved()).isTrue();
    }

    @Test
    @DisplayName("keeps what only the client changed")
    void given_onlyLocalChanged_should_keepIt() {
      // Arrange
      ObjectNode base = inject("Scan", "nmap");

      // Act
      Result result =
          ScenarioMerge.merge(base, inject("Scan", "nmap"), inject("My scan", "nmap"), Map.of());

      // Assert
      assertThat(merged(result, "title").asText()).isEqualTo("My scan");
      assertThat(result.changes())
          .extracting(Change::id, Change::status)
          .containsExactly(tuple(INJECT + "/title", Status.KEEP_MINE));
    }

    @Test
    @DisplayName("leaves a field both changed unresolved until a side is chosen")
    void given_bothChanged_should_waitForAChoice() {
      // Arrange
      ObjectNode base = inject("Scan", "nmap");

      // Act
      Result result =
          ScenarioMerge.merge(
              base, inject("Scan", "nmap -sV"), inject("Scan", "nmap -A"), Map.of());

      // Assert
      assertThat(result.unresolved()).containsExactly(INJECT + "/content.command");
      assertThat(result.changes()).extracting(Change::status).containsExactly(Status.CONFLICT);
    }

    @Test
    @DisplayName("writes the side chosen for a conflict")
    void given_choice_should_writeThatSide() {
      // Arrange
      ObjectNode base = inject("Scan", "nmap");
      ObjectNode applied = inject("Scan", "nmap -sV");
      ObjectNode local = inject("Scan", "nmap -A");

      // Act
      Result taken =
          ScenarioMerge.merge(
              base, applied, local, Map.of(INJECT + "/content.command", Choice.TAKE_APPLIED));
      Result kept =
          ScenarioMerge.merge(
              base, applied, local, Map.of(INJECT + "/content.command", Choice.KEEP_MINE));

      // Assert
      assertThat(taken.resolved()).isTrue();
      assertThat(merged(taken, "content.command").asText()).isEqualTo("nmap -sV");
      assertThat(merged(kept, "content.command").asText()).isEqualTo("nmap -A");
    }

    @Test
    @DisplayName("adds a variable the applied content adds and drops one it removes")
    void given_variablesAddedAndRemoved_should_followTheAppliedContent() {
      // Act
      Result result =
          ScenarioMerge.merge(
              content("{'variables': {'old': '1'}}"),
              content("{'variables': {'new': '2'}}"),
              content("{'variables': {'old': '1'}}"),
              Map.of());

      // Assert
      assertThat(result.merged().path("variables").has("old")).isFalse();
      assertThat(result.merged().path("variables").path("new").asText()).isEqualTo("2");
    }
  }

  @Nested
  @DisplayName("Injects")
  class Injects {

    @Test
    @DisplayName("adds an inject the applied content adds")
    void given_newInject_should_addIt() {
      // Act
      Result result =
          ScenarioMerge.merge(content("{}"), inject("Scan", "nmap"), content("{}"), Map.of());

      // Assert
      assertThat(result.merged().has(INJECT)).isTrue();
      assertThat(result.changes()).extracting(Change::status).containsExactly(Status.ADDED);
    }

    @Test
    @DisplayName("removes an inject the applied content removes and the client left as it was")
    void given_removedUntouchedInject_should_removeIt() {
      // Act
      Result result =
          ScenarioMerge.merge(
              inject("Scan", "nmap"), content("{}"), inject("Scan", "nmap"), Map.of());

      // Assert
      assertThat(result.merged().has(INJECT)).isFalse();
      assertThat(result.changes()).extracting(Change::status).containsExactly(Status.REMOVED);
    }

    @Test
    @DisplayName("asks before removing an inject the client changed")
    void given_removedChangedInject_should_askFirst() {
      // Act
      Result result =
          ScenarioMerge.merge(
              inject("Scan", "nmap"),
              content("{}"),
              inject("Scan", "nmap -A"),
              Map.of(INJECT, Choice.KEEP_MINE));

      // Assert
      assertThat(result.resolved()).isTrue();
      assertThat(merged(result, "content.command").asText()).isEqualTo("nmap -A");
      assertThat(result.changes()).extracting(Change::id).containsExactly(INJECT);
    }

    @Test
    @DisplayName("keeps deleted an inject the client deleted, and keeps the client's own")
    void given_clientDeletedOrAddedInject_should_respectIt() {
      // Act
      Result result =
          ScenarioMerge.merge(
              inject("Scan", "nmap"),
              inject("Scan", "nmap -sV"),
              content("{'inject:mine': {'title': 'Mine'}}"),
              Map.of());

      // Assert
      assertThat(result.merged().has(INJECT)).isFalse();
      assertThat(result.merged().path("inject:mine").path("title").asText()).isEqualTo("Mine");
      assertThat(result.changes()).isEmpty();
    }
  }
}
