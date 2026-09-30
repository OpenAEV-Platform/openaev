package io.openaev.utilstest;

import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.queue.BatchQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

@Slf4j
public class RabbitMQTestListener implements TestExecutionListener {

  private static boolean consumersStopped = false;

  @Override
  public void beforeTestClass(TestContext testContext) throws Exception {
    if (!consumersStopped) {
      return;
    }
    Class<?> testClass = testContext.getTestClass();
    if (testClass.isAnnotationPresent(KeepRabbit.class)) {
      ApplicationContext context = testContext.getApplicationContext();
      // Reinitialize consumers that were stopped by a previous test class
      context.getBean(InjectService.class).initInjectTraceQueue();
      context.getBean(QueueChainingService.class).init();
      // Purge stale messages that were requeued by RabbitMQ after the previous connection close
      // Safe because the scheduler's initial delay (workerFrequency) hasn't elapsed yet
      BatchQueueService<?> queueService = injectTraceQueue(context);
      if (queueService != null) {
        queueService.purge();
      }
      consumersStopped = false;
      log.info("RabbitMQ consumers reinitialized for class: {}", testClass.getSimpleName());
    }
  }

  @Override
  public void beforeTestMethod(TestContext testContext) throws Exception {
    Class<?> testClass = testContext.getTestClass();
    if (testClass.isAnnotationPresent(KeepRabbit.class)) {
      ApplicationContext context = testContext.getApplicationContext();
      BatchQueueService<?> queueService = injectTraceQueue(context);
      if (queueService != null) {
        queueService.purge();
      }
    }
  }

  @Override
  public void afterTestClass(TestContext testContext) throws Exception {
    Class<?> testClass = testContext.getTestClass();

    // Ignoring nested classes
    if (testClass.isAnnotationPresent(KeepRabbit.class)) {
      log.info("Skipping restore for @Nested class: {}", testClass.getSimpleName());
      return;
    }

    // Closing RabbitMQ consumers
    ApplicationContext context = testContext.getApplicationContext();
    BatchQueueService<?> queueService = injectTraceQueue(context);
    if (queueService != null) {
      queueService.stop();
    }
    context.getBean(QueueChainingService.class).destroy();
    consumersStopped = true;

    log.info("RabbitMQ consumers closed for class: {}", testClass.getSimpleName());
  }

  /**
   * The inject trace queue lives on InjectService, which some test classes replace with a mock
   * ({@code @MockitoBean}): their context has no queue, so there is nothing to purge or stop.
   */
  private static BatchQueueService<?> injectTraceQueue(ApplicationContext context) {
    return context.getBean(InjectService.class).getInjectTraceQueueService();
  }
}
