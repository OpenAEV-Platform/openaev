package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShutdownService unit tests")
class ShutdownServiceTest {

  @Mock private ApplicationContext context;

  @Test
  @DisplayName("given_firstCall_should_spawnDaemonThreadAndCallPerformShutdown")
  void given_firstCall_should_spawnDaemonThreadAndCallPerformShutdown() throws Exception {
    // Arrange
    CountDownLatch threadReached = new CountDownLatch(1);
    CountDownLatch proceedToExit = new CountDownLatch(1);
    ShutdownService service = spy(new ShutdownService(context));
    doAnswer(
            inv -> {
              threadReached.countDown();
              proceedToExit.await(5, TimeUnit.SECONDS);
              return null;
            })
        .when(service)
        .performShutdown();

    // Act
    service.initiateShutdown();

    // Assert — the daemon thread started and called performShutdown
    assertThat(threadReached.await(5, TimeUnit.SECONDS))
        .as("Shutdown thread should have started and called performShutdown")
        .isTrue();

    Thread shutdownThread = findThreadByName("graceful-shutdown");
    assertThat(shutdownThread).isNotNull();
    assertThat(shutdownThread.isDaemon()).isTrue();

    // Cleanup
    proceedToExit.countDown();
  }

  @Test
  @DisplayName("given_concurrentCalls_should_callPerformShutdownOnlyOnce")
  void given_concurrentCalls_should_callPerformShutdownOnlyOnce() throws Exception {
    // Arrange
    AtomicInteger shutdownCount = new AtomicInteger(0);
    ShutdownService service = spy(new ShutdownService(context));
    doAnswer(
            inv -> {
              shutdownCount.incrementAndGet();
              return null;
            })
        .when(service)
        .performShutdown();

    // Act — launch 10 concurrent calls
    CountDownLatch startGate = new CountDownLatch(1);
    List<Thread> callers = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Thread t =
          new Thread(
              () -> {
                try {
                  startGate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                service.initiateShutdown();
              });
      callers.add(t);
      t.start();
    }
    startGate.countDown(); // release all threads simultaneously

    // Wait for all caller threads to finish
    for (Thread t : callers) {
      t.join(5000);
    }
    // Wait for the daemon thread
    Thread.sleep(300);

    // Assert — performShutdown should have been called exactly once
    assertThat(shutdownCount.get())
        .as("performShutdown must be called exactly once despite concurrent calls")
        .isEqualTo(1);
  }

  private Thread findThreadByName(String name) {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(t -> name.equals(t.getName()))
        .findFirst()
        .orElse(null);
  }
}
