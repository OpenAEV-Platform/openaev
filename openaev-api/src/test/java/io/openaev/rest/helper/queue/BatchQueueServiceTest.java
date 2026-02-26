package io.openaev.rest.helper.queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.QueueConfig;
import io.openaev.config.RabbitMQSslConfiguration;
import io.openaev.config.RabbitmqConfig;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchQueueService Tests")
class BatchQueueServiceTest {

  @Mock private QueueExecution<TestQueueable> queueExecution;
  @Mock private Connection connection;
  @Mock private Channel publisherChannel;
  @Mock private Channel consumerChannel;

  private RabbitmqConfig rabbitmqConfig;
  private QueueConfig queueConfig;
  private RabbitMQSslConfiguration rabbitMQSslConfiguration;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    rabbitmqConfig = new RabbitmqConfig();
    rabbitmqConfig.setPrefix("test_");
    rabbitmqConfig.setHostname("localhost");
    rabbitmqConfig.setPort(5672);
    rabbitmqConfig.setUser("guest");
    rabbitmqConfig.setPass("guest");
    rabbitmqConfig.setVhost("/");

    queueConfig = new QueueConfig();
    queueConfig.setQueueName("test-queue");
    queueConfig.setPublisherNumber(1);
    queueConfig.setConsumerNumber(1);
    queueConfig.setWorkerNumber(1);
    queueConfig.setWorkerFrequency(1000);
    queueConfig.setMaxSize(10);
    queueConfig.setPublisherQos(10);
    queueConfig.setConsumerQos(10);

