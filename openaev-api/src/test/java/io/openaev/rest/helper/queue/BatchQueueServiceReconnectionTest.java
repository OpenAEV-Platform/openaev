package io.openaev.rest.helper.queue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import io.openaev.config.QueueConfig;
import io.openaev.driver.RabbitmqDriver;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.service.queue.QueueExecution;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the shutdown handling and reconnection logic in BatchQueueService. Verifies that
 * application-initiated shutdowns are ignored, while unexpected connection losses trigger
 * reconnection attempts.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchQueueService Reconnection Tests")
class BatchQueueServiceReconnectionTest {

  @Mock private QueueExecution<BatchQueueServiceTest.TestQueueable> queueExecution;
  @Mock private RabbitmqDriver rabbitmqDriver;
  @Mock private ConnectionFactory connectionFactory;
  @Mock private Connection connection;
  @Mock private Channel publisherChannel;
  @Mock private Channel consumerChannel;

  private static final String RABBITMQ_PREFIX = "test_";
  private BatchQueueService<BatchQueueServiceTest.TestQueueable> service;
  private ShutdownListener capturedShutdownListener;

  @BeforeEach
  void setUp() throws IOException, TimeoutException {
    QueueConfig queueConfig = new QueueConfig();
    queueConfig.setQueueName("test-queue");
    queueConfig.setPublisherNumber(1);
    queueConfig.setConsumerNumber(1);
    queueConfig.setWorkerNumber(1);
    queueConfig.setWorkerFrequency(600000);
    queueConfig.setMaxSize(10);
    queueConfig.setPublisherQos(10);
    queueConfig.setConsumerQos(10);

    when(rabbitmqDriver.createBatchConnectionFactory(anyInt())).thenReturn(connectionFactory);
    when(connectionFactory.newConnection()).thenReturn(connection);
    when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);

    service =
        new BatchQueueService<>(
            BatchQueueServiceTest.TestQueueable.class,
            queueExecution,
            RABBITMQ_PREFIX,
            new ObjectMapper(),
            queueConfig,
            rabbitmqDriver);

    // Capture the ShutdownListener registered on the connection
    ArgumentCaptor<ShutdownListener> captor = ArgumentCaptor.forClass(ShutdownListener.class);
    verify(connection).addShutdownListener(captor.capture());
    capturedShutdownListener = captor.getValue();
  }

  @AfterEach
  void tearDown() throws IOException, TimeoutException {
    if (service != null) service.stop();
  }

  @Test
  @DisplayName("should not trigger reconnection on application-initiated shutdown")
  void shouldNotReconnectOnApplicationInitiatedShutdown() {
    ShutdownSignalException appShutdown =
        new ShutdownSignalException(false, true, null, connection);

    capturedShutdownListener.shutdownCompleted(appShutdown);

    // Application-initiated shutdown should NOT enter the reconnection path
    verify(connection, never()).removeShutdownListener(any(ShutdownListener.class));
  }

  @Test
  @DisplayName("should initiate reconnection on unexpected connection loss")
  void shouldInitiateReconnectionOnUnexpectedShutdown() throws Exception {
    ShutdownSignalException unexpectedShutdown =
        new ShutdownSignalException(true, false, null, connection);

    capturedShutdownListener.shutdownCompleted(unexpectedShutdown);

    // Remove the shutdown listener to prevent recursive handling
    verify(connection).removeShutdownListener(capturedShutdownListener);

    // A reconnection task should be scheduled on the reconnectionExecutor
    ScheduledThreadPoolExecutor reconnExecutor = getReconnectionExecutor();
    assertFalse(
        reconnExecutor.getQueue().isEmpty(),
        "A reconnection task should be scheduled on the executor");
  }

  private ScheduledThreadPoolExecutor getReconnectionExecutor() throws Exception {
    Field field = BatchQueueService.class.getDeclaredField("reconnectionExecutor");
    field.setAccessible(true);
    return (ScheduledThreadPoolExecutor) field.get(service);
  }
}
