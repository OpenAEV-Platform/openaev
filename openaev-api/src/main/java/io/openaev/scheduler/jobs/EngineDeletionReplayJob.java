package io.openaev.scheduler.jobs;

import io.openaev.engine.EngineDeletionJournal;
import io.openaev.engine.facade.EngineService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * Replays journaled entity deletions against the search engine until they age out of the journal
 * retention window.
 *
 * <p>This is the convergence safety net behind the immediate after-commit delete flush: the
 * periodic incremental indexer reads rows from PostgreSQL before bulk-writing documents, so an
 * entity deleted mid-batch gets its document resurrected right after the delete-by-query — and
 * nothing would ever clean it up again (the row is gone from PostgreSQL, no further event fires).
 * Replaying recent deletions on every pass re-deletes any such ghost document within one replay
 * interval. Replays are idempotent delete-by-query calls, so re-processing already-clean ids is
 * cheap.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class EngineDeletionReplayJob implements Job {

  public static final String ENGINE_DELETION_REPLAY_JOB = "engineDeletionReplayJob";
  public static final String ENGINE_DELETION_REPLAY_TRIGGER = "engineDeletionReplayTrigger";

  /** Keeps each delete-by-query terms clause well below engine limits. */
  static final int REPLAY_BATCH_SIZE = 1000;

  private final EngineDeletionJournal deletionJournal;
  private final EngineService engineService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    List<String> pendingIds = deletionJournal.findPendingIds();
    boolean allReplayed = true;
    for (int start = 0; start < pendingIds.size(); start += REPLAY_BATCH_SIZE) {
      List<String> batch =
          pendingIds.subList(start, Math.min(start + REPLAY_BATCH_SIZE, pendingIds.size()));
      try {
        engineService.bulkDelete(batch);
        // Prune aged entries per successful batch: a persistently failing batch elsewhere in the
        // journal must not starve the pruning of entries that did replay.
        deletionJournal.pruneAged(batch);
      } catch (RuntimeException e) {
        // Keep replaying the remaining batches: one transient engine rejection must not starve
        // every other journaled deletion until the next pass.
        allReplayed = false;
        log.error(
            "Engine deletion replay failed for a batch of {} id(s), will retry on the next pass: {}",
            batch.size(),
            e.getMessage(),
            e);
      }
    }
    if (!pendingIds.isEmpty()) {
      log.debug("Replayed {} journaled engine deletion(s)", pendingIds.size());
    }
    if (allReplayed) {
      // Prune only after a fully successful replay: entries older than the retention window have
      // been replayed at least once by this very pass, even after a long platform downtime.
      deletionJournal.prune();
    } else {
      // Poison ids the engine keeps rejecting are dropped past the hard cap so they cannot grow
      // the journal and the replay workload without bound.
      deletionJournal.pruneStale();
    }
  }
}
