package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.QueueConfig;
import io.openaev.config.RabbitmqConfig;
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

  @Mock private RabbitmqConfig rabbitmqConfig;

  @Mock private OpenAEVConfig openAEVConfig;

  @Mock private ObjectMapper objectMapper;

  @Mock private BatchQueueService<StepEvent> delayQueueService;

  @Mock private BatchQueueService<StepEvent> waitQueueService;

  @Mock private BatchQueueService<ExternalUpdateEvent> updateQueueService;

  @InjectMocks private QueueChainingService queueChainingService;

  @BeforeEach
  void setUp() {
    queueChainingService.setDelayQueueService(delayQueueService);
    queueChainingService.setWaitQueueService(waitQueueService);
    queueChainingService.setUpdateQueueService(updateQueueService);
  }

  // ========================================================================
  // init Tests
  // ========================================================================
  @Nested
  @DisplayName("init")
  class InitTests {

    @Test
    @DisplayName("should throw RuntimeException when workflows-wait config is missing")
    void shouldThrowWhenWaitConfigMissing() {
      // Prepare
      Map<String, QueueConfig> queueConfig = new HashMap<>();
      queueConfig.put("workflows-update", new QueueConfig());
      queueConfig.put("workflows-delay", new QueueConfig());
      when(openAEVConfig.getQueueConfig()).thenReturn(queueConfig);

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> queueChainingService.init());
      assertTrue(exception.getMessage().contains("workflows-wait"));
    }

    @Test
    @DisplayName("should throw RuntimeException when workflows-update config is missing")
    void shouldThrowWhenUpdateConfigMissing() {
      // Prepare
      Map<String, QueueConfig> queueConfig = new HashMap<>();
      queueConfig.put("workflows-wait", new QueueConfig());
      queueConfig.put("workflows-delay", new QueueConfig());
      when(openAEVConfig.getQueueConfig()).thenReturn(queueConfig);

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> queueChainingService.init());
      assertTrue(exception.getMessage().contains("workflows-update"));
    }

    @Test
    @DisplayName("should throw RuntimeException when workflows-delay config is missing")
    void shouldThrowWhenDelayConfigMissing() {
      // Prepare
      Map<String, QueueConfig> queueConfig = new HashMap<>();
      queueConfig.put("workflows-wait", new QueueConfig());
      queueConfig.put("workflows-update", new QueueConfig());
      when(openAEVConfig.getQueueConfig()).thenReturn(queueConfig);

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> queueChainingService.init());
      assertTrue(exception.getMessage().contains("workflows-delay"));
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
  // delayStep Tests
  // ========================================================================
  @Nested
  @DisplayName("delayStep")
  class DelayStepTests {

    @Captor private ArgumentCaptor<StepEvent> eventCaptor;

    @Test
    @DisplayName("should publish event with correct step id")
    void shouldPublishEventWithCorrectStepId() throws IOException {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();
      long delayMs = 5000L;

      Step stepTemplate = mock(Step.class);
      when(stepTemplate.getId()).thenReturn(stepId);

      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn(workflowId);

      // Act
      queueChainingService.delayStep(stepTemplate, workflowRun, delayMs);

      // Assert
      verify(delayQueueService).publish(eventCaptor.capture());
      StepEvent event = eventCaptor.getValue();
      assertEquals(stepId, event.getStepId());
    }

    @Test
    @DisplayName("should publish event with correct workflow id")
    void shouldPublishEventWithCorrectWorkflowId() throws IOException {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();
      long delayMs = 5000L;

      Step stepTemplate = mock(Step.class);
      when(stepTemplate.getId()).thenReturn(stepId);

      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn(workflowId);

      // Act
      queueChainingService.delayStep(stepTemplate, workflowRun, delayMs);

      // Assert
      verify(delayQueueService).publish(eventCaptor.capture());
      StepEvent event = eventCaptor.getValue();
      assertEquals(workflowId, event.getWorkflowId());
    }

    @Test
    @DisplayName("should publish event with emission date")
    void shouldPublishEventWithEmissionDate() throws IOException {
      // Prepare
      Step stepTemplate = mock(Step.class);
      when(stepTemplate.getId()).thenReturn(UUID.randomUUID().toString());

      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn(UUID.randomUUID().toString());

      long beforeTest = Instant.now().toEpochMilli();

      // Act
      queueChainingService.delayStep(stepTemplate, workflowRun, 5000L);

      long afterTest = Instant.now().toEpochMilli();

      // Assert
      verify(delayQueueService).publish(eventCaptor.capture());
      StepEvent event = eventCaptor.getValue();
      assertTrue(event.getEmissionDate() >= beforeTest);
      assertTrue(event.getEmissionDate() <= afterTest);
    }

    @Test
    @DisplayName("should propagate IOException from queue service")
    void shouldPropagateIOException() throws IOException {
      // Prepare
      Step stepTemplate = mock(Step.class);
      when(stepTemplate.getId()).thenReturn(UUID.randomUUID().toString());

      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn(UUID.randomUUID().toString());

      doThrow(new IOException("Queue error")).when(delayQueueService).publish(any());

      // Act & Assert
      assertThrows(
          IOException.class,
          () -> queueChainingService.delayStep(stepTemplate, workflowRun, 5000L));
    }
  }

  // ========================================================================
  // waitStep Tests
  // ========================================================================
  @Nested
  @DisplayName("waitStep")
  class WaitStepTests {

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
      queueChainingService.waitStep(stepExecution, workflowRun);

      // Assert
      verify(waitQueueService).publish(eventCaptor.capture());
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
      queueChainingService.waitStep(stepExecution, workflowRun);

      // Assert
      verify(waitQueueService).publish(eventCaptor.capture());
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
      queueChainingService.waitStep(stepExecution, workflowRun);

      long afterTest = Instant.now().toEpochMilli();

      // Assert
      verify(waitQueueService).publish(eventCaptor.capture());
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

      doThrow(new IOException("Queue error")).when(waitQueueService).publish(any());

      // Act & Assert
      assertThrows(
          IOException.class, () -> queueChainingService.waitStep(stepExecution, workflowRun));
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
  // setCallbackForDelayQueue Tests
  // ========================================================================
  @Nested
  @DisplayName("setCallbackForDelayQueue")
  class SetCallbackForDelayQueueTests {

    @Captor private ArgumentCaptor<QueueExecution<StepEvent>> callbackCaptor;

    @Test
    @DisplayName("should set callback on delay queue service")
    void shouldSetCallbackOnDelayQueueService() {
      // Prepare
      QueueExecution<StepEvent> callback = mock(QueueExecution.class);

      // Act
      queueChainingService.setCallbackForDelayQueue(callback);

      // Assert
      verify(delayQueueService).setQueueExecution(callbackCaptor.capture());
      assertEquals(callback, callbackCaptor.getValue());
    }
  }

  // ========================================================================
  // setCallbackForWaitQueue Tests
  // ========================================================================
  @Nested
  @DisplayName("setCallbackForWaitQueue")
  class SetCallbackForWaitQueueTests {

    @Captor private ArgumentCaptor<QueueExecution<StepEvent>> callbackCaptor;

    @Test
    @DisplayName("should set callback on wait queue service")
    void shouldSetCallbackOnWaitQueueService() {
      // Prepare
      QueueExecution<StepEvent> callback = mock(QueueExecution.class);

      // Act
      queueChainingService.setCallbackForWaitQueue(callback);

      // Assert
      verify(waitQueueService).setQueueExecution(callbackCaptor.capture());
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
