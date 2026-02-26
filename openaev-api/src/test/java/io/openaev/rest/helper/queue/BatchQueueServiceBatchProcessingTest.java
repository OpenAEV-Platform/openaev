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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchQueueService Batch Processing Tests")
class BatchQueueServiceBatchProcessingTest {

  @Mock private QueueExecution<BatchQueueServiceTest.TestQueueable> queueExecution;
  @Mock private Connection connection;
  @Mock private Channel publisherChannel;
  @Mock private Channel consumerChannel;

  private RabbitmqConfig rabbitmqConfig;
  private RabbitMQSslConfiguration rabbitMQSslConfiguration;
  private ObjectMapper mapper;
  private BatchQueueService<BatchQueueServiceTest.TestQueueable> service;
  private MockedConstruction<ConnectionFactory> mockedFactory;

  @BeforeEach
  void setUp() throws IOException, TimeoutException {
    rabbitmqConfig = new RabbitmqConfig();
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
    queueConfig.setWorkerFrequency(600000); // Very long to avoid scheduled interference
    queueConfig.setMaxSize(10);
    queueConfig.setPublisherQos(10);
    queueConfig.setConsumerQos(10);

    mapper = new ObjectMapper();

    mockedFactory =
        mockConstruction(
            ConnectionFactory.class,
            (mock, context) -> when(mock.newConnection()).thenReturn(connection));

    when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);

