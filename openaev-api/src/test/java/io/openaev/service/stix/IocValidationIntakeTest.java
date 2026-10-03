package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.IocValidationStatus;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("IOC validation request intake")
class IocValidationIntakeTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "AWAITING_APPROVAL, recorded, awaiting approval",
    "RUNNING, already recorded, is running",
    "COMPLETED, already recorded, is completed",
    "PARTIAL, already recorded, is partially completed",
    "FAILED, already recorded, failed",
    "REJECTED, already recorded, was rejected"
  })
  @DisplayName("the OpenCTI acknowledgement of a replay states the actual validation status")
  void given_validationStatus_should_acknowledgeIt(
      IocValidationStatus status, String recorded, String state) {
    assertThat(IocValidationService.intakeAcknowledgement(status))
        .startsWith("IOC validation request " + recorded)
        .contains(state)
        .endsWith("OpenAEV");
  }

  @Test
  @DisplayName("every status has its own acknowledgement")
  void given_everyStatus_should_haveADistinctAcknowledgement() {
    assertThat(
            Arrays.stream(IocValidationStatus.values())
                .map(IocValidationService::intakeAcknowledgement)
                .distinct())
        .hasSize(IocValidationStatus.values().length);
  }

  @Test
  @DisplayName("the intake lock key is stable per tenant and request, and differs across them")
  void given_tenantAndRequest_should_deriveAStableLockKey() {
    long key = IocValidationService.intakeLockKey("tenant-a", "request-1");

    assertThat(IocValidationService.intakeLockKey("tenant-a", "request-1")).isEqualTo(key);
    assertThat(IocValidationService.intakeLockKey("tenant-b", "request-1")).isNotEqualTo(key);
    assertThat(IocValidationService.intakeLockKey("tenant-a", "request-2")).isNotEqualTo(key);
  }
}
