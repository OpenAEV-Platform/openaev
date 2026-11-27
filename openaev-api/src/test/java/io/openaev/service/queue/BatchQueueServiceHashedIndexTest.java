package io.openaev.service.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.QueueConfig;
import io.openaev.driver.RabbitmqDriver;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@code String.hashCode()} is a signed int: one element key in two is negative, and Java's {@code
 * %} keeps the dividend's sign. Both indices this service derives from a hash - the publisher
 * channel in {@code publish} and the worker id in {@code groupByKey} - were computed with that raw
 * modulo, so with more than one publisher they produced a negative index.
 *
 * <p>The existing suite never caught it because it builds the service with the {@link QueueConfig}
 * defaults ({@code publisherNumber=1}, {@code workerNumber=1}), where every remainder is 0. The
 * shipped configuration raises both ({@code openaev.queue-config.inject-trace.publisher-number=2},
 * {@code worker-number=8}), which is where the index arithmetic comes apart.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchQueueService hashed indices")
class BatchQueueServiceHashedIndexTest {

  /** A UUID whose {@code hashCode()} is negative and odd: {@code % 2} is -1 with Java's modulo. */
  private static final String NEGATIVE_ODD_HASH_KEY = "66a8be50-5ddf-44de-b94d-becb43cc62ca";

  /** A UUID whose {@code hashCode()} is negative and not a multiple of 8. */
  private static final String NEGATIVE_HASH_KEY = "a7cdff2d-0bf9-485b-b81e-eeef6b7b923e";

  @Mock private QueueExecution<KeyedElement> queueExecution;
  @Mock private RabbitmqDriver rabbitmqDriver;
  @Mock private ConnectionFactory connectionFactory;
  @Mock private Connection connection;
  @Mock private Channel firstPublisherChannel;
  @Mock private Channel secondPublisherChannel;
  @Mock private Channel consumerChannel;

  private BatchQueueService<KeyedElement> service;

  @BeforeEach
  void setUp() throws IOException, TimeoutException {
    // The inject-trace block shipped in openaev-api/src/main/resources/application.properties
    QueueConfig queueConfig = new QueueConfig();
    queueConfig.setQueueName("inject-trace");
    queueConfig.setPublisherNumber(2);
    queueConfig.setConsumerNumber(8);
    queueConfig.setWorkerNumber(8);
    queueConfig.setMaxSize(200);
    queueConfig.setWorkerFrequency(60_000);
    queueConfig.setPublisherQos(10);
    queueConfig.setConsumerQos(10);

    when(rabbitmqDriver.createBatchConnectionFactory(any(Integer.class)))
        .thenReturn(connectionFactory);
    when(connectionFactory.newConnection()).thenReturn(connection);
    when(connection.createChannel())
        .thenReturn(firstPublisherChannel, secondPublisherChannel, consumerChannel);

    service =
        new BatchQueueService<>(
            KeyedElement.class,
            queueExecution,
            "openaev_",
            new ObjectMapper(),
            queueConfig,
            rabbitmqDriver);
  }

  @AfterEach
  void tearDown() throws IOException, TimeoutException {
    service.stop();
  }

  @Test
  @DisplayName("given_an_element_with_a_negative_hash, should_still_publish_it")
  void given_anElementWithANegativeHash_should_stillPublishIt() throws Exception {
    // Arrange: a callback whose id hashes negative - half of all ids do
    KeyedElement element = new KeyedElement(NEGATIVE_ODD_HASH_KEY);
    assertThat(element.hashCode()).isNegative();

    // Act / Assert: the publish must land on one of the two publisher channels
    assertThatCode(() -> service.publish(element)).doesNotThrowAnyException();
    verify(secondPublisherChannel)
        .basicPublish(anyString(), anyString(), isNull(), any(byte[].class));
  }

  @Test
  @DisplayName("given_an_element_with_a_negative_key_hash, should_pick_a_real_worker")
  void given_anElementWithANegativeKeyHash_should_pickARealWorker() {
    // Arrange: the worker id indexes the fixed-size queue map and its insertInProgress map
    KeyedElement element = new KeyedElement(NEGATIVE_HASH_KEY);
    assertThat(element.getUniqueElementKey().hashCode()).isNegative();

    // Act
    int workerId = ReflectionTestUtils.invokeMethod(service, "groupByKey", element);

    // Assert: a negative id escapes the 0..workerNumber-1 range the queues were built for
    assertThat(workerId).isBetween(0, 7);
  }

  /**
   * Mirrors the {@code @Data} classes queued in production ({@code InjectExecutionCallback}, {@code
   * StepEvent}): the generated hashCode folds over the String fields, so its sign is arbitrary.
   */
  static class KeyedElement implements Queueable {
    private final String elementKey;

    KeyedElement(String elementKey) {
      this.elementKey = elementKey;
    }

    @Override
    public String getUniqueElementKey() {
      return elementKey;
    }

    public String getElementKey() {
      return elementKey;
    }

    @Override
    public int hashCode() {
      return elementKey.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof KeyedElement && elementKey.equals(((KeyedElement) other).elementKey);
    }
  }
}
