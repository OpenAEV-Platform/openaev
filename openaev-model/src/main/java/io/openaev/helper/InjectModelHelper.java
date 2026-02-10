package io.openaev.helper;

import static io.openaev.database.model.Inject.SPEED_STANDARD;
import static io.openaev.database.model.InjectorContract.*;
import static java.time.Duration.between;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Helper class for inject model operations and validation.
 *
 * <p>This utility provides methods for:
 *
 * <ul>
 *   <li>Validating inject readiness based on contract requirements
 *   <li>Computing inject execution dates with pause handling
 *   <li>Extracting field values from inject content
 *   <li>Determining expectation types (detection/prevention)
 * </ul>
 *
 * @see Inject
 * @see InjectorContract
 */
public class InjectModelHelper {

  private InjectModelHelper() {
    // Utility class - prevent instantiation
  }

  private static boolean isTextOrTextarea(JsonNode jsonField) {
    String type = jsonField.get("type").asText();
    return "text".equals(type) || "textarea".equals(type);
  }

  private static boolean isFieldValid(
      ObjectNode content, ArrayNode injectContractFields, String key) {
    JsonNode fieldValue = content.get(key);
    if (fieldValue == null || fieldValue.asText().isEmpty()) {
      for (JsonNode contractField : injectContractFields) {
        if (key.equals(contractField.get(CONTRACT_ELEMENT_CONTENT_KEY).asText())) {
          JsonNode defaultValue = contractField.get(DEFAULT_VALUE_FIELD);
          if (defaultValue == null || defaultValue.isNull() || defaultValue.textValue().isBlank()) {
            return false;
          }
        }
      }
    }

    return true;
  }

  /**
   * Computes the actual execution date for an inject, accounting for speed and pauses.
   *
   * <p>The calculation considers:
   *
   * <ul>
   *   <li>The base execution time from the source timestamp
   *   <li>The inject's dependency duration adjusted by speed multiplier
   *   <li>All completed pause durations from the exercise
   *   <li>Any currently active pause
   *   <li>Alignment to minute boundaries for cleaner scheduling
   * </ul>
   *
   * @param source the base timestamp (typically exercise start time)
   * @param speed the speed multiplier (1 = normal, 2 = double speed, etc.)
   * @param dependsDuration the inject's offset duration from start in seconds
   * @param exercise the exercise containing pause information, or null
   * @return the computed execution timestamp
   */
  public static Instant computeInjectDate(
      Instant source, int speed, Long dependsDuration, Exercise exercise) {
    // Compute origin execution date
    long duration = ofNullable(dependsDuration).orElse(0L) / speed;
    Instant standardExecutionDate = source.plusSeconds(duration);
    // Compute execution dates with previous terminated pauses
    Instant afterPausesExecutionDate = standardExecutionDate;
    List<Pause> sortedPauses = new ArrayList<>();
    if (exercise != null) {
      sortedPauses.addAll(
          exercise.getPauses().stream()
              .sorted(
                  (pause0, pause1) ->
                      pause0.getDate().equals(pause1.getDate())
                          ? 0
                          : pause0.getDate().isBefore(pause1.getDate()) ? -1 : 1)
              .toList());
    }
    long previousPauseDelay = 0L;
    for (Pause pause : sortedPauses) {
      if (pause.getDate().isAfter(afterPausesExecutionDate)) {
        break;
      }
      previousPauseDelay += pause.getDuration().orElse(0L);
      afterPausesExecutionDate = standardExecutionDate.plusSeconds(previousPauseDelay);
    }

    // Add current pause duration in date computation if needed
    long currentPauseDelay = 0;
    Instant finalAfterPausesExecutionDate = afterPausesExecutionDate;
    if (exercise != null) {
      currentPauseDelay =
          exercise
              .getCurrentPause()
              .filter(pauseTime -> pauseTime.isBefore(finalAfterPausesExecutionDate))
              .map(pauseTime -> between(pauseTime, now()).getSeconds())
              .orElse(0L);
    }
    long globalPauseDelay = previousPauseDelay + currentPauseDelay;
    long minuteAlignModulo = globalPauseDelay % 60;
    long alignedPauseDelay =
        minuteAlignModulo > 0 ? globalPauseDelay + (60 - minuteAlignModulo) : globalPauseDelay;
    return standardExecutionDate.plusSeconds(alignedPauseDelay);
  }

  /**
   * Computes the scheduled execution date for an inject.
   *
   * <p>Behavior varies based on context:
   *
   * <ul>
   *   <li>Standalone inject (no exercise/scenario): Returns 30 seconds ago for immediate execution
   *   <li>Scenario inject: Returns empty (scenarios don't have fixed dates)
   *   <li>Exercise inject: Computes date based on exercise start time and inject offset
   *   <li>Cancelled exercise: Returns empty
   * </ul>
   *
   * @param exercise the parent exercise, or null
   * @param scenario the parent scenario, or null
   * @param dependsDuration the inject's offset from exercise start in seconds
   * @return the computed date, or empty if not applicable
   */
  public static Optional<Instant> getDate(
      Exercise exercise, Scenario scenario, Long dependsDuration) {
    if (exercise == null && scenario == null) {
      return Optional.ofNullable(now().minusSeconds(30));
    }

    if (scenario != null) {
      return Optional.empty();
    }

    // At this point exercise cannot be null (if both were null, we returned at first condition;
    // if only scenario was not null, we returned above)
    assert exercise != null;
    if (exercise.getStatus().equals(ExerciseStatus.CANCELED)) {
      return Optional.empty();
    }
    return exercise
        .getStart()
        .map(source -> computeInjectDate(source, SPEED_STANDARD, dependsDuration, exercise));
  }

