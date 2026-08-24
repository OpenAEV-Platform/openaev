package io.openaev.utils.log.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.AuditLogProperties;
import io.openaev.config.EngineConfig;
import io.openaev.database.model.LogTransport;
import io.openaev.engine.EngineService;
import io.openaev.engine.model.log.LogEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEngineLogTransport")
class AuditEngineLogTransportTest {

  @Mock private AuditLogProperties auditLogProperties;
  @Mock private EngineService engineService;

  private AuditEngineLogTransport transport;

  @BeforeEach
  void setUp() {
    EngineConfig engineConfig = new EngineConfig();
    engineConfig.setIndexPrefix("openaev");
    transport = new AuditEngineLogTransport(auditLogProperties, engineService, engineConfig);
  }

  @Test
  @DisplayName("given_engineTransportEnabled_should_returnTrue")
  void given_engineTransportEnabled_should_returnTrue() {
    // Arrange
    when(auditLogProperties.isTransportEnabled(LogTransport.ENGINE)).thenReturn(true);

    // Act
    boolean enabled = transport.isEnabled();

    // Assert
    assertThat(enabled).isTrue();
  }

  @Test
  @DisplayName("given_validLogEvent_should_indexInAuditAlias")
  void given_validLogEvent_should_indexInAuditAlias() throws IOException {
    // Arrange
    LogEvent event = new LogEvent();
    event.setId("event-1");

    // Act
    boolean indexed = transport.send(event, null);

    // Assert
    assertThat(indexed).isTrue();
    verify(engineService).indexDocument("openaev_audit-log", "event-1", event);
  }

  @Test
  @DisplayName("given_stringMessage_should_indexStructuredAuditDocument")
  void given_stringMessage_should_indexStructuredAuditDocument() throws IOException {
    // Arrange
    ArgumentCaptor<Object> documentCaptor = ArgumentCaptor.forClass(Object.class);

    // Act
    boolean indexed = transport.send("test message", null);

    // Assert
    assertThat(indexed).isTrue();
    verify(engineService)
        .indexDocument(
            org.mockito.ArgumentMatchers.eq("openaev_audit-log"),
            org.mockito.ArgumentMatchers.anyString(),
            documentCaptor.capture());
    assertThat(documentCaptor.getValue()).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> document = (Map<String, Object>) documentCaptor.getValue();
    assertThat(document)
        .containsEntry("entity_type", "Activity")
        .containsEntry("event_scope", "log")
        .containsEntry("event_status", "success")
        .containsEntry("message", "test message");
    assertThat(document.get("created_at")).isInstanceOf(Instant.class);
    assertThat(document.get("timestamp")).isInstanceOf(Instant.class);
  }
}
