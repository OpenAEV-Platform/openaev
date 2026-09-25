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
   * Schedules the epoch re-assertion of a model whose index was just wiped and recreated.
   *
   * @param modelName the engine model name (the {@code indexing_status_type})
   */
  public void schedule(String modelName) {
    long delaySeconds = config.getIndexingResetEpochReassertDelaySeconds();
    if (delaySeconds <= 0) {
      log.info("Epoch re-assertion after the reset of {} is disabled", modelName);
      return;
    }
    log.info(
        "Index {} reset while other instances may still run: its epoch cursor will be re-asserted in {}s to repair a stale cursor write of a pod running an older version",
        modelName,
        delaySeconds);
    scheduler.schedule(() -> reassertEpoch(modelName), delaySeconds, TimeUnit.SECONDS);
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
