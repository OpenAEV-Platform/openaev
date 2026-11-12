package io.openaev.executors;

import static io.openaev.executors.BatchExecutorContextService.MAX_ATTEMPTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.executors.exception.ExecutorBusyException;
import io.openaev.executors.exception.ExecutorException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchExecutorContextServiceTest {

  private final FakeExecutor executor = new FakeExecutor();

  @AfterEach
  void tearDown() {
    executor.shutdown();
  }

  @Test
  @DisplayName("Retries a busy device until it is free")
  void given_busyDevice_should_retryUntilFree() {
    // Arrange
    executor.busyTimes("device-1", 2);

    // Act
    executor.dispatch(List.of("device-1"));

    // Assert
    await().atMost(Duration.ofSeconds(5)).until(() -> executor.sent.contains("device-1"));
    assertThat(executor.attempts("device-1")).isEqualTo(3);
  }

  @Test
  @DisplayName("Gives up on a device that stays busy")
  void given_alwaysBusyDevice_should_giveUp() {
    // Arrange
    executor.busyTimes("device-1", Integer.MAX_VALUE);

    // Act
    executor.dispatch(List.of("device-1"));

    // Assert
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> executor.attempts("device-1") == MAX_ATTEMPTS);
    await()
        .during(Duration.ofMillis(300))
        .until(() -> executor.attempts("device-1") == MAX_ATTEMPTS);
    assertThat(executor.sent).isEmpty();
  }

  @Test
  @DisplayName("Sends every batch even when one device fails")
  void given_failingDevice_should_sendAllOthers() {
    // Arrange
    executor.failing("device-2");

    // Act
    executor.dispatch(List.of("device-1", "device-2", "device-3", "device-4", "device-5"));

    // Assert
    await().atMost(Duration.ofSeconds(5)).until(() -> executor.sent.size() == 4);
    assertThat(executor.sent)
        .containsExactlyInAnyOrder("device-1", "device-3", "device-4", "device-5");
    assertThat(executor.attempts("device-2")).isEqualTo(1);
  }

  private static class FakeExecutor extends BatchExecutorContextService<String> {

    private final Map<String, Integer> busyLeft = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final Set<String> failing = ConcurrentHashMap.newKeySet();
    private final Queue<String> sent = new ConcurrentLinkedQueue<>();

    void busyTimes(String device, int times) {
      busyLeft.put(device, times);
    }

    void failing(String device) {
      failing.add(device);
    }

    int attempts(String device) {
      return attempts.getOrDefault(device, new AtomicInteger()).get();
    }

    @Override
    protected void send(String device) {
      attempts.computeIfAbsent(device, d -> new AtomicInteger()).incrementAndGet();
      if (failing.contains(device)) {
        throw new ExecutorException("failed", "fake");
      }
      if (busyLeft.merge(device, -1, Integer::sum) >= 0) {
        throw new ExecutorBusyException("busy", "fake");
      }
      sent.add(device);
    }

    @Override
    protected String agentReference(String device) {
      return device;
    }

    @Override
    protected int batchSize() {
      return 2;
    }

    @Override
    protected Duration batchInterval() {
      return Duration.ofMillis(10);
    }

    @Override
    protected Duration retryDelay() {
      return Duration.ofMillis(10);
    }

    @Override
    public void launchExecutorSubprocess(
        Inject inject, Endpoint assetEndpoint, Agent agent, String token) {}

    @Override
    public List<Agent> launchBatchExecutorSubprocess(
        Inject inject, Set<Agent> agents, InjectStatus injectStatus, String token) {
      return List.of();
    }
  }
}
