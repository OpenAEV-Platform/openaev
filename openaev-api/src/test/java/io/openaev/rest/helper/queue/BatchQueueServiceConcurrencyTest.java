package io.openaev.rest.helper.queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.QueueConfig;
import io.openaev.driver.RabbitmqDriver;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.service.queue.DeliveryContext;
import io.openaev.service.queue.QueueExecution;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchQueueService Concurrency Tests")
class BatchQueueServiceConcurrencyTest {

  @Mock private RabbitmqDriver rabbitmqDriver;
  @Mock private ConnectionFactory connectionFactory;
  @Mock private Connection connection;
  @Mock private Channel publisherChannel;
  @Mock private Channel consumerChannel;

  private static final String RABBITMQ_PREFIX = "test_";
  private QueueConfig queueConfig;
  private ObjectMapper mapper;
  private BatchQueueService<BatchQueueServiceTest.TestQueueable> service;

  @BeforeEach
  void setUp() {
    queueConfig = new QueueConfig();
    queueConfig.setQueueName("test-queue");
    queueConfig.setPublisherNumber(1);
    queueConfig.setConsumerNumber(1);
    queueConfig.setWorkerNumber(1);
    queueConfig.setWorkerFrequency(60000);
    queueConfig.setMaxSize(10);
    queueConfig.setPublisherQos(10);
    queueConfig.setConsumerQos(10);

    mapper = new ObjectMapper();
  }

