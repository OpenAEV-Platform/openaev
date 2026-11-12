package io.openaev.executors;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.executors.exception.ExecutorBusyException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the implant through an EDR API in paced batches. Subclasses only say how to send one
 * action; a busy device ({@link ExecutorBusyException}) is retried, any other failure is logged
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

  protected abstract void send(A action);

  protected abstract String agentReference(A action);

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

  protected final void dispatch(List<A> actions) {
    int size = effectiveBatchSize();
    for (int from = 0; from < actions.size(); from += size) {
      List<A> batch = actions.subList(from, Math.min(from + size, actions.size()));
      schedule(
          () -> batch.forEach(action -> trySend(action, 1)),
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

  private void trySend(A action, int attempt) {
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
        schedule(() -> trySend(action, attempt + 1), retryDelay());
        return;
      }
      log.error("Gave up on agent {}: {}", agentReference(action), e.getMessage(), e);
    } catch (RuntimeException e) {
      log.error("Sending to agent {} failed: {}", agentReference(action), e.getMessage(), e);
    }
  }

  private void schedule(Runnable task, Duration delay) {
    scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
  }
}
