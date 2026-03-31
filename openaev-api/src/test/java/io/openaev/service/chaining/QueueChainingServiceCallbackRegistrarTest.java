package io.openaev.service.chaining;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.rest.helper.queue.QueueExecution;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueChainingServiceCallbackRegistrar Tests")
class QueueChainingServiceCallbackRegistrarTest {

  @Mock private QueueChainingService queueChainingService;

  @Mock private StepService stepService;

  @InjectMocks private QueueChainingServiceCallbackRegistrar registrar;

  // ========================================================================
  // registerCallbacks Tests
  // ========================================================================
  @Nested
  @DisplayName("registerCallbacks")
  class RegisterCallbacksTests {

    @Test
    @DisplayName("should register callback for ready queue")
    void shouldRegisterCallbackForReadyQueue() {
      // Act
      registrar.registerCallbacks();

      // Assert
      verify(queueChainingService).setCallbackForReadyQueue(any());
    }

    @Test
    @DisplayName("should register callback for external update queue")
    void shouldRegisterCallbackForExternalUpdateQueue() {
      // Act
      registrar.registerCallbacks();

      // Assert
      verify(queueChainingService).setCallbackForExternalUpdateQueue(any());
    }

    @Test
    @DisplayName("should register all three callbacks")
    void shouldRegisterAllCallbacks() {
      // Act
      registrar.registerCallbacks();

      // Assert
      verify(queueChainingService).setCallbackForReadyQueue(any());
      verify(queueChainingService).setCallbackForExternalUpdateQueue(any());
      verifyNoMoreInteractions(queueChainingService);
    }

    @Test
    @DisplayName("should register callbacks in correct order")
    void shouldRegisterCallbacksInCorrectOrder() {
      // Act
      registrar.registerCallbacks();

      // Assert
      InOrder inOrder = inOrder(queueChainingService);
      inOrder.verify(queueChainingService).setCallbackForReadyQueue(any());
      inOrder.verify(queueChainingService).setCallbackForExternalUpdateQueue(any());
    }

    @Test
    @DisplayName("should be idempotent when called multiple times")
    void shouldBeIdempotentWhenCalledMultipleTimes() {
      // Act
      registrar.registerCallbacks();
      registrar.registerCallbacks();

      // Assert
      verify(queueChainingService, times(2)).setCallbackForReadyQueue(any());
      verify(queueChainingService, times(2)).setCallbackForExternalUpdateQueue(any());
    }
  }

  // ========================================================================
  // Callback Functionality Tests
  // ========================================================================
  @Nested
  @DisplayName("callback functionality")
  class CallbackFunctionalityTests {

    @Captor private ArgumentCaptor<QueueExecution<StepEvent>> readyQueueCallbackCaptor;

    @Captor private ArgumentCaptor<QueueExecution<StepEvent>> delayQueueCallbackCaptor;

    @Captor
    private ArgumentCaptor<QueueExecution<ExternalUpdateEvent>> externalUpdateCallbackCaptor;

    @Test
    @DisplayName("should invoke stepService.handleReadyEvent when ready queue callback is executed")
    void shouldInvokeHandleReadyEvent() {
      // Prepare
      List<StepEvent> events = List.of(mock(StepEvent.class));

      // Act
      registrar.registerCallbacks();

      // Capture and invoke callback
      verify(queueChainingService).setCallbackForReadyQueue(readyQueueCallbackCaptor.capture());
      QueueExecution<StepEvent> callback = readyQueueCallbackCaptor.getValue();
      callback.perform(events);

      // Assert
      verify(stepService).handleReadyEvent(events);
    }

    @Test
    @DisplayName(
        "should invoke stepService.handleExternalUpdateEvent when external update callback is executed")
    void shouldInvokeHandleExternalUpdateEvent() {
      // Prepare
      List<ExternalUpdateEvent> events = List.of(mock(ExternalUpdateEvent.class));

      // Act
      registrar.registerCallbacks();

      // Capture and invoke callback
      verify(queueChainingService)
          .setCallbackForExternalUpdateQueue(externalUpdateCallbackCaptor.capture());
      QueueExecution<ExternalUpdateEvent> callback = externalUpdateCallbackCaptor.getValue();
      callback.perform(events);

      // Assert
      verify(stepService).handleExternalUpdateEvent(events);
    }
  }
}
