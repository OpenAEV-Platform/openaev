package io.openaev.service.finding;

import io.openaev.config.EngineConfig;
import io.openaev.utils.SensitiveValueMaskingUtils;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fills {@code findings.finding_value_display} for the findings that have none (#7893, ADR-017).
 *
 * <p>{@link #fill} runs twice after an upgrade:
 *
 * <ol>
 *   <li>in the migration that adds the column, for every existing finding;
 *   <li>once more, after a delay, on the pod that applied the migration. During a rolling deploy,
 *       the pods still running the previous version keep inserting findings without the column, and
 *       Flyway never runs the migration again. Such a finding stays masked when it is displayed or
 *       indexed, but search, filters, distinct grouping and the finding summary cannot see it.
 * </ol>
 *
 * <p>The second pass waits for the old pods to be gone, with the same delay as the finding index
 * re-feed of #7892 ({@code engine.indexing-refeed-reassert-delay-seconds}); it follows the pattern
 * of {@link io.openaev.engine.IndexResetEpochReassertion}. A delay of 0 disables it, which is safe
 * on a single-instance install: no old pod runs alongside the new one. A rollout longer than the
 * delay, or this pod restarting before it, leaves some rows empty; each gets its value the next
 * time its finding is detected, from the upsert.
 *
 * <p>The value is masked in Java with {@link SensitiveValueMaskingUtils#maskIfNeededFailClosed},
 * the helper every write path uses, never in SQL, so the two cannot drift apart. The fill is a
 * deliberate cross-tenant maintenance over plain JDBC, like the migration: it fills the rows of
 * every tenant and never changes the tenant of a row.
 */
@Component
@Slf4j
public class FindingValueDisplayBackfill {

  static final int BATCH_SIZE = 1000;

  /** Set by the migration during the startup in progress, read once the application is ready. */
  private static final AtomicBoolean DELAYED_FILL_REQUESTED = new AtomicBoolean();

  private final EngineConfig engineConfig;
  private final DataSource dataSource;
  private final ScheduledExecutorService scheduler;

  public FindingValueDisplayBackfill(EngineConfig engineConfig, DataSource dataSource) {
    this.engineConfig = engineConfig;
    this.dataSource = dataSource;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "finding-value-display-backfill");
              thread.setDaemon(true);
              return thread;
            });
  }

  /**
   * Fills the displayed value of every finding that has none. The batches walk the table once in
   * primary key order, each starting after the last id of the previous one, so no batch scans again
   * the rows already filled. On an auto-commit connection each batch commits on its own, so locks
   * stay short. Idempotent: a row filled meanwhile by another writer is left alone.
   *
   * @param connection an auto-commit connection
   * @return the number of findings filled
   */
  public static int fill(Connection connection) throws SQLException {
    String selectSql =
        "SELECT finding_id, finding_type, finding_value FROM findings"
            + " WHERE finding_id > ? AND finding_value_display IS NULL"
            + " ORDER BY finding_id LIMIT "
            + BATCH_SIZE;
    String updateSql =
        "UPDATE findings SET finding_value_display = ?"
            + " WHERE finding_id = ? AND finding_value_display IS NULL";
    int filled = 0;
    try (PreparedStatement select = connection.prepareStatement(selectSql);
        PreparedStatement update = connection.prepareStatement(updateSql)) {
      String lastId = "";
      while (true) {
        int batchSize = 0;
        select.setString(1, lastId);
        try (ResultSet rows = select.executeQuery()) {
          while (rows.next()) {
            lastId = rows.getString("finding_id");
            update.setString(
                1,
                SensitiveValueMaskingUtils.maskIfNeededFailClosed(
                    rows.getString("finding_type"), rows.getString("finding_value")));
            update.setString(2, lastId);
            update.addBatch();
            batchSize++;
          }
        }
        if (batchSize == 0) {
          return filled;
        }
        update.executeBatch();
        filled += batchSize;
      }
    }
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
   * Runs {@link #fill} on a connection of the pool. Never throws: a failure is logged, and the rows
   * left empty are filled the next time their finding is detected.
   */
  void fillNow() {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(true);
      log.info("Filled the displayed value of {} finding(s) after the upgrade", fill(connection));
    } catch (SQLException | RuntimeException e) {
      log.error("Could not fill the displayed value of the findings after the upgrade", e);
    }
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
