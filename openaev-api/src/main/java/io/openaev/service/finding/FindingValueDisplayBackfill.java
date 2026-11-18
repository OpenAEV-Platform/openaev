package io.openaev.service.finding;

import io.openaev.config.EngineConfig;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.FindingRepository.FindingWithoutValueDisplay;
import io.openaev.utils.SensitiveValueMaskingUtils;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fills, once and after a delay, {@code findings.finding_value_display} for the findings that pods
 * of the previous version insert without it during a rolling deploy (#7893, ADR-017).
 *
 * <p>The migration that adds the column fills every existing finding, but the pods still running
 * the previous version keep inserting findings without it, and Flyway never runs the migration
 * again. Such a finding stays masked when it is displayed or indexed, but value search and the
 * finding summary cannot see it. So the migration asks this pod for one more pass ({@link
 * #requestDelayedFill}), once the old pods are gone: the same delay as the finding index re-feed of
 * #7892 ({@code engine.indexing-refeed-reassert-delay-seconds}), following the pattern of {@link
 * io.openaev.engine.IndexResetEpochReassertion}. A delay of 0 disables it, which is safe on a
 * single-instance install: no old pod runs alongside the new one. A rollout longer than the delay,
 * or this pod restarting before it, leaves some rows empty; each gets its value the next time its
 * finding is detected, from the upsert.
 *
 * <p>The value is masked with {@link SensitiveValueMaskingUtils#maskIfNeededFailClosed}, the helper
 * every write path uses. The pass goes through Hibernate in {@link TxCtx#allTenants()}, so the
 * tenant statement inspector applies: it fills the rows of every tenant and never changes the
 * tenant of a row.
 *
 * <p>The migration has its own copy of this loop, on purpose: it runs before Spring starts, so it
 * cannot use a repository, and code outside the migration package may not use raw JDBC ({@code
 * TenantNonOrmAccessArchTest}). Both copies mask with the same helper, so they cannot drift apart.
 */
@Component
@Slf4j
public class FindingValueDisplayBackfill {

  /** Rows per batch, for the migration and the delayed pass alike. */
  public static final int BATCH_SIZE = 1000;

  /** Set by the migration during the startup in progress, read once the application is ready. */
  private static final AtomicBoolean DELAYED_FILL_REQUESTED = new AtomicBoolean();

  private final EngineConfig engineConfig;
  private final FindingRepository findingRepository;
  private final TenantScopedTransaction tenantTx;
  private final ScheduledExecutorService scheduler;

  public FindingValueDisplayBackfill(
      EngineConfig engineConfig,
      FindingRepository findingRepository,
      TenantScopedTransaction tenantTx) {
    this.engineConfig = engineConfig;
    this.findingRepository = findingRepository;
    this.tenantTx = tenantTx;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "finding-value-display-backfill");
              thread.setDaemon(true);
              return thread;
            });
  }

  /** Called by the migration that adds the column, so this pod fills it once more later. */
  public static void requestDelayedFill() {
    DELAYED_FILL_REQUESTED.set(true);
  }

  /**
   * Schedules the delayed fill when the migration requested it during this startup. Flyway has run
   * by then, so the request is registered.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void scheduleRequestedFill() {
    if (!DELAYED_FILL_REQUESTED.getAndSet(false)) {
      return;
    }
    long delaySeconds = engineConfig.getIndexingRefeedReassertDelaySeconds();
    if (delaySeconds <= 0) {
      log.info("Delayed fill of the finding displayed values is disabled");
      return;
    }
    log.info(
        "Findings inserted by pods of the previous version get their displayed value in {}s",
        delaySeconds);
    scheduler.schedule(this::fillNow, delaySeconds, TimeUnit.SECONDS);
  }

  /**
   * Fills the displayed value of every finding that has none. The batches walk the table once in
   * primary key order, each in its own transaction so locks stay short. Never throws: a failure is
   * logged, and the rows left empty are filled the next time their finding is detected.
   */
  void fillNow() {
    long filled = 0;
    try {
      String afterId = "";
      while (true) {
        String from = afterId;
        List<FindingWithoutValueDisplay> batch =
            tenantTx.execute(TxCtx.allTenants(), () -> fillBatchAfter(from));
        filled += batch.size();
        if (batch.size() < BATCH_SIZE) {
          break;
        }
        afterId = batch.getLast().getId();
      }
      log.info("Filled the displayed value of {} finding(s) after the upgrade", filled);
    } catch (RuntimeException e) {
      log.error("Could not fill the displayed value of the findings after the upgrade", e);
    }
  }

  private List<FindingWithoutValueDisplay> fillBatchAfter(String afterId) {
    List<FindingWithoutValueDisplay> findings =
        findingRepository.findWithoutValueDisplayAfter(afterId, BATCH_SIZE);
    for (FindingWithoutValueDisplay finding : findings) {
      findingRepository.fillValueDisplay(
          finding.getId(),
          SensitiveValueMaskingUtils.maskIfNeededFailClosed(finding.getType(), finding.getValue()));
    }
    return findings;
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
