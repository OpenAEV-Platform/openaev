package io.openaev.utils.log.dispatcher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.log.transport.AuditLogTransportUtils;
import java.util.List;
import java.util.logging.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogTransportDispatcherUtils")
class AuditLogTransportDispatcherUtilsTest {

  @Nested
  @DisplayName("dispatch(LogEvent, Object)")
  class DispatchLogEvent {

    @Test
    @DisplayName("given_allTransportsEnabledAndSucceed_should_returnTrue")
    void given_allTransportsEnabledAndSucceed_should_returnTrue() {
      // -- PREPARE --
      AuditLogTransportUtils t1 = mock(AuditLogTransportUtils.class);
      AuditLogTransportUtils t2 = mock(AuditLogTransportUtils.class);
      when(t1.isEnabled()).thenReturn(true);
      when(t2.isEnabled()).thenReturn(true);
      when(t1.send(any(LogEvent.class), any())).thenReturn(true);
      when(t2.send(any(LogEvent.class), any())).thenReturn(true);

      AuditLogTransportDispatcherUtils dispatcher =
          new AuditLogTransportDispatcherUtils(List.of(t1, t2));
      LogEvent event = new LogEvent();

      // -- EXECUTE --
      boolean result = dispatcher.dispatch(event, Level.INFO);

      // -- VERIFY --
      assertTrue(result);
      verify(t1).send(event, Level.INFO);
      verify(t2).send(event, Level.INFO);
    }

    @Test
    @DisplayName("given_oneTransportFails_should_returnFalse")
    void given_oneTransportFails_should_returnFalse() {
      // -- PREPARE --
      AuditLogTransportUtils t1 = mock(AuditLogTransportUtils.class);
      AuditLogTransportUtils t2 = mock(AuditLogTransportUtils.class);
      when(t1.isEnabled()).thenReturn(true);
      when(t2.isEnabled()).thenReturn(true);
      when(t1.send(any(LogEvent.class), any())).thenReturn(true);
      when(t2.send(any(LogEvent.class), any())).thenReturn(false);

      AuditLogTransportDispatcherUtils dispatcher =
          new AuditLogTransportDispatcherUtils(List.of(t1, t2));

      // -- EXECUTE --
      boolean result = dispatcher.dispatch(new LogEvent(), Level.INFO);

      // -- VERIFY --
      assertFalse(result);
    }

    @Test
    @DisplayName("given_transportDisabled_should_skipIt")
    void given_transportDisabled_should_skipIt() {
      // -- PREPARE --
      AuditLogTransportUtils enabled = mock(AuditLogTransportUtils.class);
      AuditLogTransportUtils disabled = mock(AuditLogTransportUtils.class);
      when(enabled.isEnabled()).thenReturn(true);
      when(disabled.isEnabled()).thenReturn(false);
      when(enabled.send(any(LogEvent.class), any())).thenReturn(true);

      AuditLogTransportDispatcherUtils dispatcher =
          new AuditLogTransportDispatcherUtils(List.of(enabled, disabled));

      // -- EXECUTE --
      boolean result = dispatcher.dispatch(new LogEvent(), Level.INFO);

      // -- VERIFY --
      assertTrue(result);
      verify(disabled, never()).send(any(LogEvent.class), any());
    }

    @Test
    @DisplayName("given_noEnabledTransports_should_returnTrue")
    void given_noEnabledTransports_should_returnTrue() {
      // -- PREPARE --
      AuditLogTransportUtils transport = mock(AuditLogTransportUtils.class);
      when(transport.isEnabled()).thenReturn(false);

      AuditLogTransportDispatcherUtils dispatcher =
          new AuditLogTransportDispatcherUtils(List.of(transport));

      // -- EXECUTE --
      boolean result = dispatcher.dispatch(new LogEvent(), Level.INFO);

      // -- VERIFY --
      assertTrue(result);
    }
  }

  @Nested
  @DisplayName("dispatch(String, Object)")
  class DispatchString {

    @Test
    @DisplayName("given_enabledTransport_should_sendMessage")
    void given_enabledTransport_should_sendMessage() {
      // -- PREPARE --
      AuditLogTransportUtils transport = mock(AuditLogTransportUtils.class);
      when(transport.isEnabled()).thenReturn(true);
      when(transport.send(anyString(), any())).thenReturn(true);

      AuditLogTransportDispatcherUtils dispatcher =
          new AuditLogTransportDispatcherUtils(List.of(transport));

      // -- EXECUTE --
      boolean result = dispatcher.dispatch("[AUDIT] test", Level.WARNING);

      // -- VERIFY --
      assertTrue(result);
      verify(transport).send("[AUDIT] test", Level.WARNING);
    }
  }
}
