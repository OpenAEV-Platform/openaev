package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.QueueConfig;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.rest.helper.queue.BatchQueueService;
import io.openaev.rest.helper.queue.QueueExecution;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueChainingService Tests")
class QueueChainingServiceTest {

  @Mock private OpenAEVConfig openAEVConfig;

  @Mock private BatchQueueService<StepEvent> delayQueueService;

  @Mock private BatchQueueService<StepEvent> readyQueueService;

  @Mock private BatchQueueService<ExternalUpdateEvent> updateQueueService;

  @InjectMocks private QueueChainingService queueChainingService;

  @BeforeEach
  void setUp() {
    queueChainingService.setReadyQueueService(readyQueueService);
    queueChainingService.setUpdateQueueService(updateQueueService);
  }

  // ========================================================================
  // init Tests
  // ========================================================================
  @Nested
  @DisplayName("init")
  class InitTests {

    @Test
    @DisplayName("should throw RuntimeException when workflows-ready config is missing")
    void shouldThrowWhenReadyConfigMissing() {
      // Prepare
      Map<String, QueueConfig> queueConfig = new HashMap<>();
      queueConfig.put("workflows-update", new QueueConfig());
      queueConfig.put("workflows-delay", new QueueConfig());
      when(openAEVConfig.getQueueConfig()).thenReturn(queueConfig);

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> queueChainingService.init());
      assertTrue(exception.getMessage().contains("workflows-ready"));
    }

