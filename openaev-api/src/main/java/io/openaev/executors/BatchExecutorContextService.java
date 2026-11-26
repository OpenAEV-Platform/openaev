package io.openaev.executors;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Inject;
import io.openaev.executors.exception.ExecutorBusyException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the implant through an EDR API in paced batches. Subclasses only say how to send one
 * action; a busy device ({@link ExecutorBusyException}) is retried, any other failure is reported
 * without stopping the rest of the batch.
 */
@Slf4j
public abstract class BatchExecutorContextService<A> extends ExecutorContextService {

  private static final Duration DEFAULT_BATCH_INTERVAL = Duration.ofSeconds(5);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
  // 14 retries of 30 s stay under the 10 minutes after which an agent that never started times out.
  static final int MAX_ATTEMPTS = 15;
  static final int DEFAULT_BATCH_SIZE = 100;

  private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  /** The action that sends an inject's implant to one agent. */
  public record AgentAction<A>(Agent agent, A action) {}

  private record Delivery<A>(A action, Consumer<String> onFailure) {}

  protected abstract void send(A action);

  protected abstract String agentReference(A action);

  protected abstract ExecutorService executorService();

  /**
   * The configured number of actions per batch. {@code null} or a value under 1 falls back to
   * {@link #defaultBatchSize()}: an absent connector config key is deserialized as {@code null} and
   * overrides the field default.
   */
  protected abstract Integer batchSize();

  protected int defaultBatchSize() {
    return DEFAULT_BATCH_SIZE;
  }

  protected Duration batchInterval() {
    return DEFAULT_BATCH_INTERVAL;
  }

  protected Duration retryDelay() {
    return RETRY_DELAY;
  }

  final int effectiveBatchSize() {
    Integer configured = batchSize();
    return configured != null && configured > 0 ? configured : defaultBatchSize();
  }

  /** Sends actions tied to no inject, such as the implant cleanup: a failure is only logged. */
  protected final void dispatch(List<A> actions) {
    sendInBatches(actions.stream().map(action -> new Delivery<>(action, reason -> {})).toList());
  }

  /** Sends the implant of this inject: a failure ends the agent's execution in error. */
  protected final void dispatch(Inject inject, List<AgentAction<A>> actions) {
    String tenantId = inject.getTenant().getId();
    String injectId = inject.getId();
    List<Delivery<A>> deliveries = new ArrayList<>();
    for (AgentAction<A> agentAction : actions) {
      String agentId = agentAction.agent().getId();
      deliveries.add(
          new Delivery<>(
              agentAction.action(),
              reason -> executorService().reportSendFailure(tenantId, injectId, agentId, reason)));
    }
    sendInBatches(deliveries);
  }

  private void sendInBatches(List<Delivery<A>> deliveries) {
    int size = effectiveBatchSize();
    for (int from = 0; from < deliveries.size(); from += size) {
      List<Delivery<A>> batch = deliveries.subList(from, Math.min(from + size, deliveries.size()));
      schedule(
          () -> batch.forEach(delivery -> trySend(delivery, 1)),
          batchInterval().multipliedBy(from / size));
    }
  }

  @Override
  public void shutdown() {
    scheduler.shutdown();
  }

  @VisibleForTesting
  public void setScheduler(ScheduledExecutorService scheduler) {
    this.scheduler = scheduler;
  }

  private void trySend(Delivery<A> delivery, int attempt) {
    A action = delivery.action();
    try {
      send(action);
    } catch (ExecutorBusyException e) {
      if (attempt < MAX_ATTEMPTS) {
        log.warn(
            "{} on agent {}, retry {}/{} in {}s",
            e.getMessage(),
            agentReference(action),
            attempt,
            MAX_ATTEMPTS - 1,
            retryDelay().toSeconds());
        schedule(() -> trySend(delivery, attempt + 1), retryDelay());
        return;
      }
      log.error("Gave up on agent {}: {}", agentReference(action), e.getMessage(), e);
      delivery
          .onFailure()
          .accept(
              "The device stayed busy for "
                  + retryDelay().multipliedBy(MAX_ATTEMPTS - 1).toMinutes()
                  + " minutes, the payload was not sent: "
                  + e.getMessage());
    } catch (RuntimeException e) {
      log.error("Sending to agent {} failed: {}", agentReference(action), e.getMessage(), e);
      delivery.onFailure().accept("The payload could not be sent: " + e.getMessage());
    }
  }

  private void schedule(Runnable task, Duration delay) {
    scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
  }
}
