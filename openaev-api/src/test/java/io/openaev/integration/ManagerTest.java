package io.openaev.integration;

import io.openaev.database.model.*;
import io.openaev.integration.local_fixtures.TestIntegrationFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class ManagerTest {
  @Autowired private TestIntegrationFactory testIntegrationFactory;
  @Autowired private ThreadPoolTaskScheduler taskScheduler;

  @Test
  public void test() {
    Manager mgr = new Manager(List.of(testIntegrationFactory));
  }
}