    @Test
    @DisplayName("should throw RuntimeException when workflows-update config is missing")
    void shouldThrowWhenUpdateConfigMissing() {
      // Prepare
      Map<String, QueueConfig> queueConfig = new HashMap<>();
      queueConfig.put("workflows-ready", new QueueConfig());
      queueConfig.put("workflows-delay", new QueueConfig());
      when(openAEVConfig.getQueueConfig()).thenReturn(queueConfig);

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> queueChainingService.init());
      assertTrue(exception.getMessage().contains("workflows-update"));
    }

    @Test
    @DisplayName("should throw RuntimeException when all configs are missing")
    void shouldThrowWhenAllConfigsMissing() {
      // Prepare
      Map<String, QueueConfig> queueConfig = new HashMap<>();
      when(openAEVConfig.getQueueConfig()).thenReturn(queueConfig);

      // Act & Assert
      assertThrows(RuntimeException.class, () -> queueChainingService.init());
    }
  }

  // ========================================================================
  // readyStep Tests
  // ========================================================================
  @Nested
  @DisplayName("readyStep")
  class ReadyStepTests {

    @Captor private ArgumentCaptor<StepEvent> eventCaptor;

    @Test
    @DisplayName("should publish event with correct step id")
    void shouldPublishEventWithCorrectStepId() throws IOException {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();

      Step stepExecution = mock(Step.class);
      when(stepExecution.getId()).thenReturn(stepId);

      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn(workflowId);

      // Act
      queueChainingService.readyStep(stepExecution, workflowRun);

      // Assert
      verify(readyQueueService).publish(eventCaptor.capture());
      StepEvent event = eventCaptor.getValue();
      assertEquals(stepId, event.getStepId());
    }

    @Test
    @DisplayName("should publish event with correct workflow id")
    void shouldPublishEventWithCorrectWorkflowId() throws IOException {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();

      Step stepExecution = mock(Step.class);
      when(stepExecution.getId()).thenReturn(stepId);

      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn(workflowId);

      // Act
      queueChainingService.readyStep(stepExecution, workflowRun);

      // Assert
      verify(readyQueueService).publish(eventCaptor.capture());
      StepEvent event = eventCaptor.getValue();
      assertEquals(workflowId, event.getWorkflowId());
    }

    @Test
    @DisplayName("should publish event with emission date")
    void shouldPublishEventWithEmissionDate() throws IOException {
      // Prepare
      Step stepExecution = mock(Step.class);
      when(stepExecution.getId()).thenReturn(UUID.randomUUID().toString());

      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn(UUID.randomUUID().toString());

      long beforeTest = Instant.now().toEpochMilli();

      // Act
      queueChainingService.readyStep(stepExecution, workflowRun);

      long afterTest = Instant.now().toEpochMilli();

      // Assert
      verify(readyQueueService).publish(eventCaptor.capture());
      StepEvent event = eventCaptor.getValue();
      assertTrue(event.getEmissionDate() >= beforeTest);
      assertTrue(event.getEmissionDate() <= afterTest);
    }

    @Test
    @DisplayName("should propagate IOException from queue service")
    void shouldPropagateIOException() throws IOException {
      // Prepare
      Step stepExecution = mock(Step.class);
      when(stepExecution.getId()).thenReturn(UUID.randomUUID().toString());

      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn(UUID.randomUUID().toString());

      doThrow(new IOException("Queue error")).when(readyQueueService).publish(any());

      // Act & Assert
      assertThrows(
          IOException.class, () -> queueChainingService.readyStep(stepExecution, workflowRun));
    }
  }

  // ========================================================================
  // updateStep Tests
  // ========================================================================
  @Nested
  @DisplayName("updateStep")
  class UpdateStepTests {

    @Captor private ArgumentCaptor<ExternalUpdateEvent> eventCaptor;

    @Test
    @DisplayName("should publish event with correct step id")
    void shouldPublishEventWithCorrectStepId() throws IOException {
      // Prepare
      String stepRunId = UUID.randomUUID().toString();

      // Act
      queueChainingService.updateStep(stepRunId);

      // Assert
      verify(updateQueueService).publish(eventCaptor.capture());
      ExternalUpdateEvent event = eventCaptor.getValue();
      assertEquals(stepRunId, event.getStepId());
    }

    @Test
    @DisplayName("should publish event with emission date")
    void shouldPublishEventWithEmissionDate() throws IOException {
      // Prepare
      String stepRunId = UUID.randomUUID().toString();
      long beforeTest = Instant.now().toEpochMilli();

      // Act
      queueChainingService.updateStep(stepRunId);

      long afterTest = Instant.now().toEpochMilli();

      // Assert
      verify(updateQueueService).publish(eventCaptor.capture());
      ExternalUpdateEvent event = eventCaptor.getValue();
      assertTrue(event.getEmissionDate() >= beforeTest);
      assertTrue(event.getEmissionDate() <= afterTest);
    }

    @Test
    @DisplayName("should propagate IOException from queue service")
    void shouldPropagateIOException() throws IOException {
      // Prepare
      String stepRunId = UUID.randomUUID().toString();
      doThrow(new IOException("Queue error")).when(updateQueueService).publish(any());

      // Act & Assert
      assertThrows(IOException.class, () -> queueChainingService.updateStep(stepRunId));
    }
  }

  // ========================================================================
  // setCallbackForReadyQueue Tests
  // ========================================================================
  @Nested
  @DisplayName("setCallbackForReadyQueue")
  class SetCallbackForReadyQueueTests {

    @Captor private ArgumentCaptor<QueueExecution<StepEvent>> callbackCaptor;

    @Test
    @DisplayName("should set callback on ready queue service")
    void shouldSetCallbackOnReadyQueueService() {
      // Prepare
      QueueExecution<StepEvent> callback = mock(QueueExecution.class);

      // Act
      queueChainingService.setCallbackForReadyQueue(callback);

      // Assert
      verify(readyQueueService).setQueueExecution(callbackCaptor.capture());
      assertEquals(callback, callbackCaptor.getValue());
    }
  }

  // ========================================================================
  // setCallbackForExternalUpdateQueue Tests
  // ========================================================================
  @Nested
  @DisplayName("setCallbackForExternalUpdateQueue")
  class SetCallbackForExternalUpdateQueueTests {

    @Captor private ArgumentCaptor<QueueExecution<ExternalUpdateEvent>> callbackCaptor;

    @Test
    @DisplayName("should set callback on update queue service")
    void shouldSetCallbackOnUpdateQueueService() {
      // Prepare
      QueueExecution<ExternalUpdateEvent> callback = mock(QueueExecution.class);

      // Act
      queueChainingService.setCallbackForExternalUpdateQueue(callback);

      // Assert
      verify(updateQueueService).setQueueExecution(callbackCaptor.capture());
      assertEquals(callback, callbackCaptor.getValue());
    }
  }
}
