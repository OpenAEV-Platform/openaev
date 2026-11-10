package io.openaev.engine;

import io.openaev.config.EngineConfig;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.service.EsIndexingUtils;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Repair pass of a boot-time index reset performed while other instances may still run (a rolling
 * deploy): re-asserts the model's epoch cursor once the longest possible stale sync round has
 * drained.
 *
 * <p>The engine drivers reset an index by wiping and recreating it, then writing an epoch cursor so
 * the incremental sync re-feeds it from PostgreSQL. A pod running this version persists its cursor
 * as a compare-and-set on the value it read ({@link EsIndexingUtils#persistCursor}), so a round
 * that read the row before the reset cannot land after it. A pod running an OLDER version persists
 * unconditionally: a round of it in flight from before the reset migration committed until after
 * the recreate moves the cursor from epoch to its stale value, and the recreated index then
 * permanently skips every row before that cursor - silently, on the model the reset was meant to
 * repair. Nothing in the old code can be changed, so the pod that performed the reset re-asserts
 * epoch after a delay longer than any round can last ({@link
 * EngineConfig#getIndexingResetEpochReassertDelaySeconds()}), through a conditional update that
 * never replaces a newer reset request and never re-creates a deleted row. Re-feeding the first
 * minutes of the rebuild a second time is idempotent (upserts).
 *
 * <p>Only resets requested through the sentinel or in-process ({@link
 * EsIndexingUtils#isRollingDeployReset}) are repaired: a missing row is a fresh install or a
 * single-instance reset, with no older pod to guard against, and every model of a fresh install
 * must not be re-fed twice. The pass becomes unnecessary once every pod runs a version with the
 * compare-and-set; it can then be disabled with a delay of 0.
 *
 * <p>The same pass also follows an <b>in-place re-feed</b> requested by a migration ({@link
 * EsIndexingUtils#requestRefeedAtStartup}): the migration moved the cursor back to epoch without
 * wiping the index, and the pods still running the previous version take part in that re-feed with
 * their own document building. Once the application is ready, the pod that applied the migration
 * schedules one more epoch re-assertion per re-fed model, after {@link
 * EngineConfig#getIndexingRefeedReassertDelaySeconds()} - long enough for the old pods to be gone -
 * so the index is re-fed once more by new pods only. That listener only reads the in-process
 * registrations and schedules: the database is touched by the scheduled task alone.
 */
@Component
@Slf4j
public class IndexResetEpochReassertion {

  private final EngineConfig config;
  private final IndexingStatusRepository indexingStatusRepository;
  private final ScheduledExecutorService scheduler;

  public IndexResetEpochReassertion(
      EngineConfig config, IndexingStatusRepository indexingStatusRepository) {
    this.config = config;
    this.indexingStatusRepository = indexingStatusRepository;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "index-reset-epoch-reassertion");
              thread.setDaemon(true);
              return thread;
            });
  }

  /**
   * Schedules the epoch re-assertion of a model whose index was just wiped and recreated, after the
   * reset delay ({@link EngineConfig#getIndexingResetEpochReassertDelaySeconds()}).
   *
   * @param modelName the engine model name (the {@code indexing_status_type})
   * @return true when the re-assertion was scheduled, false when the delay disables it
   */
  public boolean schedule(String modelName) {
    return schedule(modelName, config.getIndexingResetEpochReassertDelaySeconds());
  }

  /**
   * Schedules the epoch re-assertion of a model after the given delay.
   *
   * @param modelName the engine model name (the {@code indexing_status_type})
   * @param delaySeconds the delay before the re-assertion; 0 or less disables it
   * @return true when the re-assertion was scheduled, false when the delay disables it
   */
  public boolean schedule(String modelName, long delaySeconds) {
    if (delaySeconds <= 0) {
      log.info("Epoch re-assertion of {} is disabled", modelName);
      return false;
    }
    log.info(
        "Index {} re-fed while other instances may still run: its epoch cursor will be re-asserted in {}s to repair what a pod running an older version wrote meanwhile",
        modelName,
        delaySeconds);
    scheduler.schedule(() -> reassertEpoch(modelName), delaySeconds, TimeUnit.SECONDS);
    return true;
  }

  /**
   * Schedules, once the application is ready, the second re-feed of every model whose in-place
   * re-feed a migration of this startup requested ({@link EsIndexingUtils#requestRefeedAtStartup}).
   * Flyway has run by then, so every request of this startup is registered.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void scheduleRequestedRefeeds() {
    long delaySeconds = config.getIndexingRefeedReassertDelaySeconds();
    for (String modelName : EsIndexingUtils.drainRefeedRequests()) {
      schedule(modelName, delaySeconds);
    }
  }

  /**
   * Re-asserts the epoch cursor of a model now, unless its row carries a new reset request or is
   * missing. Never throws: a failure is logged and the residual is the documented ops workaround.
   *
   * @param modelName the engine model name
   * @return true when the cursor was re-asserted (the incremental sync re-feeds from epoch again)
   */
  public boolean reassertEpoch(String modelName) {
    try {
      int written =
          indexingStatusRepository.reassertCursorUnlessResetRequested(
              modelName, Instant.EPOCH, EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD);
      if (written == 0) {
        log.info(
            "Epoch cursor of {} not re-asserted: the row is missing or requests a new reset",
            modelName);
        return false;
      }
      log.info(
          "Epoch cursor of {} re-asserted after its reset: the incremental sync re-feeds the index from epoch, a stale cursor written meanwhile by a pod running an older version is repaired",
          modelName);
      return true;
    } catch (RuntimeException e) {
      log.error("Epoch cursor of {} could not be re-asserted after its reset", modelName, e);
      return false;
    }
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
