package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationOutcome;
import io.openaev.database.model.IocValidationPair;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IOC validation results message sent to OpenCTI")
class IocValidationResultsMessageTest {

  private static IocValidationPair pair(IocValidationOutcome outcome, String reason) {
    IocValidationPair pair = new IocValidationPair();
    pair.setOutcome(outcome);
    pair.setOutcomeReason(reason);
    return pair;
  }

  private static IocValidation validation(List<IocValidationPair> pairs) {
    IocValidation validation = new IocValidation();
    validation.setPairs(new ArrayList<>(pairs));
    validation.refreshCounters();
    return validation;
  }

  @Test
  @DisplayName("names the distinct reasons of the error outcomes after the counts")
  void given_errorOutcomes_should_nameTheirReasons() {
    IocValidation validation =
        validation(
            List.of(
                pair(IocValidationOutcome.DETECTED, null),
                pair(IocValidationOutcome.ERROR, "No collector of the platform answered in time"),
                pair(IocValidationOutcome.ERROR, "No collector of the platform answered in time"),
                pair(IocValidationOutcome.ERROR, "The agent of the endpoint is not active"),
                pair(IocValidationOutcome.ERROR, " ")));

    assertThat(IocValidationService.resultsMessage(validation))
        .isEqualTo(
            "Prevented 0, detected 1, missed 0, error 4 (of 5 indicator-platform pairs). Errors:"
                + " No collector of the platform answered in time; The agent of the endpoint is"
                + " not active");
  }

  @Test
  @DisplayName("gives the counts only when no error outcome has a reason")
  void given_noErrorReason_should_giveTheCountsOnly() {
    IocValidation validation =
        validation(
            List.of(
                pair(IocValidationOutcome.MISSED, "not an error"),
                pair(IocValidationOutcome.ERROR, null)));

    assertThat(IocValidationService.resultsMessage(validation))
        .isEqualTo("Prevented 0, detected 0, missed 1, error 1 (of 2 indicator-platform pairs)");
  }

  @Test
  @DisplayName("keeps the message bounded: a few reasons, each shortened")
  void given_manyLongReasons_should_boundTheMessage() {
    IocValidation validation =
        validation(
            IntStream.range(0, 20)
                .mapToObj(i -> pair(IocValidationOutcome.ERROR, i + "x".repeat(1000)))
                .toList());

    String message = IocValidationService.resultsMessage(validation);

    String reasons = message.substring(message.indexOf("Errors: ") + "Errors: ".length());
    assertThat(reasons.split("; "))
        .hasSize(IocValidationService.MAX_ERROR_REASONS_IN_MESSAGE)
        .allSatisfy(
            reason -> assertThat(reason).hasSize(IocValidationService.MAX_ERROR_REASON_LENGTH));
    assertThat(message.length()).isLessThan(5000);
  }
}
