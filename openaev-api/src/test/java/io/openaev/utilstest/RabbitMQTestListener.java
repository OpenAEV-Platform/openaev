package io.openaev.utilstest;

import io.openaev.rest.helper.queue.BatchQueueService;
import io.openaev.rest.inject.InjectApi;
import io.openaev.service.chaining.QueueChainingService;
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
      context.getBean(InjectApi.class).init();
      context.getBean(QueueChainingService.class).init();
      // Purge stale messages that were requeued by RabbitMQ after the previous connection close
      // Safe because the scheduler's initial delay (workerFrequency) hasn't elapsed yet
      context.getBean(InjectApi.class).getInjectTraceQueueService().purge();
      consumersStopped = false;
      log.info("RabbitMQ consumers reinitialized for class: {}", testClass.getSimpleName());
    }
  }

  @Override
  public void beforeTestMethod(TestContext testContext) throws Exception {
    Class<?> testClass = testContext.getTestClass();
    if (testClass.isAnnotationPresent(KeepRabbit.class)) {
      ApplicationContext context = testContext.getApplicationContext();
      BatchQueueService<?> queueService =
          context.getBean(InjectApi.class).getInjectTraceQueueService();
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
    context.getBean(InjectApi.class).getInjectTraceQueueService().stop();
    context.getBean(QueueChainingService.class).destroy();
    consumersStopped = true;

    log.info("RabbitMQ consumers closed for class: {}", testClass.getSimpleName());
  }
}
