package io.openaev.service;

import static io.openaev.service.InjectExpectationTraceService.parseMatchType;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.InjectExpectationTrace.LOGGING_MATCH_TYPE;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InjectExpectationTraceService match type")
class InjectExpectationTraceMatchTypeTest {

  @Test
  @DisplayName("given_known_value_should_return_match_type")
  void given_known_value_should_return_match_type() {
    // Act & Assert
    assertThat(parseMatchType("marker")).isEqualTo(LOGGING_MATCH_TYPE.MARKER);
    assertThat(parseMatchType("source_ip")).isEqualTo(LOGGING_MATCH_TYPE.SOURCE_IP);
    assertThat(parseMatchType(" TARGET_IP ")).isEqualTo(LOGGING_MATCH_TYPE.TARGET_IP);
  }

  @Test
  @DisplayName("given_missing_or_unknown_value_should_return_null")
  void given_missing_or_unknown_value_should_return_null() {
    // Act & Assert
    assertThat(parseMatchType(null)).isNull();
    assertThat(parseMatchType(" ")).isNull();
    assertThat(parseMatchType("HOSTNAME")).isNull();
    assertThat(parseMatchType("ip_only")).isNull();
  }
}
