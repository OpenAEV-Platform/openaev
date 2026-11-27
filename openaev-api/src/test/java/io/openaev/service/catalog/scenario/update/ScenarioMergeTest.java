package io.openaev.service.catalog.scenario.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.service.catalog.merge.MergeChoice;
import io.openaev.service.catalog.merge.MergeStatus;
import io.openaev.service.catalog.scenario.update.ScenarioMerge.Change;
import io.openaev.service.catalog.scenario.update.ScenarioMerge.Result;
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
          .containsExactly(tuple(INJECT + "/content.command", MergeStatus.TAKE_APPLIED));
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
          .containsExactly(tuple(INJECT + "/title", MergeStatus.KEEP_MINE));
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
      assertThat(result.changes()).extracting(Change::status).containsExactly(MergeStatus.CONFLICT);
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
              base, applied, local, Map.of(INJECT + "/content.command", MergeChoice.TAKE_APPLIED));
      Result kept =
          ScenarioMerge.merge(
              base, applied, local, Map.of(INJECT + "/content.command", MergeChoice.KEEP_MINE));

      // Assert
      assertThat(taken.resolved()).isTrue();
      assertThat(merged(taken, "content.command").asText()).isEqualTo("nmap -sV");
      assertThat(merged(kept, "content.command").asText()).isEqualTo("nmap -A");
    }

    @Test
    @DisplayName("writes the value typed for a text conflict, never for a payload")
    void given_editedConflict_should_writeTheTypedTextOnly() {
      // Arrange
      ObjectNode base =
          content("{'inject:a': {'title': 'Scan', 'contract': {'payload_version': '1'}}}");
      ObjectNode applied =
          content("{'inject:a': {'title': 'Scan v2', 'contract': {'payload_version': '2'}}}");
      ObjectNode local =
          content("{'inject:a': {'title': 'My scan', 'contract': {'payload_version': '3'}}}");
      Map<String, MergeChoice> edit =
          Map.of(INJECT + "/title", MergeChoice.EDIT, INJECT + "/contract", MergeChoice.EDIT);
      Map<String, String> typed = Map.of(INJECT + "/title", "Our scan", INJECT + "/contract", "{}");

      // Act
      Result result = ScenarioMerge.merge(base, applied, local, edit, typed);

      // Assert
      assertThat(merged(result, "title").asText()).isEqualTo("Our scan");
      assertThat(result.unresolved()).containsExactly(INJECT + "/contract");
      assertThat(result.changes())
          .extracting(Change::field, Change::editable)
          .containsExactly(tuple("title", true), tuple("contract", false));
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
      assertThat(result.changes()).extracting(Change::status).containsExactly(MergeStatus.ADDED);
    }

    @Test
    @DisplayName("asks which side to keep for an inject both added differently")
    void given_injectBothAddedDifferently_should_askFirst() {
      // Act
      Result result =
          ScenarioMerge.merge(
              content("{}"), inject("Scan", "nmap -sV"), inject("Scan", "nmap"), Map.of());

      // Assert
      assertThat(result.unresolved()).containsExactly(INJECT + "/content.command");
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
      assertThat(result.changes()).extracting(Change::status).containsExactly(MergeStatus.REMOVED);
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
              Map.of(INJECT, MergeChoice.KEEP_MINE));

      // Assert
      assertThat(result.resolved()).isTrue();
      assertThat(merged(result, "content.command").asText()).isEqualTo("nmap -A");
      assertThat(result.changes()).extracting(Change::id).containsExactly(INJECT);
    }

    @Test
    @DisplayName("names the injects that lose a dependency on a removed inject")
    void given_removedParent_should_nameTheChildLosingIt() {
      // Arrange
      ObjectNode content =
          content(
              "{'inject:a': {'title': 'Warn'},"
                  + " 'inject:b': {'title': 'Scan', 'depends_on': [{'parent': 'a'}]}}");
      ObjectNode withoutParent =
          content("{'inject:b': {'title': 'Scan', 'depends_on': [{'parent': 'a'}]}}");

      // Act
      Result result = ScenarioMerge.merge(content, withoutParent, content, Map.of());

      // Assert
      assertThat(result.lostDependencies()).containsExactly("Scan");
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
