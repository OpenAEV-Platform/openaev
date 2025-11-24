package io.openaev.utilstest;

import io.openaev.rest.inject.InjectApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

@Slf4j
public class RabbitMQTestListener implements TestExecutionListener {

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

    log.info("RabbitMQ consumers closed for class : {}", testClass.getSimpleName());
  }
}
