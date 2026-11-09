package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.TargetType;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The execution-trace endpoints refuse a target type before the by-id guard, and the lookup
 * dispatches on it: both follow {@link TargetType#hasExecutionTraces()}, so a type added to that
 * predicate must have a lookup, and one left out must be refused by both.
 */
@Transactional
@DisplayName("The execution trace target types of the guard and of the lookup are the same")
class InjectExecutionTraceTargetTypeTest extends IntegrationTest {

  @Autowired private InjectService injectService;

  @ParameterizedTest(name = "{0}")
  @EnumSource(TargetType.class)
  @DisplayName(
      "given a target type, should accept or refuse it the same way in the guard and the lookup")
  void given_targetType_should_beAcceptedOrRefusedTheSameWayInGuardAndLookup(
      TargetType targetType) {
    // Arrange
    boolean supported = targetType.hasExecutionTraces();

    // Act
    var guard =
        assertThatCode(() -> injectService.requireSupportedExecutionTraceTargetType(targetType));
    var lookup =
        assertThatCode(
            () ->
                injectService.getInjectTracesFromInjectAndTarget(
                    "no-inject", "no-target", targetType));

    // Assert
    if (supported) {
      guard.doesNotThrowAnyException();
      lookup.doesNotThrowAnyException();
    } else {
      guard.isInstanceOf(BadRequestException.class);
      lookup.isInstanceOf(BadRequestException.class);
    }
  }

  @Test
  @DisplayName("given the enum, should support the target types the trace endpoints document")
  void given_enum_should_supportTheDocumentedTargetTypes() {
    // Assert
    assertThat(Arrays.stream(TargetType.values()).filter(TargetType::hasExecutionTraces))
        .containsExactlyInAnyOrder(
            TargetType.AGENT,
            TargetType.ASSETS,
            TargetType.AI_TARGETS,
            TargetType.TEAMS,
            TargetType.PLAYERS,
            TargetType.MANUAL);
    assertThatThrownBy(
            () -> injectService.requireSupportedExecutionTraceTargetType(TargetType.AGENTS))
        .isInstanceOf(BadRequestException.class);
  }
}
