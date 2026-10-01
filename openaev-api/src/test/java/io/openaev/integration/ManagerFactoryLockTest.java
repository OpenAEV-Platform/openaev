package io.openaev.integration;

import static io.openaev.integration.ManagerFactory.MANAGER_LOCK_TIMEOUT_MS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.aop.lock.LockAcquisitionException;
import io.openaev.aop.lock.LockAspect;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * Exercises the {@code @Lock} on {@link ManagerFactory#getManager} through a real Spring AOP proxy
 * and the real {@link LockAspect}, without a Spring context or a database. A latch stands in for
 * the PostgreSQL row lock of the 2026-10-01 staging incident, where a Manager creation and an
 * inject transaction waited on each other forever.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerFactory lock tests")
class ManagerFactoryLockTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";
  private static final long LATCH_TIMEOUT_MS = MANAGER_LOCK_TIMEOUT_MS + 20_000;

  @Mock private ManagerCreator managerCreator;

  private ManagerFactory managerFactory;
  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new ManagerFactory(managerCreator));
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAspect(new LockAspect());
    managerFactory = proxyFactory.getProxy();
    executor =
        Executors.newCachedThreadPool(
            runnable -> {
              Thread thread = new Thread(runnable);
              thread.setDaemon(true);
              return thread;
            });
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Nested
  @DisplayName("getManager(tenantId) — while another creation is in progress")
  class GetManagerDuringCreation {

    @Test
    @DisplayName("given_creationInProgressForOneTenant_should_notBlockAnotherTenant")
    void given_creationInProgressForOneTenant_should_notBlockAnotherTenant() throws Exception {
      // Arrange — tenant A's creation hangs, as when it waits on a DB row lock
      CountDownLatch creationStarted = new CountDownLatch(1);
      CountDownLatch releaseCreation = new CountDownLatch(1);
      Manager managerA = mock(Manager.class);
      Manager managerB = mock(Manager.class);
      when(managerCreator.createManager(TENANT_A))
          .thenAnswer(
              invocation -> {
                creationStarted.countDown();
                releaseCreation.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                return managerA;
              });
      when(managerCreator.createManager(TENANT_B)).thenReturn(managerB);
      Future<Manager> creationA = executor.submit(() -> managerFactory.getManager(TENANT_A));
      assertThat(creationStarted.await(5, TimeUnit.SECONDS)).isTrue();

      // Act — tenant B asks for its own Manager meanwhile
      Future<Manager> creationB = executor.submit(() -> managerFactory.getManager(TENANT_B));

      // Assert — tenant B is served right away, it does not queue behind tenant A
      assertThat(creationB.get(5, TimeUnit.SECONDS)).isSameAs(managerB);
      releaseCreation.countDown();
      assertThat(creationA.get(5, TimeUnit.SECONDS)).isSameAs(managerA);
    }

    @Test
    @DisplayName("given_waiterHoldsRowNeededByCreation_should_failWaiterInsteadOfDeadlocking")
    void given_waiterHoldsRowNeededByCreation_should_failWaiterInsteadOfDeadlocking()
        throws Exception {
      // Arrange — the creation (ManagerIntegrationsSyncJob registering built-ins) needs a row
      // that the waiter's still-open transaction (an inject execution) has already written
      CountDownLatch creationStarted = new CountDownLatch(1);
      CountDownLatch rowReleased = new CountDownLatch(1);
      Manager managerA = mock(Manager.class);
      when(managerCreator.createManager(TENANT_A))
          .thenAnswer(
              invocation -> {
                creationStarted.countDown();
                rowReleased.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                return managerA;
              });
      Future<Manager> creation = executor.submit(() -> managerFactory.getManager(TENANT_A));
      assertThat(creationStarted.await(5, TimeUnit.SECONDS)).isTrue();

      // Act — the waiter asks for the same tenant's Manager, then ends its transaction
      Future<Throwable> waiter =
          executor.submit(
              () -> {
                try {
                  managerFactory.getManager(TENANT_A);
                  return null;
                } catch (LockAcquisitionException e) {
                  return e;
                } finally {
                  rowReleased.countDown();
                }
              });

      // Assert — the waiter gives up after the lock timeout, which frees the row and lets the
      // creation finish; without a timeout both threads wait on each other forever
      assertThat(waiter.get(MANAGER_LOCK_TIMEOUT_MS + 10_000, TimeUnit.MILLISECONDS))
          .isInstanceOf(LockAcquisitionException.class);
      assertThat(creation.get(5, TimeUnit.SECONDS)).isSameAs(managerA);
    }
  }
}
