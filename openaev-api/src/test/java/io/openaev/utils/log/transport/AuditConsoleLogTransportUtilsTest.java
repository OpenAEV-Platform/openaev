package io.openaev.utils.log.transport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.openaev.engine.model.log.LogEvent;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditConsoleLogTransportUtils")
class AuditConsoleLogTransportUtilsTest {

  @Mock private ObjectMapper objectMapper;

  private AuditConsoleLogTransportUtils transport;

  @BeforeEach
  void setUp() {
    transport = new AuditConsoleLogTransportUtils(objectMapper);
  }

  private void setEnabled(boolean enabled) {
    ReflectionTestUtils.setField(transport, "enabled", enabled);
  }

  @Nested
  @DisplayName("isEnabled")
  class IsEnabled {

    @Test
    @DisplayName("given_enabledPropertyTrue_should_returnTrue")
    void given_enabledPropertyTrue_should_returnTrue() {
      // -- PREPARE --
      setEnabled(true);

      // -- EXECUTE --
      boolean result = transport.isEnabled();

      // -- VERIFY --
      assertTrue(result);
    }

    @Test
    @DisplayName("given_enabledPropertyFalse_should_returnFalse")
    void given_enabledPropertyFalse_should_returnFalse() {
      // -- PREPARE --
      setEnabled(false);

      // -- EXECUTE --
      boolean result = transport.isEnabled();

      // -- VERIFY --
      assertFalse(result);
    }
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
      CompletableFuture<Boolean> result = transport.send(event, Level.INFO);

      // -- VERIFY --
      assertTrue(result.join());
      verify(writer).writeValueAsString(event);
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
      CompletableFuture<Boolean> result = transport.send(event, Level.INFO);

      // -- VERIFY --
      assertFalse(result.join());
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
      CompletableFuture<Boolean> result = transport.send("[AUDIT] test message", Level.INFO);

      // -- VERIFY --
      assertTrue(result.join());
    }

    @Test
    @DisplayName("given_nullLevel_should_stillReturnTrue")
    void given_nullLevel_should_stillReturnTrue() {
      // -- PREPARE --
      setEnabled(true);

      // -- EXECUTE --
      CompletableFuture<Boolean> result = transport.send("[AUDIT] test", null);

      // -- VERIFY --
      assertTrue(result.join());
    }
  }
}