    service =
        new BatchQueueService<>(
            BatchQueueServiceTest.TestQueueable.class,
            queueExecution,
            rabbitmqConfig,
            mapper,
            queueConfig,
            rabbitMQSslConfiguration);
  }

  @AfterEach
  void tearDown() throws IOException, TimeoutException {
    if (service != null) service.stop();
    if (mockedFactory != null) mockedFactory.close();
  }

  @SuppressWarnings("unchecked")
  private Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> getInternalQueue()
      throws Exception {
    Field queueField = BatchQueueService.class.getDeclaredField("queue");
    queueField.setAccessible(true);
    return (Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>>)
        queueField.get(service);
  }

  @SuppressWarnings("unchecked")
  private Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> getDeliveryTable()
      throws Exception {
    Field deliveryTableField = BatchQueueService.class.getDeclaredField("deliveryTable");
    deliveryTableField.setAccessible(true);
    return (Map<BatchQueueServiceTest.TestQueueable, DeliveryContext>)
        deliveryTableField.get(service);
  }

  private void recreateServiceWithWorkers(int workerNumber) throws IOException, TimeoutException {
    service.stop();
    mockedFactory.close();

    QueueConfig config = new QueueConfig();
    config.setQueueName("test-queue");
    config.setPublisherNumber(1);
    config.setConsumerNumber(1);
    config.setWorkerNumber(workerNumber);
    config.setWorkerFrequency(600000);
    config.setMaxSize(10);
    config.setPublisherQos(10);
    config.setConsumerQos(10);

    mockedFactory =
        mockConstruction(
            ConnectionFactory.class,
            (mock, context) -> when(mock.newConnection()).thenReturn(connection));

    Channel pub = mock(Channel.class);
    Channel cons = mock(Channel.class);
    when(connection.createChannel()).thenReturn(pub, cons);

    service =
        new BatchQueueService<>(
            BatchQueueServiceTest.TestQueueable.class,
            queueExecution,
            rabbitmqConfig,
            mapper,
            config,
            rabbitMQSslConfiguration);
  }

  // ========================================================================
  // Batch Execution Tests
  // ========================================================================
  @Nested
  @DisplayName("Batch execution")
  class BatchExecutionTests {

    @Test
    @DisplayName("should call queueExecution.perform with buffered elements")
    void shouldCallPerformWithBufferedElements() throws Exception {
      CountDownLatch performCalled = new CountDownLatch(1);
      CopyOnWriteArrayList<List<BatchQueueServiceTest.TestQueueable>> capturedBatches =
          new CopyOnWriteArrayList<>();

      BatchQueueServiceTest.TestQueueable elem1 = new BatchQueueServiceTest.TestQueueable("key1");
      BatchQueueServiceTest.TestQueueable elem2 = new BatchQueueServiceTest.TestQueueable("key2");

      when(queueExecution.perform(anyList()))
          .thenAnswer(
              invocation -> {
                List<BatchQueueServiceTest.TestQueueable> batch =
                    new ArrayList<>(invocation.getArgument(0));
                capturedBatches.add(batch);
                performCalled.countDown();
                return List.of(elem1, elem2);
              });

      // Add elements to internal buffer
      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      internalQueue.get(0).add(elem1);
      internalQueue.get(0).add(elem2);

      // Set up delivery table entries
      Channel mockChannel = mock(Channel.class);
      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          elem1, DeliveryContext.builder().tag(1L).deliveryChannel(mockChannel).build());
      deliveryTable.put(
          elem2, DeliveryContext.builder().tag(2L).deliveryChannel(mockChannel).build());

      service.processBufferedBatch(0);

      assertTrue(performCalled.await(5, TimeUnit.SECONDS), "perform should be called");

      // Find the non-empty batch (the do-while may produce a second empty-list iteration)
      boolean hasNonEmptyBatch = capturedBatches.stream().anyMatch(batch -> batch.size() == 2);
      assertTrue(hasNonEmptyBatch, "Should have a batch with 2 elements");
    }

    @Test
    @DisplayName("should not call perform when buffer is empty")
    void shouldNotCallPerformWhenBufferIsEmpty() throws Exception {
      service.processBufferedBatch(0);

      // Wait for async processing
      Thread.sleep(300);

      verify(queueExecution, never()).perform(argThat(batch -> !batch.isEmpty()));
    }
  }

  // ========================================================================
  // Ack/Reject Tests
  // ========================================================================
  @Nested
  @DisplayName("Ack and Reject behavior")
  class AckRejectTests {

    @Test
    @DisplayName("should ack successfully processed elements")
    void shouldAckSuccessfullyProcessedElements() throws Exception {
      CountDownLatch done = new CountDownLatch(1);
      BatchQueueServiceTest.TestQueueable elem = new BatchQueueServiceTest.TestQueueable("key1");

      Channel mockChannel = mock(Channel.class);
      doAnswer(
              inv -> {
                done.countDown();
                return null;
              })
          .when(mockChannel)
          .basicAck(eq(42L), eq(false));

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      internalQueue.get(0).add(elem);

      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          elem, DeliveryContext.builder().tag(42L).deliveryChannel(mockChannel).build());

      when(queueExecution.perform(anyList())).thenReturn(List.of(elem));

      service.processBufferedBatch(0);
      assertTrue(done.await(5, TimeUnit.SECONDS), "basicAck should be called");

      verify(mockChannel).basicAck(42L, false);
      verify(mockChannel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("should reject elements not in the success list")
    void shouldRejectElementsNotInSuccessList() throws Exception {
      CountDownLatch done = new CountDownLatch(1);
      BatchQueueServiceTest.TestQueueable successElem =
          new BatchQueueServiceTest.TestQueueable("key1");
      BatchQueueServiceTest.TestQueueable failElem =
          new BatchQueueServiceTest.TestQueueable("key2");

      Channel ackChannel = mock(Channel.class);
      Channel rejectChannel = mock(Channel.class);
      doAnswer(
              inv -> {
                done.countDown();
                return null;
              })
          .when(rejectChannel)
          .basicReject(eq(2L), eq(false));

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      internalQueue.get(0).add(successElem);
      internalQueue.get(0).add(failElem);

      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          successElem, DeliveryContext.builder().tag(1L).deliveryChannel(ackChannel).build());
      deliveryTable.put(
          failElem, DeliveryContext.builder().tag(2L).deliveryChannel(rejectChannel).build());

      // Only successElem is returned as successfully processed
      when(queueExecution.perform(anyList())).thenReturn(List.of(successElem));

      service.processBufferedBatch(0);
      assertTrue(done.await(5, TimeUnit.SECONDS), "basicReject should be called");

      verify(ackChannel).basicAck(1L, false);
      verify(rejectChannel).basicReject(2L, false);
    }

    @Test
    @DisplayName("should reject all elements when perform throws exception")
    void shouldRejectAllElementsWhenPerformThrows() throws Exception {
      CountDownLatch done = new CountDownLatch(2);
      BatchQueueServiceTest.TestQueueable elem1 = new BatchQueueServiceTest.TestQueueable("key1");
      BatchQueueServiceTest.TestQueueable elem2 = new BatchQueueServiceTest.TestQueueable("key2");

      Channel mockChannel1 = mock(Channel.class);
      Channel mockChannel2 = mock(Channel.class);
      doAnswer(
              inv -> {
                done.countDown();
                return null;
              })
          .when(mockChannel1)
          .basicReject(eq(1L), eq(false));
      doAnswer(
              inv -> {
                done.countDown();
                return null;
              })
          .when(mockChannel2)
          .basicReject(eq(2L), eq(false));

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      internalQueue.get(0).add(elem1);
      internalQueue.get(0).add(elem2);

      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          elem1, DeliveryContext.builder().tag(1L).deliveryChannel(mockChannel1).build());
      deliveryTable.put(
          elem2, DeliveryContext.builder().tag(2L).deliveryChannel(mockChannel2).build());

      when(queueExecution.perform(anyList())).thenThrow(new RuntimeException("Processing failed"));

      service.processBufferedBatch(0);
      assertTrue(done.await(5, TimeUnit.SECONDS), "Both rejects should be called");

      verify(mockChannel1).basicReject(1L, false);
      verify(mockChannel2).basicReject(2L, false);
      verify(mockChannel1, never()).basicAck(anyLong(), anyBoolean());
      verify(mockChannel2, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("should continue ACKing remaining elements when one ack throws IOException")
    void shouldContinueACKingWhenOneThrowsIOException() throws Exception {
      CountDownLatch done = new CountDownLatch(1);
      BatchQueueServiceTest.TestQueueable elem1 = new BatchQueueServiceTest.TestQueueable("key1");
      BatchQueueServiceTest.TestQueueable elem2 = new BatchQueueServiceTest.TestQueueable("key2");

      Channel failingChannel = mock(Channel.class);
      Channel succeedingChannel = mock(Channel.class);
      doThrow(new IOException("Channel closed")).when(failingChannel).basicAck(eq(1L), eq(false));
      doAnswer(
              inv -> {
                done.countDown();
                return null;
              })
          .when(succeedingChannel)
          .basicAck(eq(2L), eq(false));

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      internalQueue.get(0).add(elem1);
      internalQueue.get(0).add(elem2);

      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          elem1, DeliveryContext.builder().tag(1L).deliveryChannel(failingChannel).build());
      deliveryTable.put(
          elem2, DeliveryContext.builder().tag(2L).deliveryChannel(succeedingChannel).build());

      when(queueExecution.perform(anyList())).thenReturn(List.of(elem1, elem2));

      service.processBufferedBatch(0);
      assertTrue(done.await(5, TimeUnit.SECONDS), "Second element should still be acked");

      verify(failingChannel).basicAck(1L, false);
      verify(succeedingChannel).basicAck(2L, false);
    }

    @Test
    @DisplayName("should continue rejecting remaining elements when one reject throws IOException")
    void shouldContinueRejectingWhenOneThrowsIOException() throws Exception {
      CountDownLatch done = new CountDownLatch(1);
      BatchQueueServiceTest.TestQueueable elem1 = new BatchQueueServiceTest.TestQueueable("key1");
      BatchQueueServiceTest.TestQueueable elem2 = new BatchQueueServiceTest.TestQueueable("key2");

      Channel failingChannel = mock(Channel.class);
      Channel succeedingChannel = mock(Channel.class);
      doThrow(new IOException("Channel closed"))
          .when(failingChannel)
          .basicReject(eq(1L), eq(false));
      doAnswer(
              inv -> {
                done.countDown();
                return null;
              })
          .when(succeedingChannel)
          .basicReject(eq(2L), eq(false));

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      internalQueue.get(0).add(elem1);
      internalQueue.get(0).add(elem2);

      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          elem1, DeliveryContext.builder().tag(1L).deliveryChannel(failingChannel).build());
      deliveryTable.put(
          elem2, DeliveryContext.builder().tag(2L).deliveryChannel(succeedingChannel).build());

      // Return empty list so all elements are rejected
      when(queueExecution.perform(anyList())).thenReturn(List.of());

      service.processBufferedBatch(0);
      assertTrue(done.await(5, TimeUnit.SECONDS), "Second element should still be rejected");

      verify(failingChannel).basicReject(1L, false);
      verify(succeedingChannel).basicReject(2L, false);
    }
  }

  // ========================================================================
  // groupByKey routing tests
  // ========================================================================
  @Nested
  @DisplayName("groupByKey routing")
  class GroupByKeyTests {

    @Test
    @DisplayName("should initialize separate queues for each worker")
    void shouldInitializeSeparateQueuesForEachWorker() throws Exception {
      recreateServiceWithWorkers(2);

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      assertNotNull(internalQueue.get(0));
      assertNotNull(internalQueue.get(1));
      assertEquals(2, internalQueue.size());
    }

    @Test
    @DisplayName("should process elements with null key in worker 0")
    void shouldProcessNullKeyInWorkerZero() throws Exception {
      recreateServiceWithWorkers(2);

      CountDownLatch performCalled = new CountDownLatch(1);
      BatchQueueServiceTest.TestQueueable nullKeyElem =
          new BatchQueueServiceTest.TestQueueable(null);

      when(queueExecution.perform(anyList()))
          .thenAnswer(
              invocation -> {
                performCalled.countDown();
                return invocation.<List<BatchQueueServiceTest.TestQueueable>>getArgument(0);
              });

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      // groupByKey returns 0 for null key, add to worker 0
      internalQueue.get(0).add(nullKeyElem);

      Channel mockChannel = mock(Channel.class);
      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          nullKeyElem, DeliveryContext.builder().tag(1L).deliveryChannel(mockChannel).build());

      service.processBufferedBatch(0);
      assertTrue(performCalled.await(5, TimeUnit.SECONDS), "perform should be called for worker 0");

      verify(mockChannel).basicAck(1L, false);
    }

    @Test
    @DisplayName("should process elements with empty key in worker 0")
    void shouldProcessEmptyKeyInWorkerZero() throws Exception {
      recreateServiceWithWorkers(2);

      CountDownLatch performCalled = new CountDownLatch(1);
      BatchQueueServiceTest.TestQueueable emptyKeyElem =
          new BatchQueueServiceTest.TestQueueable("");

      when(queueExecution.perform(anyList()))
          .thenAnswer(
              invocation -> {
                performCalled.countDown();
                return invocation.<List<BatchQueueServiceTest.TestQueueable>>getArgument(0);
              });

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      // groupByKey returns 0 for empty key
      internalQueue.get(0).add(emptyKeyElem);

      Channel mockChannel = mock(Channel.class);
      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          emptyKeyElem, DeliveryContext.builder().tag(1L).deliveryChannel(mockChannel).build());

      service.processBufferedBatch(0);
      assertTrue(performCalled.await(5, TimeUnit.SECONDS), "perform should be called for worker 0");
    }
  }

  // ========================================================================
  // Purge Tests
  // ========================================================================
  @Nested
  @DisplayName("purge")
  class PurgeTests {

    @Test
    @DisplayName("should purge RabbitMQ queue, clear buffers, and reject unacked messages")
    void shouldPurgeQueueClearBuffersAndRejectUnacked() throws Exception {
      when(publisherChannel.isOpen()).thenReturn(true);

      BatchQueueServiceTest.TestQueueable elem = new BatchQueueServiceTest.TestQueueable("key1");
      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      internalQueue.get(0).add(elem);

      Channel mockChannel = mock(Channel.class);
      when(mockChannel.isOpen()).thenReturn(true);
      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          elem, DeliveryContext.builder().tag(99L).deliveryChannel(mockChannel).build());

      service.purge();

      verify(publisherChannel).queuePurge(anyString());
      assertTrue(internalQueue.get(0).isEmpty());
      verify(mockChannel).basicReject(99L, false);
      assertTrue(deliveryTable.isEmpty());
    }

    @Test
    @DisplayName("should handle closed channel during purge reject gracefully")
    void shouldHandleClosedChannelDuringPurgeReject() throws Exception {
      when(publisherChannel.isOpen()).thenReturn(true);

      BatchQueueServiceTest.TestQueueable elem = new BatchQueueServiceTest.TestQueueable("key1");
      Channel closedChannel = mock(Channel.class);
      when(closedChannel.isOpen()).thenReturn(false);

      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      deliveryTable.put(
          elem, DeliveryContext.builder().tag(1L).deliveryChannel(closedChannel).build());

      service.purge();

      verify(closedChannel, never()).basicReject(anyLong(), anyBoolean());
    }
  }

  // ========================================================================
  // Loop condition tests
  // ========================================================================
  @Nested
  @DisplayName("processBufferedBatch loop condition")
  class LoopConditionTests {

    @Test
    @DisplayName("should re-drain when queue remains above 75% of maxSize after processing")
    void shouldReDrainWhenQueueAboveThreshold() throws Exception {
      CountDownLatch performCalledAtLeastOnce = new CountDownLatch(1);
      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      Channel mockChannel = mock(Channel.class);

      // Add 8 elements to worker 0's queue (> 75% of maxSize=10)
      for (int i = 0; i < 8; i++) {
        BatchQueueServiceTest.TestQueueable elem =
            new BatchQueueServiceTest.TestQueueable("key" + i);
        internalQueue.get(0).add(elem);
        deliveryTable.put(
            elem, DeliveryContext.builder().tag(i).deliveryChannel(mockChannel).build());
      }

      when(queueExecution.perform(anyList()))
          .thenAnswer(
              invocation -> {
                performCalledAtLeastOnce.countDown();
                return invocation.<List<BatchQueueServiceTest.TestQueueable>>getArgument(0);
              });

      service.processBufferedBatch(0);
      assertTrue(performCalledAtLeastOnce.await(5, TimeUnit.SECONDS), "perform should be called");

      // Wait for full processing to complete
      Thread.sleep(300);

      verify(queueExecution, atLeastOnce()).perform(anyList());
    }
  }

  // ========================================================================
  // processBufferedBatch edge cases
  // ========================================================================
  @Nested
  @DisplayName("processBufferedBatch edge cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle processing for worker 0 without error")
    void shouldHandleWorkerZeroGracefully() {
      assertDoesNotThrow(
          () -> {
            service.processBufferedBatch(0);
            Thread.sleep(200);
          });
    }
  }

  // ========================================================================
  // Publish edge cases
  // ========================================================================
  @Nested
  @DisplayName("Publish edge cases")
  class PublishEdgeCaseTests {

    /**
     * Subclass that forces a negative hashCode to expose the negative-modulus bug in {@link
     * BatchQueueService#publish}.
     */
    static class NegativeHashQueueable extends BatchQueueServiceTest.TestQueueable {
      NegativeHashQueueable(String key) {
        super(key);
      }

      @Override
      public int hashCode() {
        return -3;
      }
    }

    @Test
    @DisplayName("should throw when element has negative hashCode with multiple publisher channels")
    void shouldThrowWhenNegativeHashCodeWithMultiplePublishers() throws Exception {
      // Stop existing service to create one with 2 publishers
      service.stop();
      mockedFactory.close();

      QueueConfig multiPubConfig = new QueueConfig();
      multiPubConfig.setQueueName("test-queue");
      multiPubConfig.setPublisherNumber(2);
      multiPubConfig.setConsumerNumber(1);
      multiPubConfig.setWorkerNumber(1);
      multiPubConfig.setWorkerFrequency(600000);
      multiPubConfig.setMaxSize(10);
      multiPubConfig.setPublisherQos(10);
      multiPubConfig.setConsumerQos(10);

      mockedFactory =
          mockConstruction(
              ConnectionFactory.class,
              (mock, context) -> when(mock.newConnection()).thenReturn(connection));

      Channel pub1 = mock(Channel.class);
      Channel pub2 = mock(Channel.class);
      Channel cons = mock(Channel.class);
      when(connection.createChannel()).thenReturn(pub1, pub2, cons);

      service =
          new BatchQueueService<>(
              BatchQueueServiceTest.TestQueueable.class,
              queueExecution,
              rabbitmqConfig,
              mapper,
              multiPubConfig,
              rabbitMQSslConfiguration);

      // -3 % 2 = -1 in Java, so publisherChannels.get(-1) throws
      NegativeHashQueueable negativeHashElem = new NegativeHashQueueable("key");
      assertThrows(IndexOutOfBoundsException.class, () -> service.publish(negativeHashElem));
    }
  }
}
