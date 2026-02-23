package io.openaev.rest.helper.queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import io.openaev.config.QueueConfig;
import io.openaev.config.RabbitMQSslConfiguration;
import io.openaev.config.RabbitmqConfig;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the consumer delivery callback path inside BatchQueueService. This path is responsible for
 * deserializing messages from RabbitMQ, routing them to the correct worker queue via groupByKey,
 * populating the deliveryTable, and triggering batch processing when the queue exceeds maxSize.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchQueueService Delivery Callback Tests")
class BatchQueueServiceDeliveryCallbackTest {

  @Mock private QueueExecution<SerializableQueueable> queueExecution;
  @Mock private Connection connection;
  @Mock private Channel publisherChannel;
  @Mock private Channel consumerChannel;
  @Mock private RabbitMQSslConfiguration rabbitMQSslConfiguration;

  private ObjectMapper mapper;
  private BatchQueueService<SerializableQueueable> service;
  private MockedConstruction<ConnectionFactory> mockedFactory;
  private DeliverCallback deliverCallback;

  @BeforeEach
  void setUp() throws Exception {
    RabbitmqConfig rabbitmqConfig = new RabbitmqConfig();
    rabbitmqConfig.setPrefix("test_");
    rabbitmqConfig.setHostname("localhost");
    rabbitmqConfig.setPort(5672);
    rabbitmqConfig.setUser("guest");
    rabbitmqConfig.setPass("guest");
    rabbitmqConfig.setVhost("/");

    QueueConfig queueConfig = new QueueConfig();
    queueConfig.setQueueName("test-queue");
    queueConfig.setPublisherNumber(1);
    queueConfig.setConsumerNumber(1);
    queueConfig.setWorkerNumber(1);
    queueConfig.setWorkerFrequency(600000);
    queueConfig.setMaxSize(2); // Small maxSize for critical mass tests
    queueConfig.setPublisherQos(10);
    queueConfig.setConsumerQos(10);

    mapper = new ObjectMapper();
    // Match Spring Boot's default: ignore unknown properties during deserialization.
    // In production, getUniqueElementKey() IS serialized to JSON but ignored on read.
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    mockedFactory =
        mockConstruction(
            ConnectionFactory.class,
            (mock, context) -> when(mock.newConnection()).thenReturn(connection));

    when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);

    service =
        new BatchQueueService<>(
            SerializableQueueable.class,
            queueExecution,
            rabbitmqConfig,
            mapper,
            queueConfig,
            rabbitMQSslConfiguration);

    // Capture the DeliverCallback registered with the consumer channel
    ArgumentCaptor<DeliverCallback> captor = ArgumentCaptor.forClass(DeliverCallback.class);
    verify(consumerChannel)
        .basicConsume(
            anyString(),
            eq(false),
            anyString(),
            eq(false),
            eq(false),
            isNull(),
            captor.capture(),
            any(CancelCallback.class));
    deliverCallback = captor.getValue();
  }

  @AfterEach
  void tearDown() throws IOException, TimeoutException {
    if (service != null) service.stop();
    if (mockedFactory != null) mockedFactory.close();
  }

  @SuppressWarnings("unchecked")
  private Map<Integer, BlockingQueue<SerializableQueueable>> getInternalQueue() throws Exception {
    Field queueField = BatchQueueService.class.getDeclaredField("queue");
    queueField.setAccessible(true);
    return (Map<Integer, BlockingQueue<SerializableQueueable>>) queueField.get(service);
  }

  @SuppressWarnings("unchecked")
  private Map<SerializableQueueable, DeliveryContext> getDeliveryTable() throws Exception {
    Field field = BatchQueueService.class.getDeclaredField("deliveryTable");
    field.setAccessible(true);
    return (Map<SerializableQueueable, DeliveryContext>) field.get(service);
  }

  private Delivery createDelivery(String key, long deliveryTag) throws Exception {
    SerializableQueueable element = new SerializableQueueable(key);
    byte[] json = mapper.writeValueAsBytes(element);
    Envelope envelope = new Envelope(deliveryTag, false, "exchange", "routing");
    return new Delivery(envelope, null, json);
  }

  @Test
  @DisplayName("should deserialize and route delivered message to the correct worker queue")
  void shouldRouteDeliveredMessageToCorrectQueue() throws Exception {
    deliverCallback.handle("consumer-tag", createDelivery("testKey", 1L));

    Map<Integer, BlockingQueue<SerializableQueueable>> internalQueue = getInternalQueue();
    // With workerNumber=1, all elements go to worker 0
    assertEquals(1, internalQueue.get(0).size());
    SerializableQueueable queued = internalQueue.get(0).peek();
    assertNotNull(queued);
    assertEquals("testKey", queued.getUniqueElementKey());
  }

  @Test
  @DisplayName("should populate deliveryTable with delivery context for each consumed message")
  void shouldPopulateDeliveryTable() throws Exception {
    deliverCallback.handle("consumer-tag", createDelivery("key1", 42L));

    Map<SerializableQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
    assertEquals(1, deliveryTable.size());

    SerializableQueueable element = new SerializableQueueable("key1");
    DeliveryContext ctx = deliveryTable.get(element);
    assertNotNull(ctx, "DeliveryContext should exist for the delivered element");
    assertEquals(42L, ctx.getTag());
    assertSame(consumerChannel, ctx.getDeliveryChannel());
  }

  @Test
  @DisplayName("should trigger batch processing when queue exceeds maxSize")
  void shouldTriggerBatchProcessingWhenQueueExceedsMaxSize() throws Exception {
    CountDownLatch performCalled = new CountDownLatch(1);
    when(queueExecution.perform(anyList()))
        .thenAnswer(
            inv -> {
              performCalled.countDown();
              return inv.getArgument(0);
            });

    // maxSize=2, so queue.size() > 2 (i.e., 3 elements) triggers processBufferedBatch
    deliverCallback.handle("tag", createDelivery("a", 1L));
    deliverCallback.handle("tag", createDelivery("b", 2L));
    deliverCallback.handle("tag", createDelivery("c", 3L));

    assertTrue(
        performCalled.await(5, TimeUnit.SECONDS),
        "processBufferedBatch should be triggered when queue exceeds maxSize");
  }

  @Test
  @DisplayName("should propagate exception when message contains malformed JSON")
  void shouldPropagateExceptionOnMalformedJson() {
    Envelope envelope = new Envelope(1L, false, "exchange", "routing");
    Delivery delivery =
        new Delivery(envelope, null, "not valid json".getBytes(StandardCharsets.UTF_8));

    assertThrows(IOException.class, () -> deliverCallback.handle("tag", delivery));
  }

  /** Jackson-compatible Queueable for delivery callback tests. */
  static class SerializableQueueable implements Queueable {
    @JsonProperty("key")
    private String key;

    @JsonCreator
    SerializableQueueable(@JsonProperty("key") String key) {
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
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SerializableQueueable that = (SerializableQueueable) o;
      return Objects.equals(key, that.key);
    }
  }
}
