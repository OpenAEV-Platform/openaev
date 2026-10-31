package io.openaev.rest.collector.service;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.DETECTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.LOGGING;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY;
import static io.openaev.rest.collector.service.CollectorService.parseSupportedExpectationTypes;
import static io.openaev.service.InjectExpectationUtils.filterCollectorsSupportingType;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Collector;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Collector supported expectation types")
class CollectorSupportedExpectationTypesTest {

  private static final String COLLECTOR_ID = "collector-id";

  private static Collector collectorDeclaring(EXPECTATION_TYPE... types) {
    Collector collector = new Collector();
    collector.setId(COLLECTOR_ID);
    collector.setSupportedExpectationTypes(types);
    return collector;
  }

  @Nested
  @DisplayName("parseSupportedExpectationTypes")
  class Parse {

    @Test
    @DisplayName("given_no_declaration_should_return_null")
    void given_no_declaration_should_return_null() {
      // Act
      EXPECTATION_TYPE[] types = parseSupportedExpectationTypes(COLLECTOR_ID, null);

      // Assert
      assertThat(types).isNull();
    }

    @Test
    @DisplayName("given_unknown_and_duplicated_values_should_keep_known_types_once")
    void given_unknown_and_duplicated_values_should_keep_known_types_once() {
      // Act
      EXPECTATION_TYPE[] types =
          parseSupportedExpectationTypes(
              COLLECTOR_ID,
              Arrays.asList("detection", " LOGGING ", "FUTURE_TYPE", null, "LOGGING"));

      // Assert
      assertThat(types).containsExactly(DETECTION, LOGGING);
    }

    @Test
    @DisplayName("given_only_unknown_values_should_return_null")
    void given_only_unknown_values_should_return_null() {
      // Act
      EXPECTATION_TYPE[] types =
          parseSupportedExpectationTypes(COLLECTOR_ID, List.of("FUTURE_TYPE"));

      // Assert
      assertThat(types).isNull();
    }
  }

  @Nested
  @DisplayName("supportsExpectationType")
  class Supports {

    @Test
    @DisplayName("given_no_declaration_should_support_detection_and_prevention_only")
    void given_no_declaration_should_support_detection_and_prevention_only() {
      // Arrange
      Collector collector = collectorDeclaring((EXPECTATION_TYPE[]) null);

      // Act & Assert
      assertThat(collector.supportsExpectationType(DETECTION)).isTrue();
      assertThat(collector.supportsExpectationType(PREVENTION)).isTrue();
      assertThat(collector.supportsExpectationType(LOGGING)).isFalse();
      assertThat(collector.supportsExpectationType(VULNERABILITY)).isFalse();
    }

    @Test
    @DisplayName("given_declaration_should_support_declared_types_only")
    void given_declaration_should_support_declared_types_only() {
      // Arrange
      Collector collector = collectorDeclaring(LOGGING);

      // Act & Assert
      assertThat(collector.supportsExpectationType(LOGGING)).isTrue();
      assertThat(collector.supportsExpectationType(DETECTION)).isFalse();
    }
  }

  @Nested
  @DisplayName("filterCollectorsSupportingType")
  class Filter {

    @Test
    @DisplayName("given_logging_should_keep_only_collectors_declaring_it")
    void given_logging_should_keep_only_collectors_declaring_it() {
      // Arrange
      Collector legacy = collectorDeclaring((EXPECTATION_TYPE[]) null);
      Collector logging = collectorDeclaring(DETECTION, PREVENTION, LOGGING);

      // Act
      List<Collector> collectors =
          filterCollectorsSupportingType(List.of(legacy, logging), LOGGING);

      // Assert
      assertThat(collectors).containsExactly(logging);
    }

    @Test
    @DisplayName("given_detection_should_keep_legacy_and_declaring_collectors")
    void given_detection_should_keep_legacy_and_declaring_collectors() {
      // Arrange
      Collector legacy = collectorDeclaring((EXPECTATION_TYPE[]) null);
      Collector loggingOnly = collectorDeclaring(LOGGING);

      // Act
      List<Collector> collectors =
          filterCollectorsSupportingType(List.of(legacy, loggingOnly), DETECTION);

      // Assert
      assertThat(collectors).containsExactly(legacy);
    }
  }
}
