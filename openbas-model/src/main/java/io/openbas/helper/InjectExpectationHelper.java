package io.openbas.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.InjectExpectation;
import io.openbas.database.model.InjectExpectation.EXPECTATION_STATUS;
import io.openbas.database.raw.RawInjectExpectation;
import org.jetbrains.annotations.NotNull;

public class InjectExpectationHelper {

  private InjectExpectationHelper() {}

  public static EXPECTATION_STATUS computeStatus(
      @NotNull final InjectExpectation injectExpectation) {
    if (injectExpectation.getScore() == null) {
      return EXPECTATION_STATUS.PENDING;
    }
    if (injectExpectation.getTeam() != null) {
      // Only 1 result possible when expectation team
      return computeStatusForTeam(injectExpectation.getResults().getFirst().getResult());
    }

    if (injectExpectation.getScore() >= injectExpectation.getExpectedScore()) {
      return EXPECTATION_STATUS.SUCCESS;
    }
    if (0.0 == injectExpectation.getScore()) {
      return EXPECTATION_STATUS.FAILED;
    }
    return EXPECTATION_STATUS.PARTIAL;
  }

  public static EXPECTATION_STATUS computeStatusForIndexing(
      @NotNull final RawInjectExpectation injectExpectation) {
    if (injectExpectation.getInject_expectation_score() == null) {
      return EXPECTATION_STATUS.PENDING;
    }
    if (injectExpectation.getTeam_id() != null) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        JsonNode node = mapper.readTree(injectExpectation.getInject_expectation_results());
        // Only 1 result possible when expectation team
        return computeStatusForTeam(node.get(0).get("result").asText());
      } catch (JsonProcessingException e) {
        return EXPECTATION_STATUS.PENDING;
      }
    }

    if (injectExpectation.getInject_expectation_score()
        >= injectExpectation.getInject_expectation_expected_score()) {
      return EXPECTATION_STATUS.SUCCESS;
    }
    if (0.0 == injectExpectation.getInject_expectation_score()) {
      return EXPECTATION_STATUS.FAILED;
    }
    return EXPECTATION_STATUS.PARTIAL;
  }

  public static EXPECTATION_STATUS computeStatusForTeam(final String result) {
    if (result == null) {
      return EXPECTATION_STATUS.PENDING;
    }
    return switch (result.toUpperCase()) {
      case "FAILED" -> EXPECTATION_STATUS.FAILED;
      case "SUCCESS" -> EXPECTATION_STATUS.SUCCESS;
      case "PARTIAL" -> EXPECTATION_STATUS.PARTIAL;
      case "UNKNOWN" -> EXPECTATION_STATUS.UNKNOWN;
      default -> EXPECTATION_STATUS.PENDING;
    };
  }
}
