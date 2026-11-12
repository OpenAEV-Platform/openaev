package io.openaev.executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.executors.exception.ExecutorBusyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchExecutorContextServiceTest {

  private RecordingService service;

  @BeforeEach
  void setUp() {
    service = new RecordingService();
    service.setScheduler(immediateScheduler());
  }

  @Test
  @DisplayName("a busy agent is retried until the EDR accepts the call")
  void given_busyAgent_should_retryUntilAccepted() {
    // Arrange: agent-1 is busy twice, then accepts
    service.busyCalls.put("agent-1", 2);

    // Act
    service.dispatchAll(List.of("agent-1", "agent-2"));

    // Assert
    assertEquals(3, service.attempts("agent-1"));
    assertEquals(1, service.attempts("agent-2"));
    assertEquals(List.of("agent-1", "agent-2"), service.sent);
  }

  @Test
  @DisplayName("a busy agent is given up after the last attempt, the rest still dispatched")
  void given_alwaysBusyAgent_should_giveUpAfterLastAttempt() {
    // Arrange: agent-1 stays busy
    service.busyCalls.put("agent-1", Integer.MAX_VALUE);

    // Act
    assertDoesNotThrow(() -> service.dispatchAll(List.of("agent-1", "agent-2")));

    // Assert: 15 attempts in total, then the base class gives up
    assertEquals(15, service.attempts("agent-1"));
    assertEquals(List.of("agent-2"), service.sent);
  }

  @Test
  @DisplayName("any other failure on one agent is logged and the batch goes on")
  void given_failingAgent_should_notRetryAndDispatchTheRest() {
    // Arrange
    service.failing.add("agent-2");

    // Act
    assertDoesNotThrow(() -> service.dispatchAll(List.of("agent-1", "agent-2", "agent-3")));

    // Assert
    assertEquals(1, service.attempts("agent-2"));
    assertEquals(List.of("agent-1", "agent-3"), service.sent);
  }

  private static ScheduledExecutorService immediateScheduler() {
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    doAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(0);
              runnable.run();
              return mock(ScheduledFuture.class);
            })
        .when(scheduler)
        .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    return scheduler;
  }

  private static final class RecordingService extends BatchExecutorContextService<String> {

    private final Map<String, Integer> busyCalls = new HashMap<>();
    private final List<String> failing = new ArrayList<>();
    private final Map<String, Integer> attempts = new HashMap<>();
    private final List<String> sent = new ArrayList<>();

    void dispatchAll(List<String> actions) {
      dispatch(actions);
    }

    int attempts(String agent) {
      return attempts.getOrDefault(agent, 0);
    }

    @Override
    protected void send(String action) {
      attempts.merge(action, 1, Integer::sum);
      int busy = busyCalls.getOrDefault(action, 0);
      if (busy > 0) {
        busyCalls.put(action, busy - 1);
        throw new ExecutorBusyException("device busy", "test-executor");
      }
      if (failing.contains(action)) {
        throw new IllegalStateException("vendor unavailable");
      }
      sent.add(action);
    }

    @Override
    protected String agentReference(String action) {
      return action;
    }

    @Override
    protected int batchSize() {
      return 10;
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
