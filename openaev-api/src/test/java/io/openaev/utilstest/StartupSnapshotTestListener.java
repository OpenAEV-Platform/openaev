package io.openaev.utilstest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Nested;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

@Slf4j
public class StartupSnapshotTestListener implements TestExecutionListener {

  @Override
  public void afterTestClass(TestContext testContext) throws Exception {
    Class<?> testClass = testContext.getTestClass();

    // Ignoring nested classes
    if (testClass.isAnnotationPresent(Nested.class)) {
      log.info("Skipping restore for @Nested class: {}", testClass.getSimpleName());
      return;
    }

    // Restoring data to startup state
    ApplicationContext context = testContext.getApplicationContext();
    DatabaseSnapshotManager snapshotManager = context.getBean(DatabaseSnapshotManager.class);
    snapshotManager.restoreToSnapshotState();

    log.info("Restore completed after main test class: {}", testClass.getSimpleName());
  }
}