    mapper = new ObjectMapper();
  }

  // ========================================================================
  // Constants Tests
  // ========================================================================
  @Nested
  @DisplayName("Constants")
  class ConstantsTests {

    @Test
    @DisplayName("should have correct ROUTING_KEY format")
    void shouldHaveCorrectRoutingKeyFormat() {
      assertEquals("_push_routing_%s", BatchQueueService.ROUTING_KEY);
    }

    @Test
    @DisplayName("should have correct EXCHANGE_KEY format")
    void shouldHaveCorrectExchangeKeyFormat() {
      assertEquals("_amqp.%s.exchange", BatchQueueService.EXCHANGE_KEY);
    }

    @Test
    @DisplayName("should have correct QUEUE_NAME format")
    void shouldHaveCorrectQueueNameFormat() {
      assertEquals("_execution_%s", BatchQueueService.QUEUE_NAME);
    }
  }

  // ========================================================================
  // Constructor Tests
  // ========================================================================
  @Nested
  @DisplayName("Constructor")
  class ConstructorTests {

    @Test
    @DisplayName("should initialize BatchQueueService with valid configuration")
    void shouldInitializeWithValidConfiguration() throws IOException, TimeoutException {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenReturn(connection);
              })) {

        when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);

        BatchQueueService<TestQueueable> service =
            new BatchQueueService<>(
                TestQueueable.class,
                queueExecution,
                rabbitmqConfig,
                mapper,
                queueConfig,
                rabbitMQSslConfiguration);

        assertNotNull(service);
        service.stop();
      }
    }

    @Test
    @DisplayName("should throw IOException when connection fails")
    void shouldThrowIOExceptionWhenConnectionFails() {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenThrow(new IOException("Connection failed"));
              })) {

        assertThrows(
            IOException.class,
            () ->
                new BatchQueueService<>(
                    TestQueueable.class,
                    queueExecution,
                    rabbitmqConfig,
                    mapper,
                    queueConfig,
                    rabbitMQSslConfiguration));
      }
    }

    @Test
    @DisplayName("should throw TimeoutException when connection times out")
    void shouldThrowTimeoutExceptionWhenConnectionTimesOut() {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenThrow(new TimeoutException("Connection timed out"));
              })) {

        assertThrows(
            TimeoutException.class,
            () ->
                new BatchQueueService<>(
                    TestQueueable.class,
                    queueExecution,
                    rabbitmqConfig,
                    mapper,
                    queueConfig,
                    rabbitMQSslConfiguration));
      }
    }
  }

  // ========================================================================
  // publish Tests
  // ========================================================================
  @Nested
  @DisplayName("publish")
  class PublishTests {

    @Test
    @DisplayName("should publish element to queue")
    void shouldPublishElementToQueue() throws IOException, TimeoutException {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenReturn(connection);
              })) {

        when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);

        BatchQueueService<TestQueueable> service =
            new BatchQueueService<>(
                TestQueueable.class,
                queueExecution,
                rabbitmqConfig,
                mapper,
                queueConfig,
                rabbitMQSslConfiguration);

        TestQueueable element = new TestQueueable("key1");
        service.publish(element);

        verify(publisherChannel)
            .basicPublish(anyString(), anyString(), isNull(), any(byte[].class));
        service.stop();
      }
    }

    @Test
    @DisplayName("should throw IOException when publish fails")
    void shouldThrowIOExceptionWhenPublishFails() throws IOException, TimeoutException {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenReturn(connection);
              })) {

        when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);
        doThrow(new IOException("Publish failed"))
            .when(publisherChannel)
            .basicPublish(anyString(), anyString(), isNull(), any(byte[].class));

        BatchQueueService<TestQueueable> service =
            new BatchQueueService<>(
                TestQueueable.class,
                queueExecution,
                rabbitmqConfig,
                mapper,
                queueConfig,
                rabbitMQSslConfiguration);

        TestQueueable element = new TestQueueable("key1");

        assertThrows(IOException.class, () -> service.publish(element));
        service.stop();
      }
    }
  }

  // ========================================================================
  // forcePurge Tests
  // ========================================================================
  @Nested
  @DisplayName("forcePurge")
  class ForcePurgeTests {

    @Test
    @DisplayName("should purge queue")
    void shouldPurgeQueue() throws IOException, TimeoutException {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenReturn(connection);
              })) {

        when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);

        BatchQueueService<TestQueueable> service =
            new BatchQueueService<>(
                TestQueueable.class,
                queueExecution,
                rabbitmqConfig,
                mapper,
                queueConfig,
                rabbitMQSslConfiguration);

        service.forcePurge();

        verify(publisherChannel).queuePurge(anyString());
        service.stop();
      }
    }

    @Test
    @DisplayName("should throw IOException when purge fails")
    void shouldThrowIOExceptionWhenPurgeFails() throws IOException, TimeoutException {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenReturn(connection);
              })) {

        when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);
        doThrow(new IOException("Purge failed")).when(publisherChannel).queuePurge(anyString());

        BatchQueueService<TestQueueable> service =
            new BatchQueueService<>(
                TestQueueable.class,
                queueExecution,
                rabbitmqConfig,
                mapper,
                queueConfig,
                rabbitMQSslConfiguration);

        assertThrows(IOException.class, service::forcePurge);
        service.stop();
      }
    }
  }

  // ========================================================================
  // stop Tests
  // ========================================================================
  @Nested
  @DisplayName("stop")
  class StopTests {

    @Test
    @DisplayName("should close channels and connection")
    void shouldCloseChannelsAndConnection() throws IOException, TimeoutException {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenReturn(connection);
              })) {

        when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);
        when(publisherChannel.isOpen()).thenReturn(true);
        when(consumerChannel.isOpen()).thenReturn(true);
        when(connection.isOpen()).thenReturn(true);

        BatchQueueService<TestQueueable> service =
            new BatchQueueService<>(
                TestQueueable.class,
                queueExecution,
                rabbitmqConfig,
                mapper,
                queueConfig,
                rabbitMQSslConfiguration);

        service.stop();

        verify(publisherChannel).close();
        verify(consumerChannel).close();
        verify(connection).close();
      }
    }

    @Test
    @DisplayName("should not close already closed channels")
    void shouldNotCloseAlreadyClosedChannels() throws IOException, TimeoutException {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenReturn(connection);
              })) {

        when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);
        when(publisherChannel.isOpen()).thenReturn(false);
        when(consumerChannel.isOpen()).thenReturn(false);
        when(connection.isOpen()).thenReturn(false);

        BatchQueueService<TestQueueable> service =
            new BatchQueueService<>(
                TestQueueable.class,
                queueExecution,
                rabbitmqConfig,
                mapper,
                queueConfig,
                rabbitMQSslConfiguration);

        service.stop();

        verify(publisherChannel, never()).close();
        verify(consumerChannel, never()).close();
        verify(connection, never()).close();
      }
    }
  }

  // ========================================================================
  // processBufferedBatch Tests
  // ========================================================================
  @Nested
  @DisplayName("processBufferedBatch")
  class ProcessBufferedBatchTests {

    @Test
    @DisplayName("should process empty batch without error")
    void shouldProcessEmptyBatchWithoutError() throws IOException, TimeoutException {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenReturn(connection);
              })) {

        when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);

        BatchQueueService<TestQueueable> service =
            new BatchQueueService<>(
                TestQueueable.class,
                queueExecution,
                rabbitmqConfig,
                mapper,
                queueConfig,
                rabbitMQSslConfiguration);

        // Should not throw
        service.processBufferedBatch(0);

        // Wait for async processing
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }

        service.stop();
      }
    }
  }

  // ========================================================================
  // setQueueExecution Tests
  // ========================================================================
  @Nested
  @DisplayName("setQueueExecution")
  class SetQueueExecutionTests {

    @Test
    @DisplayName("should allow setting queue execution after construction")
    void shouldAllowSettingQueueExecutionAfterConstruction() throws IOException, TimeoutException {
      try (MockedConstruction<ConnectionFactory> mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> {
                when(mock.newConnection()).thenReturn(connection);
              })) {

        when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);

        BatchQueueService<TestQueueable> service =
            new BatchQueueService<>(
                TestQueueable.class,
                queueExecution,
                rabbitmqConfig,
                mapper,
                queueConfig,
                rabbitMQSslConfiguration);

        QueueExecution<TestQueueable> newExecution = elements -> elements;
        service.setQueueExecution(newExecution);

        // Should not throw
        assertNotNull(service);
        service.stop();
      }
    }
  }

  // ========================================================================
  // Helper class for testing
  // ========================================================================
  static class TestQueueable implements Queueable {
    private final String key;

    TestQueueable(String key) {
      this.key = key;
    }

    @Override
    public String getUniqueElementKey() {
      return key;
    }

    @Override
    public int hashCode() {
      return key != null ? key.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || getClass() != obj.getClass()) return false;
      TestQueueable that = (TestQueueable) obj;
      return java.util.Objects.equals(key, that.key);
    }
  }
}
