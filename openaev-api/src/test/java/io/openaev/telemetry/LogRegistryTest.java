package io.openaev.telemetry;

import static io.openaev.database.model.UserEventType.USER_CREATED;
import static io.openaev.telemetry.TelemetryAttributes.CREATED_AT;
import static io.openaev.telemetry.TelemetryAttributes.EVENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.telemetry.registry.LogRegistry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LogRegistry tests")
class LogRegistryTest {

  private InMemoryLogRecordExporter logExporter;
  private SdkLoggerProvider loggerProvider;
  private LogRegistry logRegistry;

  @BeforeEach
  void setUp() {
    logExporter = InMemoryLogRecordExporter.create();
    loggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
            .build();
    Logger logger = loggerProvider.loggerBuilder("test-logger").build();
    logRegistry = new LogRegistry(logger);
  }

  @AfterEach
  void tearDown() {
    loggerProvider.shutdown();
  }

  @Test
  @DisplayName("emit should produce a log record with body and structured attributes")
  void should_produce_log_record_with_body_and_attributes() {
    // -- ACT --
    logRegistry.emit(
        "user-registered",
        Attributes.of(EVENT_TYPE, USER_CREATED.name(), CREATED_AT, "2026-02-09T10:30:00Z"));

    // -- ASSERT --
    LogRecordData logRecord = logExporter.getFinishedLogRecordItems().getFirst();
    assertThat(logRecord.getSeverity()).isEqualTo(Severity.INFO);
    assertThat(logRecord.getBodyValue().asString()).isEqualTo("user-registered");
    assertThat(logRecord.getAttributes().get(EVENT_TYPE)).isEqualTo(USER_CREATED.name());
    assertThat(logRecord.getAttributes().get(CREATED_AT)).isEqualTo("2026-02-09T10:30:00Z");
  }
}
