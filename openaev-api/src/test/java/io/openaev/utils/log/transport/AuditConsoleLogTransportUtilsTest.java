package io.openaev.utils.log.transport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.openaev.config.AuditLogProperties;
import io.openaev.engine.model.log.LogEvent;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditConsoleLogTransportUtils")
class AuditConsoleLogTransportUtilsTest {

  @Mock private ObjectMapper objectMapper;
  @Mock private AuditLogProperties auditLogProperties;

  private AuditConsoleLogTransportUtils transport;

  @BeforeEach
  void setUp() {
    transport = new AuditConsoleLogTransportUtils(auditLogProperties, objectMapper);
  }

  private void setEnabled(boolean enabled) {
    lenient().when(auditLogProperties.isTransportEnabled(any())).thenReturn(enabled);
  }

  @Nested
  @DisplayName("send(LogEvent, Object)")
  class SendLogEvent {

    @Test
    @DisplayName("given_validEvent_should_serializeAndReturnTrue")
    void given_validEvent_should_serializeAndReturnTrue() throws Exception {
      // -- PREPARE --
      setEnabled(true);
      LogEvent event = new LogEvent();
      event.setEventType("authentication");

      ObjectWriter writer = mock(ObjectWriter.class);
      when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
      when(writer.writeValueAsString(event)).thenReturn("{\"event_type\":\"authentication\"}");

      // -- EXECUTE --
      boolean result = transport.send(event, Level.INFO);

      // -- VERIFY --
      assertTrue(result);
      verify(writer).writeValueAsString(event);
    }

    @Test
    @DisplayName("given_validEvent_should_prefixAuditAndPropagateLevel")
    void given_validEvent_should_prefixAuditAndPropagateLevel() throws Exception {
      // -- PREPARE --
      AuditConsoleLogTransportUtils spyTransport =
          spy(new AuditConsoleLogTransportUtils(auditLogProperties, objectMapper));

      LogEvent event = new LogEvent();
      ObjectWriter writer = mock(ObjectWriter.class);
      when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
      when(writer.writeValueAsString(event)).thenReturn("{\"event_type\":\"authentication\"}");
      doReturn(true).when(spyTransport).send(anyString(), any());

      // -- EXECUTE --
      boolean result = spyTransport.send(event, Level.WARNING);

      // -- VERIFY --
      assertTrue(result);
      verify(spyTransport)
          .send(eq("[AUDIT] {\"event_type\":\"authentication\"}"), eq(Level.WARNING));
    }

    @Test
    @DisplayName("given_serializationFailure_should_returnFalse")
    void given_serializationFailure_should_returnFalse() throws Exception {
      // -- PREPARE --
      setEnabled(true);
      LogEvent event = new LogEvent();

      ObjectWriter writer = mock(ObjectWriter.class);
      when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
      when(writer.writeValueAsString(event)).thenThrow(new RuntimeException("serialization error"));

      // -- EXECUTE --
      boolean result = transport.send(event, Level.INFO);

      // -- VERIFY --
      assertFalse(result);
    }

    @Test
    @DisplayName("given_messageSendThrows_should_returnFalse")
    void given_messageSendThrows_should_returnFalse() throws Exception {
      // -- PREPARE --
      AuditConsoleLogTransportUtils spyTransport =
          spy(new AuditConsoleLogTransportUtils(auditLogProperties, objectMapper));

      LogEvent event = new LogEvent();
      ObjectWriter writer = mock(ObjectWriter.class);
      when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
      when(writer.writeValueAsString(event)).thenReturn("{}");
      doThrow(new RuntimeException("log failure")).when(spyTransport).send(anyString(), any());

      // -- EXECUTE --
      boolean result = spyTransport.send(event, Level.INFO);

      // -- VERIFY --
      assertFalse(result);
    }
  }

  @Nested
  @DisplayName("send(String, Object)")
  class SendString {

    @Test
    @DisplayName("given_validMessageAndLevel_should_returnTrue")
    void given_validMessageAndLevel_should_returnTrue() {
      // -- PREPARE --
      setEnabled(true);

      // -- EXECUTE --
      boolean result = transport.send("[AUDIT] test message", Level.INFO);

      // -- VERIFY --
      assertTrue(result);
    }

    @Test
    @DisplayName("given_nullLevel_should_stillReturnTrue")
    void given_nullLevel_should_stillReturnTrue() {
      // -- PREPARE --
      setEnabled(true);

      // -- EXECUTE --
      boolean result = transport.send("[AUDIT] test", null);

      // -- VERIFY --
      assertTrue(result);
    }
  }
}