  @AfterEach
  void tearDown() throws IOException, TimeoutException {
    if (service != null) {
      service.stop();
    }
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

  private void initService() throws IOException, TimeoutException {
    when(rabbitmqDriver.createBatchConnectionFactory(anyInt())).thenReturn(connectionFactory);
    when(connectionFactory.newConnection()).thenReturn(connection);
    when(connection.createChannel()).thenReturn(publisherChannel, consumerChannel);
    service =
        new BatchQueueService<>(
            BatchQueueServiceTest.TestQueueable.class,
            null,
            RABBITMQ_PREFIX,
            mapper,
            queueConfig,
            rabbitmqDriver);
  }

  // ========================================================================
  // CAS Guard Tests
  // ========================================================================
  @Nested
  @DisplayName("CAS guard (insertInProgress)")
  class CasGuardTests {

    @Test
    @DisplayName("should prevent concurrent batch processing for the same worker")
    void shouldPreventConcurrentProcessing() throws Exception {
      AtomicInteger performCallCount = new AtomicInteger(0);
      CountDownLatch blockPerform = new CountDownLatch(1);
      CountDownLatch performStarted = new CountDownLatch(1);

      QueueExecution<BatchQueueServiceTest.TestQueueable> slowExecution =
          elements -> {
            performCallCount.incrementAndGet();
            performStarted.countDown();
            try {
              // Block until we release the latch
              blockPerform.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              // We can't fail here, we need to stop the background thread instead because it's the
              // executor that would not work as expected, not the test
              Thread.currentThread().interrupt();
            }
            return elements;
          };

      initService();
      service.setQueueExecution(slowExecution);

      // Add elements so perform is actually called
      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      Channel mockChannel = mock(Channel.class);

      BatchQueueServiceTest.TestQueueable elem1 = new BatchQueueServiceTest.TestQueueable("key1");
      internalQueue.get(0).add(elem1);
      deliveryTable.put(
          elem1, DeliveryContext.builder().tag(1L).deliveryChannel(mockChannel).build());

      // First call starts processing
      service.processBufferedBatch(0);

      // Wait for the first perform call to start
      assertTrue(performStarted.await(2, TimeUnit.SECONDS), "First perform should have started");

      // Now add another element and try to process again — should be blocked by CAS
      BatchQueueServiceTest.TestQueueable elem2 = new BatchQueueServiceTest.TestQueueable("key2");
      internalQueue.get(0).add(elem2);
      deliveryTable.put(
          elem2, DeliveryContext.builder().tag(2L).deliveryChannel(mockChannel).build());

      service.processBufferedBatch(0); // This should be a no-op due to CAS guard

      // Release the first processing
      blockPerform.countDown();
      Thread.sleep(300);

      // perform should have been called only once for the first invocation.
      // The second processBufferedBatch was rejected by compareAndSet.
      assertEquals(1, performCallCount.get());
    }
  }

  // ========================================================================
  // Multiple Worker Tests
  // ========================================================================
  @Nested
  @DisplayName("Multiple workers")
  class MultipleWorkerTests {

    @Test
    @DisplayName("should process different workers concurrently")
    void shouldProcessDifferentWorkersConcurrently() throws Exception {
      // Close any existing resources
      if (service != null) {
        service.stop();
      }

      QueueConfig multiWorkerConfig = new QueueConfig();
      multiWorkerConfig.setQueueName("test-queue");
      multiWorkerConfig.setPublisherNumber(1);
      multiWorkerConfig.setConsumerNumber(1);
      multiWorkerConfig.setWorkerNumber(2);
      multiWorkerConfig.setWorkerFrequency(60000);
      multiWorkerConfig.setMaxSize(10);
      multiWorkerConfig.setPublisherQos(10);
      multiWorkerConfig.setConsumerQos(10);

      CountDownLatch worker0Started = new CountDownLatch(1);
      CountDownLatch worker1Started = new CountDownLatch(1);
      CountDownLatch releaseAll = new CountDownLatch(1);
      AtomicInteger performCallCount = new AtomicInteger(0);

      QueueExecution<BatchQueueServiceTest.TestQueueable> blockingExecution =
          elements -> {
            int count = performCallCount.incrementAndGet();
            // Signal which worker started (first call = worker 0, second = worker 1)
            if (count == 1) worker0Started.countDown();
            if (count == 2) worker1Started.countDown();
            try {
              releaseAll.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              // We can't fail here, we need to stop the background thread instead because it's the
              // executor that would not work as expected, not the test
              Thread.currentThread().interrupt();
            }
            return elements;
          };

      Channel pub = mock(Channel.class);
      Channel cons = mock(Channel.class);
      when(rabbitmqDriver.createBatchConnectionFactory(anyInt())).thenReturn(connectionFactory);
      when(connectionFactory.newConnection()).thenReturn(connection);
      when(connection.createChannel()).thenReturn(pub, cons);

      service =
          new BatchQueueService<>(
              BatchQueueServiceTest.TestQueueable.class,
              blockingExecution,
              RABBITMQ_PREFIX,
              mapper,
              multiWorkerConfig,
              rabbitmqDriver);

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      Channel mockCh = mock(Channel.class);

      // Add element to worker 0
      BatchQueueServiceTest.TestQueueable elem0 = new BatchQueueServiceTest.TestQueueable("w0");
      internalQueue.get(0).add(elem0);
      deliveryTable.put(elem0, DeliveryContext.builder().tag(1L).deliveryChannel(mockCh).build());

      // Add element to worker 1
      BatchQueueServiceTest.TestQueueable elem1 = new BatchQueueServiceTest.TestQueueable("w1");
      internalQueue.get(1).add(elem1);
      deliveryTable.put(elem1, DeliveryContext.builder().tag(2L).deliveryChannel(mockCh).build());

      // Trigger both workers
      service.processBufferedBatch(0);
      service.processBufferedBatch(1);

      // Both workers should start concurrently
      boolean bothStarted =
          worker0Started.await(2, TimeUnit.SECONDS) && worker1Started.await(2, TimeUnit.SECONDS);
      assertTrue(bothStarted, "Both workers should run concurrently");

      releaseAll.countDown();
      Thread.sleep(300);

      assertEquals(2, performCallCount.get());
    }
  }

  // ========================================================================
  // Thread Safety of Publish
  // ========================================================================
  @Nested
  @DisplayName("Thread safety of publish")
  class ThreadSafetyPublishTests {

    @Test
    @DisplayName("should handle concurrent publish calls without exceptions")
    void shouldHandleConcurrentPublishCalls() throws Exception {
      initService();

      int threadCount = 10;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      CountDownLatch startGate = new CountDownLatch(1);
      CountDownLatch endGate = new CountDownLatch(threadCount);
      List<Throwable> errors = new CopyOnWriteArrayList<>();

      for (int i = 0; i < threadCount; i++) {
        int index = i;
        executor.submit(
            () -> {
              try {
                startGate.await();
                service.publish(new BatchQueueServiceTest.TestQueueable("key" + index));
              } catch (Exception e) {
                errors.add(e);
              } finally {
                endGate.countDown();
              }
            });
      }

      // Release all threads at once
      startGate.countDown();
      assertTrue(endGate.await(5, TimeUnit.SECONDS), "All publish calls should complete");
      executor.shutdown();

      assertTrue(errors.isEmpty(), "No exceptions expected, but got: " + errors);
    }
  }

  // ========================================================================
  // Stop during processing
  // ========================================================================
  @Nested
  @DisplayName("Stop during processing")
  class StopDuringProcessingTests {

    @Test
    @DisplayName("should complete stop even when a batch is being processed")
    void shouldCompleteStopDuringActiveProcessing() throws Exception {
      CountDownLatch performStarted = new CountDownLatch(1);
      CountDownLatch releasePerform = new CountDownLatch(1);

      QueueExecution<BatchQueueServiceTest.TestQueueable> slowExecution =
          elements -> {
            performStarted.countDown();
            try {
              releasePerform.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              // We can't fail here, we need to stop the background thread instead because it's the
              // executor that would not work as expected, not the test
              Thread.currentThread().interrupt();
            }
            return elements;
          };

      initService();
      service.setQueueExecution(slowExecution);

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      Channel mockChannel = mock(Channel.class);

      BatchQueueServiceTest.TestQueueable elem = new BatchQueueServiceTest.TestQueueable("key1");
      internalQueue.get(0).add(elem);
      deliveryTable.put(
          elem, DeliveryContext.builder().tag(1L).deliveryChannel(mockChannel).build());

      service.processBufferedBatch(0);
      assertTrue(performStarted.await(2, TimeUnit.SECONDS), "Processing should have started");

      // stop() calls executor.shutdownNow() which interrupts running tasks
      releasePerform.countDown();
      assertDoesNotThrow(() -> service.stop());
      service = null; // Prevent tearDown from calling stop() again
    }
  }

  // ========================================================================
  // Purge during processing
  // ========================================================================
  @Nested
  @DisplayName("Purge during processing")
  class PurgeDuringProcessingTests {

    @Test
    @DisplayName("should wait for in-flight batch before purging")
    void shouldWaitForInFlightBatchBeforePurging() throws Exception {
      CountDownLatch performStarted = new CountDownLatch(1);
      CountDownLatch releasePerform = new CountDownLatch(1);
      AtomicInteger performCallCount = new AtomicInteger(0);

      QueueExecution<BatchQueueServiceTest.TestQueueable> slowExecution =
          elements -> {
            performCallCount.incrementAndGet();
            performStarted.countDown();
            try {
              releasePerform.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              // We can't fail here, we need to stop the background thread instead because it's the
              // executor that would not work as expected, not the test
              Thread.currentThread().interrupt();
            }
            return elements;
          };

      initService();
      when(publisherChannel.isOpen()).thenReturn(true);
      service.setQueueExecution(slowExecution);

      Map<Integer, BlockingQueue<BatchQueueServiceTest.TestQueueable>> internalQueue =
          getInternalQueue();
      Map<BatchQueueServiceTest.TestQueueable, DeliveryContext> deliveryTable = getDeliveryTable();
      Channel mockChannel = mock(Channel.class);

      BatchQueueServiceTest.TestQueueable elem = new BatchQueueServiceTest.TestQueueable("key1");
      internalQueue.get(0).add(elem);
      deliveryTable.put(
          elem, DeliveryContext.builder().tag(1L).deliveryChannel(mockChannel).build());

      // Start processing
      service.processBufferedBatch(0);
      assertTrue(performStarted.await(2, TimeUnit.SECONDS), "Processing should have started");

      // Start purge in a separate thread — it should busy-wait on insertInProgress
      CountDownLatch purgeComplete = new CountDownLatch(1);
      Thread purgeThread =
          new Thread(
              () -> {
                try {
                  service.purge();
                  purgeComplete.countDown();
                } catch (IOException e) {
                  // ignore
                }
              });
      purgeThread.start();

      // Give purge time to reach the busy-wait loop
      Thread.sleep(200);
      assertFalse(
          purgeComplete.await(100, TimeUnit.MILLISECONDS),
          "Purge should not complete while batch is in progress");

      // Release the batch processing
      releasePerform.countDown();

      // Now purge should complete
      assertTrue(
          purgeComplete.await(5, TimeUnit.SECONDS), "Purge should complete after batch finishes");
    }
  }
}