  /**
   * Extracts the sent timestamp from an inject status.
   *
   * @param status the inject status, or empty if not yet executed
   * @return the timestamp when the inject was sent, or {@code null} if not available
   */
  public static Instant getSentAt(Optional<InjectStatus> status) {
    return status.map(InjectStatus::getTrackingSentDate).orElse(null);
  }

  public static boolean isFieldSet(
      final boolean allTeams,
      @NotNull final List<String> teams,
      @NotNull final List<String> assets,
      @NotNull final List<String> assetGroups,
      @NotNull final JsonNode jsonField,
      @NotNull final ObjectNode content,
      @NotNull final ArrayNode injectContractFields) {
    boolean isSet = true;
    String key = jsonField.get(CONTRACT_ELEMENT_CONTENT_KEY).asText();
    String type = jsonField.get(CONTRACT_ELEMENT_CONTENT_TYPE).asText();
    switch (type) {
      case CONTRACT_ELEMENT_CONTENT_TYPE_TEAM -> {
        if (teams.isEmpty() && !allTeams) {
          isSet = false;
        }
      }
      case CONTRACT_ELEMENT_CONTENT_TYPE_ASSET -> {
        if (assets.isEmpty()) {
          isSet = false;
        }
      }
      case CONTRACT_ELEMENT_CONTENT_TYPE_ASSET_GROUP -> {
        if (assetGroups.isEmpty()) {
          isSet = false;
        }
      }
      default -> {
        if (content == null) {
          isSet = false;
          break;
        }
        if (isTextOrTextarea(jsonField)) {
          isSet = isFieldValid(content, injectContractFields, key);
        } else if (content.get(key) == null
            || (content.get(key).isArray() && content.get(key).isEmpty())
            || (content.get(key).isObject() && content.get(key).isEmpty())
            || (content.get(key).isTextual() && content.get(key).asText().isEmpty())) {
          isSet = false;
        }
      }
    }
    return isSet;
  }

  /**
   * Checks if the inject content includes detection or prevention expectations.
   *
   * <p>This is used to determine if an inject requires security tool validation (e.g., EDR
   * detection, firewall blocking) rather than just execution completion.
   *
   * @param content the inject content JSON
   * @return {@code true} if the inject has DETECTION or PREVENTION expectations
   */
  public static boolean isDetectionOrPrevention(final ObjectNode content) {
    if (content == null
        || content.get("expectations") == null
        || content.get("expectations").isNull()) {
      return false;
    }

    JsonNode valueNode = content.get("expectations");

    if (valueNode.isArray()) {
      for (JsonNode node : valueNode) {
        if (!node.isNull()
            && node.get("expectation_type") != null
            && !node.get("expectation_type").isNull()) {
          InjectExpectation.EXPECTATION_TYPE type =
              InjectExpectation.EXPECTATION_TYPE.valueOf(node.get("expectation_type").asText());
          if (InjectExpectation.EXPECTATION_TYPE.DETECTION.equals(type)
              || InjectExpectation.EXPECTATION_TYPE.PREVENTION.equals(type)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /**
   * Extracts the value(s) for a specific field from inject content.
   *
   * <p>For target-type fields (team, asset, asset_group), returns the corresponding ID list. For
   * other field types, extracts the value from the content JSON.
   *
   * @param teams list of targeted team IDs
   * @param assets list of targeted asset IDs
   * @param assetGroups list of targeted asset group IDs
   * @param jsonField the field definition from the contract
   * @param content the inject content JSON
   * @return the field value(s) as a list of strings, or empty list if not set
   */
  public static List<String> getFieldValue(
      @NotNull final List<String> teams,
      @NotNull final List<String> assets,
      @NotNull final List<String> assetGroups,
      @NotNull final JsonNode jsonField,
      @NotNull final ObjectNode content) {

    String key = jsonField.get(CONTRACT_ELEMENT_CONTENT_KEY).asText();
    String type = jsonField.get(CONTRACT_ELEMENT_CONTENT_TYPE).asText();

    return switch (type) {
      case CONTRACT_ELEMENT_CONTENT_TYPE_TEAM -> teams;
      case CONTRACT_ELEMENT_CONTENT_TYPE_ASSET -> assets;
      case CONTRACT_ELEMENT_CONTENT_TYPE_ASSET_GROUP -> assetGroups;
      default -> {
        if (content == null || content.get(key) == null || content.get(key).isNull()) {
          yield List.of();
        }

        JsonNode valueNode = content.get(key);

        if (valueNode.isArray()) {
          List<String> values = new ArrayList<>();
          for (JsonNode node : valueNode) {
            if (!node.isNull()) {
              values.add(node.asText());
            }
          }
          yield values;
        }

        if (valueNode.isTextual()) {
          String value = valueNode.asText();
          yield value.isEmpty() ? List.of() : List.of(value);
        } else if (valueNode.isBoolean()) {
          String value = valueNode.asText();
          yield value.isEmpty() ? List.of() : List.of(value);
        }

        yield List.of();
      }
    };
  }
}
