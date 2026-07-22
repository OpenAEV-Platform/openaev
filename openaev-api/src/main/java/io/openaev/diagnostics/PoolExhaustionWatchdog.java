package io.openaev.diagnostics;

import com.google.common.annotations.VisibleForTesting;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Always-on, zero-dependency watchdog that detects HikariCP pool exhaustion and logs actionable
 * evidence at ERROR level (visible with production log levels, {@code io.openaev=error}).
 *
 * <p>When the pool is exhausted, every DB-bound code path in the platform is stuck, so nothing can
 * be diagnosed from the outside: HTTP requests time out, scheduler jobs fail, and the only log line
 * is Hikari's generic "Connection is not available". This watchdog samples the Hikari MXBean from
 * its own scheduler thread (it never touches the database, so it keeps working while the pool is
 * saturated) and, once saturation persists across consecutive samples, emits a single ERROR
 * containing:
 *
 * <ul>
 *   <li>the pool statistics (total / active / idle / threads awaiting connection);
 *   <li>a thread dump in which threads currently inside JDBC / Hibernate / Hikari frames — the
 *       actual connection holders — are dumped with their full stack, while all other threads are
 *       summarized on one line each.
 * </ul>
 *
 * <p>Combined with {@code spring.datasource.hikari.leak-detection-threshold} (which logs the
 * acquisition stack of any connection held longer than the threshold), the logs of the next
 * exhaustion episode directly name the code paths pinning the pool.
 *
 * <p>A cooldown bounds the log volume to one dump per {@link #DUMP_COOLDOWN} even if the outage
 * lasts longer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PoolExhaustionWatchdog {

  /** Consecutive saturated samples (5s apart) required before dumping: filters out blips. */
  private static final int SATURATION_SAMPLES_BEFORE_DUMP = 2;

  private static final Duration DUMP_COOLDOWN = Duration.ofSeconds(60);

  /** Full stacks are only printed for suspected holders; cap them to keep the log bounded. */
  private static final int MAX_FRAMES_PER_THREAD = 40;

  private final DataSource dataSource;

  private HikariPoolMXBean pool;
  private boolean unavailable;
  private int consecutiveSaturatedSamples;
  private Instant lastDumpAt = Instant.EPOCH;

  @Scheduled(fixedDelay = 5_000, initialDelay = 30_000)
  public void sample() {
    HikariPoolMXBean poolBean = resolvePool();
    if (poolBean == null) {
      return;
    }
    int total = poolBean.getTotalConnections();
    int active = poolBean.getActiveConnections();
    int awaiting = poolBean.getThreadsAwaitingConnection();
    if (total > 0 && active >= total && awaiting > 0) {
      consecutiveSaturatedSamples++;
      if (consecutiveSaturatedSamples >= SATURATION_SAMPLES_BEFORE_DUMP
          && Instant.now().isAfter(lastDumpAt.plus(DUMP_COOLDOWN))) {
        lastDumpAt = Instant.now();
        log.error(
            "Connection pool exhausted for >{}s (total={}, active={}, idle={}, awaiting={})."
                + " Thread dump with suspected connection holders follows.\n{}",
            (consecutiveSaturatedSamples - 1) * 5,
            total,
            active,
            poolBean.getIdleConnections(),
            awaiting,
            buildThreadDump(Thread.getAllStackTraces()));
      }
    } else {
      consecutiveSaturatedSamples = 0;
    }
  }

  /**
   * Formats the dump so the connection holders stand out: threads with JDBC / Hibernate / Hikari
   * frames get their full stack (they are the ones executing on a borrowed connection), everything
   * else is one line, so the ERROR entry stays readable and bounded.
   */
  @VisibleForTesting
  static String buildThreadDump(Map<Thread, StackTraceElement[]> stacks) {
    StringBuilder holders = new StringBuilder();
    // TreeMap for a stable, name-sorted summary section
    Map<String, String> others = new TreeMap<>();
    int holderCount = 0;
    for (Map.Entry<Thread, StackTraceElement[]> entry : stacks.entrySet()) {
      Thread thread = entry.getKey();
      StackTraceElement[] frames = entry.getValue();
      if (isSuspectedConnectionHolder(frames)) {
        holderCount++;
        holders
            .append("\n--- HOLDER \"")
            .append(thread.getName())
            .append("\" ")
            .append(thread.getState())
            .append('\n');
        int limit = Math.min(frames.length, MAX_FRAMES_PER_THREAD);
        for (int i = 0; i < limit; i++) {
          holders.append("    at ").append(frames[i]).append('\n');
        }
        if (frames.length > limit) {
          holders.append("    ... ").append(frames.length - limit).append(" more\n");
        }
      } else {
        // Thread names are not unique (pool threads share patterns): suffix the id so
        // colliding entries do not overwrite each other and under-report threads.
        others.put(
            thread.getName() + " #" + thread.threadId(),
            thread.getState() + (frames.length > 0 ? " at " + frames[0] : " (no frames)"));
      }
    }
    StringBuilder dump =
        new StringBuilder()
            .append(holderCount)
            .append(" thread(s) inside JDBC/Hibernate/Hikari frames (suspected connection")
            .append(" holders):")
            .append(holders)
            .append("\n--- OTHER THREADS (")
            .append(others.size())
            .append(")\n");
    others.forEach(
        (name, summary) ->
            dump.append('"').append(name).append("\" ").append(summary).append('\n'));
    return dump.toString();
  }

  /**
   * A thread executing inside the Postgres driver, Hibernate's JDBC layer or Hikari's proxy is, by
   * construction, holding (or opening) a database connection right now. Hikari's own housekeeping
   * threads can match too (e.g. the connection adder stuck dialing an unreachable database): that
   * is intentional, their stacks are just as diagnostic as application holders.
   */
  private static boolean isSuspectedConnectionHolder(StackTraceElement[] frames) {
    for (StackTraceElement frame : frames) {
      String className = frame.getClassName();
      if (className.startsWith("org.postgresql.")
          || className.startsWith("org.hibernate.engine.jdbc.")
          || className.startsWith("org.hibernate.resource.jdbc.")
          || className.startsWith("com.zaxxer.hikari.pool.ProxyConnection")
          || className.startsWith("com.zaxxer.hikari.pool.ProxyStatement")
          || className.startsWith("com.zaxxer.hikari.pool.ProxyPreparedStatement")) {
        return true;
      }
    }
    return false;
  }

  private HikariPoolMXBean resolvePool() {
    if (pool != null || unavailable) {
      return pool;
    }
    try {
      if (dataSource.isWrapperFor(HikariDataSource.class)) {
        // May still be null before the pool served its first connection: retried next sample.
        pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
      } else {
        // Non-Hikari datasource (some test setups): nothing to watch, disable permanently.
        unavailable = true;
      }
    } catch (SQLException e) {
      unavailable = true;
      log.warn("Pool exhaustion watchdog disabled, cannot access Hikari pool: {}", e.getMessage());
    }
    return pool;
  }
}
