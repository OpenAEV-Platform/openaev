package io.openaev.migration;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.FindingLocationKeys;
import io.openaev.finding.identity.FindingValueNormalizer;
import io.openaev.finding.identity.LocationIdentity;
import io.openaev.finding.identity.LocationIdentity.AssetLocation;
import io.openaev.finding.identity.LocationIdentity.UnknownLocation;
import io.openaev.finding.identity.LocationIdentity.UserLocation;
import io.openaev.service.EsIndexingUtils;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Converts the old-shape findings (one row per detection, no value hash) into one finding per
 * identity: tenant, type, value hash and location, with its occurrences, tags, teams and links.
 *
 * <p>Built for a large table in use. It runs outside a wrapping transaction, so that the WAL it
 * writes grows with the rows it converts:
 *
 * <ol>
 *   <li>catalog-only schema changes, each committed at once;
 *   <li>walk the table in physical order, one committed transaction per batch, so the pages of the
 *       old rows, and of their occurrences and links written with them, are rewritten together;
 *   <li>write each new finding once, with its final dates, computed from the occurrences it
 *       receives, under a time-ordered id, as its occurrences: their rows land on the last pages of
 *       the indexes keyed by these ids;
 *   <li>after each batch, pause, and wait while WAL archiving or a replica is too far behind;
 *   <li>convert the old rows the walk misses (written meanwhile by an older pod), through a partial
 *       index.
 * </ol>
 *
 * <p>Pods of the older version write findings during a rolling update. A batch locks the old rows
 * it reads, so a write of the older version on one of them waits for the batch and lands on a new
 * old row, converted by a later batch. A batch that meets a concurrent delete of a row it links to,
 * or a deadlock, is rolled back and read again (see {@link #commitBatch}).
 *
 * <p>Every row taken is deleted, converted or not, so an interrupted run resumes from the rows
 * left, and a converted table is only read. No {@code VACUUM}: autovacuum reclaims the old rows.
 */
@Slf4j
@Component
public class V6_20261117160000000__Convert_findings_to_identity extends BaseJavaMigration {

  private static final int BATCH_SIZE = 1000;
  static final long UNKNOWN_DENSITY_PAGES_PER_RANGE = 100;
  static final int PAUSE_MILLIS = 50;
  static final long WAL_LAG_BOUND_BYTES = 1_024L * 1_024 * 1_024;
  private static final long MEGABYTE = 1_024L * 1_024;
  private static final Duration LOG_INTERVAL = Duration.ofMinutes(1);

  /**
   * The writes of a batch grow with the locations of its rows (one finding, its occurrences, tags
   * and links per location), not with the rows read: a batch stops before this many locations.
   */
  static final int MAX_LOCATIONS_PER_BATCH = 1000;

  private static final String FINDING_MODEL = "finding";
  private static final String UNCONVERTED_INDEX = "idx_findings_unconverted";

  private static final String READ_COLUMNS =
      "SELECT finding_id, tenant_id, finding_type, finding_value, finding_value_display,"
          + " finding_name, finding_created_at, finding_updated_at FROM findings";

  static final String READ_RANGE =
      READ_COLUMNS
          + " WHERE ctid >= ?::tid AND ctid < ?::tid AND finding_value_hash IS NULL"
          + " ORDER BY ctid LIMIT "
          + BATCH_SIZE
          + " FOR UPDATE";

  private static final String READ_ANY =
      READ_COLUMNS
          + " WHERE finding_value_hash IS NULL ORDER BY finding_id LIMIT "
          + BATCH_SIZE
          + " FOR UPDATE";

  /**
   * The WAL not yet archived or replayed, in bytes. Inactive slots, and the archiver when {@code
   * archive_mode} is off, are left out: they never catch up. The archiver's lag comes from WAL file
   * names, counted from the run's first file (the parameter) while no WAL segment is archived.
   */
  private static final String WAL_LAG_QUERY =
      "SELECT MAX(lag) FROM ("
          + " SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn) AS lag"
          + " FROM pg_stat_replication"
          + " UNION ALL"
          + " SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS lag"
          + " FROM pg_replication_slots WHERE active AND restart_lsn IS NOT NULL"
          + " UNION ALL"
          + " SELECT ((('x' || substr(cur, 9, 8))::bit(32)::bigint"
          + " - ('x' || substr(done, 9, 8))::bit(32)::bigint) * (4294967296 / seg)"
          + " + ('x' || substr(cur, 17, 8))::bit(32)::bigint"
          + " - ('x' || substr(done, 17, 8))::bit(32)::bigint) * seg AS lag"
          + " FROM (SELECT pg_walfile_name(pg_current_wal_lsn()) AS cur,"
          + " COALESCE(CASE WHEN last_archived_wal ~ '^[0-9A-F]{24}$'"
          + " THEN last_archived_wal END, ?) AS done,"
          + " pg_size_bytes(current_setting('wal_segment_size')) AS seg"
          + " FROM pg_stat_archiver WHERE current_setting('archive_mode') <> 'off') archiver"
          + " WHERE done ~ '^[0-9A-F]{24}$') lags";

  private final Long pagesPerRangeOverride;
  private final int pauseMillis;
  private final long walLagBoundBytes;
  private final BatchRetry batchRetry;
  private final TimeOrderedIds ids = new TimeOrderedIds();

  public V6_20261117160000000__Convert_findings_to_identity() {
    this(null, PAUSE_MILLIS, WAL_LAG_BOUND_BYTES);
  }

  V6_20261117160000000__Convert_findings_to_identity(
      Long pagesPerRangeOverride, int pauseMillis, long walLagBoundBytes) {
    this(pagesPerRangeOverride, pauseMillis, walLagBoundBytes, BatchRetry.DEFAULT);
  }

  V6_20261117160000000__Convert_findings_to_identity(
      Long pagesPerRangeOverride, int pauseMillis, long walLagBoundBytes, BatchRetry batchRetry) {
    this.pagesPerRangeOverride = pagesPerRangeOverride;
    this.pauseMillis = pauseMillis;
    this.walLagBoundBytes = walLagBoundBytes;
    this.batchRetry = batchRetry;
  }

  /** {@link #UNKNOWN_DENSITY_PAGES_PER_RANGE} when the table has no statistics yet. */
  static long pagesPerRange(long estimatedRows, long estimatedPages) {
    if (estimatedRows <= 0 || estimatedPages <= 0) {
      return UNKNOWN_DENSITY_PAGES_PER_RANGE;
    }
    double rowsPerPage = (double) estimatedRows / estimatedPages;
    return Math.max(1, Math.round(BATCH_SIZE / Math.max(rowsPerPage, 1.0)));
  }

  /** An old-shape row and what it is linked to. */
  private record OldFinding(
      String id,
      String tenantId,
      String type,
      String value,
      String valueDisplay,
      String name,
      Timestamp createdAt,
      Timestamp updatedAt,
      List<String> assetIds,
      List<String> userIds,
      List<Occurrence> occurrences) {

    List<LocationIdentity> locations() {
      List<LocationIdentity> locations = new ArrayList<>();
      assetIds.forEach(assetId -> locations.add(new AssetLocation(assetId)));
      userIds.forEach(userId -> locations.add(new UserLocation(userId)));
      return locations.isEmpty() ? List.of(UnknownLocation.INSTANCE) : locations;
    }

    Timestamp firstDate() {
      return occurrences.stream().map(Occurrence::date).min(Timestamp::compareTo).orElse(createdAt);
    }

    Timestamp lastDate() {
      return occurrences.stream().map(Occurrence::date).max(Timestamp::compareTo).orElse(updatedAt);
    }
  }

  private record Occurrence(String injectId, Timestamp date, String field) {}

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      changeCatalog(connection, LockPolicy.DEFAULT);
      // Each batch reads the first rows left without a hash: without this index it would scan the
      // rows already converted again. Built without blocking writes; a leftover of an interrupted
      // run can be invalid, so it is rebuilt.
      executeOutsideTransaction(
          connection, "DROP INDEX CONCURRENTLY IF EXISTS " + UNCONVERTED_INDEX);
      executeOutsideTransaction(
          connection,
          "CREATE INDEX CONCURRENTLY "
              + UNCONVERTED_INDEX
              + " ON findings (finding_id) WHERE finding_value_hash IS NULL");

      WalGuard walGuard = new WalGuard(currentWalFile(connection));
      walk(connection, walGuard);
      drain(connection, walGuard);

      executeOutsideTransaction(
          connection, "DROP INDEX CONCURRENTLY IF EXISTS " + UNCONVERTED_INDEX);
      requestReindex(connection);
      connection.commit();
    } catch (Exception e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(autoCommit);
    }
    EsIndexingUtils.requestReindexAtStartup(FINDING_MODEL);
  }

  /**
   * How a catalog step waits for its locks. During a rolling update, pods of the older version read
   * and write findings and injects, and a lock request that waits in the queue of a table makes
   * every later query on it wait behind it. So a step takes its table locks with {@code NOWAIT}: an
   * attempt gets them at once or fails, rolls back, and runs again after a pause (see {@link
   * #pauseAfter}). {@code lockTimeout} bounds any other lock a statement waits for. The step fails
   * after {@code attempts} attempts; the default gives up after two to four and a half minutes.
   */
  record LockPolicy(Duration lockTimeout, int attempts, Duration firstPause) {
    static final LockPolicy DEFAULT =
        new LockPolicy(Duration.ofSeconds(1), 12, Duration.ofSeconds(3));
  }

  /** A table lock a step takes before its statements, granted at once or not at all. */
  record TableLock(String table, String mode) {
    String sql() {
      return "LOCK TABLE " + table + " IN " + mode + " MODE NOWAIT";
    }
  }

  private static final Duration MAX_PAUSE = Duration.ofSeconds(30);

  /**
   * {@code lock_not_available} (a {@code NOWAIT} lock or the lock timeout) and {@code
   * deadlock_detected} (a cycle through a lock outside the step's table locks).
   */
  private static final Set<String> LOCK_CONFLICTS = Set.of("55P03", "40P01");

  void changeCatalog(Connection connection, LockPolicy policy) throws SQLException {
    // Catalog-only changes, each committed at once, so each ACCESS EXCLUSIVE lock lasts
    // milliseconds
    commitUnderLocks(
        connection,
        policy,
        List.of(new TableLock("finding_locations", "ACCESS EXCLUSIVE")),
        "ALTER TABLE finding_locations ALTER COLUMN finding_location_key"
            + " DROP EXPRESSION IF EXISTS");
    // Dropping a foreign key takes ACCESS EXCLUSIVE on the referenced table too, injects here.
    // NOT VALID checks no existing row, so the swap is instant; the validation then scans the
    // table under locks that let reads and writes through, on findings and on injects.
    commitUnderLocks(
        connection,
        policy,
        List.of(
            new TableLock("injects", "ACCESS EXCLUSIVE"),
            new TableLock("findings", "ACCESS EXCLUSIVE")),
        "ALTER TABLE findings DROP CONSTRAINT IF EXISTS finding_inject_id_fk",
        "ALTER TABLE findings ADD CONSTRAINT finding_inject_id_fk"
            + " FOREIGN KEY (finding_inject_id) REFERENCES injects (inject_id)"
            + " ON DELETE SET NULL NOT VALID");
    commitUnderLocks(
        connection,
        policy,
        List.of(
            new TableLock("injects", "ROW SHARE"),
            new TableLock("findings", "SHARE UPDATE EXCLUSIVE")),
        "ALTER TABLE findings VALIDATE CONSTRAINT finding_inject_id_fk");
  }

  /**
   * Takes the locks, runs the statements in one transaction and commits it. A lock conflict rolls
   * the transaction back and runs it again after a pause.
   *
   * @throws SQLException the error of a statement that is not a lock conflict, or the lock error
   *     itself when the rollback fails (the rollback failure is suppressed in it)
   * @throws IllegalStateException after the last attempt, with the last lock error as its cause
   */
  void commitUnderLocks(
      Connection connection, LockPolicy policy, List<TableLock> locks, String... statements)
      throws SQLException {
    List<String> steps = new ArrayList<>();
    locks.forEach(lock -> steps.add(lock.sql()));
    steps.addAll(List.of(statements));
    for (int attempt = 1; ; attempt++) {
      String running = null;
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET LOCAL lock_timeout = '" + policy.lockTimeout().toMillis() + "ms'");
        for (String sql : steps) {
          running = sql;
          statement.execute(sql);
        }
        connection.commit();
        return;
      } catch (SQLException e) {
        rollback(connection, e);
        if (!LOCK_CONFLICTS.contains(e.getSQLState())) {
          throw e;
        }
        if (attempt >= policy.attempts()) {
          throw new IllegalStateException(giveUpMessage(attempt, running, locks), e);
        }
        Duration pause =
            pauseAfter(attempt, policy.firstPause(), ThreadLocalRandom.current().nextDouble());
        log.info(
            "Findings migration: \"{}\" met a lock another session holds ({}), attempt {}/{},"
                + " retrying in {} ms",
            running,
            e.getSQLState(),
            attempt,
            policy.attempts(),
            pause.toMillis());
        pauseBeforeRetry(pause);
      }
    }
  }

  private static void rollback(Connection connection, SQLException failure) throws SQLException {
    try {
      connection.rollback();
    } catch (SQLException rollbackFailure) {
      failure.addSuppressed(rollbackFailure);
      throw failure;
    }
  }

  private String giveUpMessage(int attempts, String failed, List<TableLock> locks) {
    String tables = String.join(", ", locks.stream().map(TableLock::table).toList());
    return ("Findings migration: gave up after %d attempts on \"%s\": another session holds a lock"
                + " on %s, for example a pod of the older version. ")
            .formatted(attempts, failed, tables)
        + repairHint();
  }

  private String repairHint() {
    return ("Stop the pods of the older version, remove the failed row of this migration with"
            + " `flyway repair` on the schema history table `migrations`, or with `DELETE FROM"
            + " migrations WHERE version = '%s' AND NOT success`, then start again: every step is"
            + " safe to run again.")
        .formatted(getVersion().getVersion());
  }

  /**
   * The pause after the failed attempt {@code failedAttempt} (from 1). Its ceiling doubles from
   * {@code firstPause} up to {@link #MAX_PAUSE}; {@code random}, from 0 to 1, draws it in the upper
   * half of the ceiling, so pods that retry together spread out. Never under {@code firstPause}.
   */
  static Duration pauseAfter(int failedAttempt, Duration firstPause, double random) {
    long ceiling =
        Math.min(firstPause.toMillis() << Math.min(failedAttempt - 1, 30), MAX_PAUSE.toMillis());
    long drawn = ceiling / 2 + Math.round(random * (ceiling - ceiling / 2));
    return Duration.ofMillis(Math.max(firstPause.toMillis(), drawn));
  }

  private static void pauseBeforeRetry(Duration pause) {
    try {
      Thread.sleep(pause.toMillis());
    } catch (InterruptedException e) {
      // Stop here: every step is safe to run again on the next start
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Findings migration interrupted", e);
    }
  }

  /**
   * Walks the pages the table holds at the start, range by range; a range is read again until no
   * old row is left in it. Commits each batch.
   */
  void walk(Connection connection, WalGuard walGuard) throws SQLException {
    TableSize size = tableSize(connection);
    long pagesPerRange =
        pagesPerRangeOverride != null
            ? pagesPerRangeOverride
            : pagesPerRange(size.estimatedRows(), size.estimatedPages());
    log.info(
        "findings: {} page(s) on disk, about {} row(s), {} page(s) per range",
        size.pages(),
        size.estimatedRows(),
        pagesPerRange);
    long taken = 0;
    long startedAt = System.nanoTime();
    long nextLogAt = startedAt + LOG_INTERVAL.toNanos();
    try (PreparedStatement range = connection.prepareStatement(READ_RANGE)) {
      for (long page = 0; page < size.pages(); page += pagesPerRange) {
        range.setString(1, tid(page));
        range.setString(2, tid(page + pagesPerRange));
        int batch;
        while ((batch =
                commitBatch(
                    connection, () -> convertBatch(connection, readBatch(connection, range))))
            > 0) {
          taken += batch;
          pace(connection, walGuard);
        }
        if (System.nanoTime() - nextLogAt >= 0) {
          log.info(
              "findings: page {} of {} walked, {} old row(s) taken",
              Math.min(page + pagesPerRange, size.pages()),
              size.pages(),
              taken);
          nextLogAt = System.nanoTime() + LOG_INTERVAL.toNanos();
        }
      }
    }
    connection.commit();
    log.info(
        "findings: {} old row(s) taken in physical order, {} s",
        taken,
        Duration.ofNanos(System.nanoTime() - startedAt).toSeconds());
  }

  /** Converts the rows the walk misses, in id order, one committed batch at a time. */
  void drain(Connection connection, WalGuard walGuard) throws SQLException {
    while (commitBatch(connection, () -> convertNextBatch(connection) ? 1 : 0) > 0) {
      pace(connection, walGuard);
    }
  }

  /** How a batch that meets a concurrent write runs again. */
  record BatchRetry(int attempts, Duration firstPause) {
    static final BatchRetry DEFAULT = new BatchRetry(5, Duration.ofSeconds(1));
  }

  /**
   * {@code foreign_key_violation}: a pod of the older version deleted a row the batch links to,
   * such as an inject of a deleted simulation or an asset, after the batch read it. {@code
   * deadlock_detected}: the batch and a concurrent write lock the same rows in another order.
   */
  private static final Set<String> BATCH_CONFLICTS = Set.of("23503", "40P01");

  /** Reads and converts one batch, without committing. */
  @FunctionalInterface
  interface Batch {
    int convert() throws SQLException;
  }

  /**
   * Converts one batch and commits it. A conflict with a concurrent write rolls the batch back, and
   * the batch is read again after a pause (see {@link #pauseAfter}): what it reads then reflects
   * the write.
   *
   * @return the rows the batch took
   * @throws SQLException the error of a batch that is not a conflict, or the conflict itself when
   *     the rollback fails (the rollback failure is suppressed in it)
   * @throws IllegalStateException after the last attempt, with the last conflict as its cause
   */
  int commitBatch(Connection connection, Batch batch) throws SQLException {
    for (int attempt = 1; ; attempt++) {
      try {
        int taken = batch.convert();
        connection.commit();
        return taken;
      } catch (SQLException e) {
        rollback(connection, e);
        if (!BATCH_CONFLICTS.contains(e.getSQLState())) {
          throw e;
        }
        if (attempt >= batchRetry.attempts()) {
          throw new IllegalStateException(
              ("Findings migration: gave up after %d attempts on a batch: each one met a"
                          + " concurrent write (%s, %s), for example a pod of the older version"
                          + " deleting a simulation. ")
                      .formatted(attempt, e.getSQLState(), e.getMessage())
                  + repairHint(),
              e);
        }
        Duration pause =
            pauseAfter(attempt, batchRetry.firstPause(), ThreadLocalRandom.current().nextDouble());
        log.info(
            "Findings migration: a batch met a concurrent write ({}), attempt {}/{}, reading it"
                + " again in {} ms",
            e.getSQLState(),
            attempt,
            batchRetry.attempts(),
            pause.toMillis());
        pauseBeforeRetry(pause);
      }
    }
  }

  /**
   * Converts the first batch of old-shape rows left in id order, without committing: the rows the
   * walk misses, written meanwhile by an older pod.
   *
   * @return false when no row is left
   */
  boolean convertNextBatch(Connection connection) throws SQLException {
    try (PreparedStatement any = connection.prepareStatement(READ_ANY)) {
      return convertBatch(connection, readBatch(connection, any)) > 0;
    }
  }

  /**
   * Converts the rows of {@code read} within the budget of a batch. Each step runs once for the
   * whole batch: the identities and dates are computed in Java, the writes are set-based. Every row
   * taken is deleted, converted or not, so the next read takes the rows left.
   *
   * @return the number of rows taken, 0 when {@code read} is empty
   */
  private int convertBatch(Connection connection, List<OldFinding> read) throws SQLException {
    if (read.isEmpty()) {
      return 0;
    }
    List<OldFinding> batch = withinBudget(read);
    List<Conversion> conversions = conversions(batch);
    if (!conversions.isEmpty()) {
      convert(connection, conversions);
    }
    Set<String> convertedIds = new HashSet<>();
    conversions.forEach(conversion -> convertedIds.add(conversion.row().id()));
    List<String> unconvertedIds =
        batch.stream().map(OldFinding::id).filter(id -> !convertedIds.contains(id)).toList();
    if (!unconvertedIds.isEmpty()) {
      deleteFindings(connection, unconvertedIds);
    }
    return batch.size();
  }

  // A row is converted whole: one alone over the budget makes a batch of its own
  private static List<OldFinding> withinBudget(List<OldFinding> rows) {
    List<OldFinding> batch = new ArrayList<>();
    int locations = 0;
    for (OldFinding row : rows) {
      locations += row.locations().size();
      if (!batch.isEmpty() && locations > MAX_LOCATIONS_PER_BATCH) {
        break;
      }
      batch.add(row);
    }
    return batch;
  }

  private void convert(Connection connection, List<Conversion> conversions) throws SQLException {
    // 1 - the identity of each (row, location)
    Map<String, String> locationIdByKey = locationIds(connection, conversions);
    List<Identity> identities = new ArrayList<>();
    for (Conversion conversion : conversions) {
      OldFinding row = conversion.row();
      identities.add(
          new Identity(
              row.tenantId(),
              row.type(),
              conversion.hash(),
              locationIdByKey.get(conversion.key())));
    }
    // 2 - the stable finding of each identity: the one already there, else a new one
    Map<Identity, Target> targets = existingTargets(connection, new LinkedHashSet<>(identities));
    readHeldOccurrences(connection, targets.values());
    List<NewOccurrence> occurrences = new ArrayList<>();
    List<String> oldIds = new ArrayList<>();
    List<String> targetIds = new ArrayList<>();
    List<String[]> assetLinks = new ArrayList<>();
    List<String[]> userLinks = new ArrayList<>();
    Map<String, String> firstTargetByRow = new LinkedHashMap<>();
    for (int i = 0; i < conversions.size(); i++) {
      Conversion conversion = conversions.get(i);
      Target target =
          targets.computeIfAbsent(identities.get(i), identity -> new Target(ids.next(), false));
      target.take(conversion.row());
      target.receive(conversion.row(), occurrences);
      oldIds.add(conversion.row().id());
      targetIds.add(target.id);
      firstTargetByRow.putIfAbsent(conversion.row().id(), target.id);
      switch (conversion.location()) {
        case AssetLocation(String assetId) -> assetLinks.add(new String[] {target.id, assetId});
        case UserLocation(String userId) -> userLinks.add(new String[] {target.id, userId});
        case UnknownLocation unknown -> {}
      }
    }
    // 3 - write the findings with their final dates, pour the old rows into them, then delete
    // the old rows
    writeTargets(connection, targets);
    insertOccurrences(connection, occurrences);
    Array oldIdArray = varchars(connection, oldIds);
    Array targetIdArray = varchars(connection, targetIds);
    copyLinks(connection, "findings_tags", "tag_id", oldIdArray, targetIdArray);
    copyLinks(connection, "findings_teams", "team_id", oldIdArray, targetIdArray);
    linkLocations(connection, "findings_assets", "asset_id", assetLinks);
    linkLocations(connection, "findings_users", "user_id", userLinks);
    moveAndDelete(connection, firstTargetByRow);
  }

  // -- READ --

  // The statement selects READ_COLUMNS, its parameters set
  private static List<OldFinding> readBatch(Connection connection, PreparedStatement statement)
      throws SQLException {
    Map<String, Object[]> rows = new LinkedHashMap<>();
    try (ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        rows.put(
            resultSet.getString(1),
            new Object[] {
              resultSet.getString(2),
              resultSet.getString(3),
              resultSet.getString(4),
              resultSet.getString(5),
              resultSet.getString(6),
              resultSet.getTimestamp(7),
              resultSet.getTimestamp(8)
            });
      }
    }
    if (rows.isEmpty()) {
      return List.of();
    }
    Array ids = connection.createArrayOf("varchar", rows.keySet().toArray());
    Map<String, List<String>> assetIds = readLinks(connection, "findings_assets", "asset_id", ids);
    Map<String, List<String>> userIds = readLinks(connection, "findings_users", "user_id", ids);
    Map<String, List<Occurrence>> occurrences = readOccurrences(connection, ids);
    List<OldFinding> batch = new ArrayList<>();
    rows.forEach(
        (id, row) ->
            batch.add(
                new OldFinding(
                    id,
                    (String) row[0],
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    (Timestamp) row[5],
                    (Timestamp) row[6],
                    assetIds.getOrDefault(id, List.of()),
                    userIds.getOrDefault(id, List.of()),
                    occurrences.getOrDefault(id, List.of()))));
    return batch;
  }

  private static Map<String, List<String>> readLinks(
      Connection connection, String table, String column, Array findingIds) throws SQLException {
    Map<String, List<String>> links = new HashMap<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT DISTINCT finding_id, "
                + column
                + " FROM "
                + table
                + " WHERE finding_id = ANY (?) ORDER BY 1, 2")) {
      statement.setArray(1, findingIds);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          links
              .computeIfAbsent(resultSet.getString(1), id -> new ArrayList<>())
              .add(resultSet.getString(2));
        }
      }
    }
    return links;
  }

  private static Map<String, List<Occurrence>> readOccurrences(
      Connection connection, Array findingIds) throws SQLException {
    Map<String, List<Occurrence>> occurrences = new HashMap<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT finding_occurrence_finding_id, finding_occurrence_inject_id,"
                + " finding_occurrence_date, finding_occurrence_field FROM finding_occurrences"
                + " WHERE finding_occurrence_finding_id = ANY (?)")) {
      statement.setArray(1, findingIds);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          occurrences
              .computeIfAbsent(resultSet.getString(1), id -> new ArrayList<>())
              .add(
                  new Occurrence(
                      resultSet.getString(2), resultSet.getTimestamp(3), resultSet.getString(4)));
        }
      }
    }
    return occurrences;
  }

  // -- CONVERT --

  /** One location of an old row, and the identity hash of its value there. */
  private record Conversion(OldFinding row, LocationIdentity location, String hash, String key) {}

  private record Identity(String tenantId, String type, String hash, String locationId) {}

  /** The stable finding of one identity, and the value and dates the batch brings to it. */
  private static final class Target {
    private final String id;
    private final boolean existing;
    private String value;
    private String valueDisplay;
    private String name;
    private Timestamp firstSeen;
    private Timestamp lastSeen;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    // The occurrences the finding holds: their injects, their first and last dates
    private final Set<String> heldInjects = new HashSet<>();
    private Timestamp firstOccurrence;
    private Timestamp lastOccurrence;

    private Target(String id, boolean existing) {
      this.id = id;
      this.existing = existing;
    }

    // A row detected after what the finding last saw gives it its value and name; the dates widen
    private void take(OldFinding row) {
      if (lastSeen == null || !row.lastDate().before(lastSeen)) {
        value = row.value();
        valueDisplay = row.valueDisplay();
        name = row.name();
      }
      firstSeen = min(firstSeen, row.firstDate());
      lastSeen = max(lastSeen, row.lastDate());
      createdAt = min(createdAt, row.createdAt());
      updatedAt = max(updatedAt, row.updatedAt());
    }

    private void hold(String injectId, Timestamp date) {
      if (injectId != null) {
        heldInjects.add(injectId);
      }
      firstOccurrence = min(firstOccurrence, date);
      lastOccurrence = max(lastOccurrence, date);
    }

    // An inject the finding holds is skipped: on a merge, the occurrence first written wins
    private void receive(OldFinding row, List<NewOccurrence> occurrences) {
      for (Occurrence occurrence : row.occurrences()) {
        if (occurrence.injectId() == null || !heldInjects.contains(occurrence.injectId())) {
          hold(occurrence.injectId(), occurrence.date());
          occurrences.add(new NewOccurrence(id, occurrence));
        }
      }
    }

    // From the occurrences the finding holds; without any, from the dates of its rows
    private Timestamp firstSeen() {
      return firstOccurrence != null ? firstOccurrence : firstSeen;
    }

    private Timestamp lastSeen() {
      return lastOccurrence != null ? lastOccurrence : lastSeen;
    }
  }

  private record NewOccurrence(String findingId, Occurrence occurrence) {}

  // A row whose type or value cannot be hashed is left out, before anything is written
  private static List<Conversion> conversions(List<OldFinding> batch) {
    List<Conversion> conversions = new ArrayList<>();
    for (OldFinding row : batch) {
      try {
        ContractOutputType type = ContractOutputType.valueOf(row.type());
        List<Conversion> ofRow = new ArrayList<>();
        for (LocationIdentity location : row.locations()) {
          ofRow.add(
              new Conversion(
                  row,
                  location,
                  FindingValueNormalizer.hash(type, row.value(), location),
                  FindingLocationKeys.keyOf(assetId(location), userId(location))));
        }
        conversions.addAll(ofRow);
      } catch (IllegalArgumentException e) {
        log.warn("Finding {} deleted, it cannot be converted: {}", row.id(), e.getMessage());
      }
    }
    return conversions;
  }

  private static Map<String, String> locationIds(
      Connection connection, List<Conversion> conversions) throws SQLException {
    Map<String, LocationIdentity> locationByKey = new LinkedHashMap<>();
    conversions.forEach(
        conversion -> locationByKey.putIfAbsent(conversion.key(), conversion.location()));
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO finding_locations (finding_location_id, finding_location_asset_id,"
                + " finding_location_user_id, finding_location_key) VALUES (?, ?, ?, ?)"
                + " ON CONFLICT DO NOTHING")) {
      for (Map.Entry<String, LocationIdentity> entry : locationByKey.entrySet()) {
        insert.setString(1, UUID.randomUUID().toString());
        insert.setString(2, assetId(entry.getValue()));
        insert.setString(3, userId(entry.getValue()));
        insert.setString(4, entry.getKey());
        insert.addBatch();
      }
      insert.executeBatch();
    }
    Map<String, String> locationIdByKey = new HashMap<>();
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT finding_location_key, finding_location_id FROM finding_locations"
                + " WHERE finding_location_key = ANY (?)")) {
      select.setArray(1, varchars(connection, locationByKey.keySet()));
      try (ResultSet resultSet = select.executeQuery()) {
        while (resultSet.next()) {
          locationIdByKey.put(resultSet.getString(1), resultSet.getString(2));
        }
      }
    }
    return locationIdByKey;
  }

  private static Map<Identity, Target> existingTargets(
      Connection connection, Set<Identity> identities) throws SQLException {
    List<String> tenantIds = new ArrayList<>();
    List<String> types = new ArrayList<>();
    List<String> hashes = new ArrayList<>();
    List<String> locationIds = new ArrayList<>();
    for (Identity identity : identities) {
      tenantIds.add(identity.tenantId());
      types.add(identity.type());
      hashes.add(identity.hash());
      locationIds.add(identity.locationId());
    }
    Map<Identity, Target> targets = new HashMap<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT f.finding_id, f.tenant_id, f.finding_type, f.finding_value_hash,"
                + " f.finding_location_id, f.finding_value, f.finding_value_display,"
                + " f.finding_name, f.finding_first_seen, f.finding_last_seen"
                + " FROM findings f JOIN unnest(?, ?, ?, ?) AS i(tenant_id, finding_type,"
                + " finding_value_hash, finding_location_id)"
                + " ON f.tenant_id = i.tenant_id AND f.finding_type = i.finding_type"
                + " AND f.finding_value_hash = i.finding_value_hash"
                + " AND f.finding_location_id = i.finding_location_id")) {
      statement.setArray(1, varchars(connection, tenantIds));
      statement.setArray(2, varchars(connection, types));
      statement.setArray(3, varchars(connection, hashes));
      statement.setArray(4, varchars(connection, locationIds));
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          Target target = new Target(resultSet.getString(1), true);
          target.value = resultSet.getString(6);
          target.valueDisplay = resultSet.getString(7);
          target.name = resultSet.getString(8);
          target.firstSeen = resultSet.getTimestamp(9);
          target.lastSeen = resultSet.getTimestamp(10);
          targets.put(
              new Identity(
                  resultSet.getString(2),
                  resultSet.getString(3),
                  resultSet.getString(4),
                  resultSet.getString(5)),
              target);
        }
      }
    }
    return targets;
  }

  private static void readHeldOccurrences(Connection connection, Collection<Target> existing)
      throws SQLException {
    if (existing.isEmpty()) {
      return;
    }
    Map<String, Target> byId = new HashMap<>();
    existing.forEach(target -> byId.put(target.id, target));
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT finding_occurrence_finding_id, finding_occurrence_inject_id,"
                + " finding_occurrence_date FROM finding_occurrences"
                + " WHERE finding_occurrence_finding_id = ANY (?)")) {
      statement.setArray(1, varchars(connection, byId.keySet()));
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          byId.get(resultSet.getString(1)).hold(resultSet.getString(2), resultSet.getTimestamp(3));
        }
      }
    }
  }

  private static void writeTargets(Connection connection, Map<Identity, Target> targets)
      throws SQLException {
    try (PreparedStatement insert =
            connection.prepareStatement(
                "INSERT INTO findings (finding_id, tenant_id, finding_type, finding_value,"
                    + " finding_value_display, finding_name, finding_value_hash,"
                    + " finding_location_id, finding_created_at, finding_updated_at,"
                    + " finding_first_seen, finding_last_seen)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?)");
        PreparedStatement update =
            connection.prepareStatement(
                "UPDATE findings SET finding_value = ?, finding_value_display = ?,"
                    + " finding_name = ?, finding_first_seen = ?, finding_last_seen = ?,"
                    + " finding_updated_at = now() WHERE finding_id = ?")) {
      for (Map.Entry<Identity, Target> entry : targets.entrySet()) {
        Identity identity = entry.getKey();
        Target target = entry.getValue();
        if (target.existing) {
          update.setString(1, target.value);
          update.setString(2, target.valueDisplay);
          update.setString(3, target.name);
          update.setTimestamp(4, target.firstSeen());
          update.setTimestamp(5, target.lastSeen());
          update.setString(6, target.id);
          update.addBatch();
        } else {
          insert.setString(1, target.id);
          insert.setString(2, identity.tenantId());
          insert.setString(3, identity.type());
          insert.setString(4, target.value);
          insert.setString(5, target.valueDisplay);
          insert.setString(6, target.name);
          insert.setString(7, identity.hash());
          insert.setString(8, identity.locationId());
          insert.setTimestamp(9, target.createdAt);
          insert.setTimestamp(10, target.firstSeen());
          insert.setTimestamp(11, target.lastSeen());
          insert.addBatch();
        }
      }
      insert.executeBatch();
      update.executeBatch();
    }
  }

  // The dates travel as ISO instants: a timestamp array has no time zone of its own
  private void insertOccurrences(Connection connection, List<NewOccurrence> occurrences)
      throws SQLException {
    if (occurrences.isEmpty()) {
      return;
    }
    List<String> occurrenceIds = new ArrayList<>();
    List<String> findingIds = new ArrayList<>();
    List<String> injectIds = new ArrayList<>();
    List<String> dates = new ArrayList<>();
    List<String> fields = new ArrayList<>();
    for (NewOccurrence row : occurrences) {
      occurrenceIds.add(ids.next());
      findingIds.add(row.findingId());
      injectIds.add(row.occurrence().injectId());
      dates.add(row.occurrence().date().toInstant().toString());
      fields.add(row.occurrence().field());
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO finding_occurrences (finding_occurrence_id, finding_occurrence_finding_id,"
                + " finding_occurrence_inject_id, finding_occurrence_date,"
                + " finding_occurrence_field, finding_occurrence_created_at)"
                + " SELECT o.id, o.finding_id, o.inject_id, o.date::timestamptz, o.field, now()"
                + " FROM unnest(?, ?, ?, ?, ?) AS o(id, finding_id, inject_id, date, field)"
                + " ON CONFLICT (finding_occurrence_finding_id, finding_occurrence_inject_id)"
                + " DO NOTHING")) {
      statement.setArray(1, varchars(connection, occurrenceIds));
      statement.setArray(2, varchars(connection, findingIds));
      statement.setArray(3, varchars(connection, injectIds));
      statement.setArray(4, varchars(connection, dates));
      statement.setArray(5, varchars(connection, fields));
      statement.executeUpdate();
    }
  }

  private static void copyLinks(
      Connection connection, String table, String column, Array oldIds, Array targetIds)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO "
                + table
                + " (finding_id, "
                + column
                + ") SELECT DISTINCT m.target_id, l."
                + column
                + " FROM unnest(?, ?) AS m(old_id, target_id) JOIN "
                + table
                + " l ON l.finding_id = m.old_id ON CONFLICT DO NOTHING")) {
      statement.setArray(1, oldIds);
      statement.setArray(2, targetIds);
      statement.executeUpdate();
    }
  }

  private static void linkLocations(
      Connection connection, String table, String column, List<String[]> links)
      throws SQLException {
    if (links.isEmpty()) {
      return;
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO "
                + table
                + " (finding_id, "
                + column
                + ") SELECT DISTINCT * FROM unnest(?, ?) ON CONFLICT DO NOTHING")) {
      statement.setArray(1, varchars(connection, links.stream().map(link -> link[0]).toList()));
      statement.setArray(2, varchars(connection, links.stream().map(link -> link[1]).toList()));
      statement.executeUpdate();
    }
  }

  // CONCURRENTLY refuses to run inside a transaction block
  private static void executeOutsideTransaction(Connection connection, String sql)
      throws SQLException {
    connection.setAutoCommit(true);
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } finally {
      connection.setAutoCommit(false);
    }
  }

  private static void deleteFindings(Connection connection, List<String> findingIds)
      throws SQLException {
    try (PreparedStatement delete =
        connection.prepareStatement("DELETE FROM findings WHERE finding_id = ANY (?)")) {
      delete.setArray(1, varchars(connection, findingIds));
      delete.executeUpdate();
    }
  }

  private static void moveAndDelete(Connection connection, Map<String, String> firstTargetByRow)
      throws SQLException {
    Array oldIds = varchars(connection, firstTargetByRow.keySet());
    try (PreparedStatement results =
            connection.prepareStatement(
                "UPDATE phishing_results pr SET phishing_result_finding = m.target_id,"
                    + " phishing_result_finding_occurrence = ("
                    + "SELECT moved.finding_occurrence_id FROM finding_occurrences previous"
                    + " JOIN finding_occurrences moved"
                    + " ON moved.finding_occurrence_inject_id = previous.finding_occurrence_inject_id"
                    + " AND moved.finding_occurrence_finding_id = m.target_id"
                    + " WHERE previous.finding_occurrence_id"
                    + " = pr.phishing_result_finding_occurrence)"
                    + " FROM unnest(?, ?) AS m(old_id, target_id)"
                    + " WHERE pr.phishing_result_finding = m.old_id");
        PreparedStatement delete =
            connection.prepareStatement("DELETE FROM findings WHERE finding_id = ANY (?)")) {
      results.setArray(1, oldIds);
      results.setArray(2, varchars(connection, firstTargetByRow.values()));
      results.executeUpdate();
      delete.setArray(1, oldIds);
      delete.executeUpdate();
    }
  }

  private static String assetId(LocationIdentity location) {
    return location instanceof AssetLocation(String id) ? id : null;
  }

  private static String userId(LocationIdentity location) {
    return location instanceof UserLocation(String id) ? id : null;
  }

  private static Array varchars(Connection connection, Collection<String> values)
      throws SQLException {
    return connection.createArrayOf("varchar", values.toArray());
  }

  private static Timestamp min(Timestamp current, Timestamp candidate) {
    return current == null || candidate.before(current) ? candidate : current;
  }

  private static Timestamp max(Timestamp current, Timestamp candidate) {
    return current == null || candidate.after(current) ? candidate : current;
  }

  // -- SEARCH INDEX --

  // A reset, not a re-feed: the merged rows' ids disappear, and a re-feed never deletes documents
  private static void requestReindex(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO indexing_status (indexing_status_type, indexing_status_indexing_date)"
              + " VALUES ('"
              + FINDING_MODEL
              + "', '"
              + EsIndexingUtils.REINDEX_REQUESTED_CURSOR
              + "')"
              + " ON CONFLICT (indexing_status_type)"
              + " DO UPDATE SET indexing_status_indexing_date = EXCLUDED.indexing_status_indexing_date");
    }
  }

  /**
   * RFC 9562 version 7 ids: the time in milliseconds, then a counter, then random bits. Ids drawn
   * in a row increase, also within one millisecond.
   */
  static final class TimeOrderedIds {
    private static final int MAX_COUNTER = 0xFFF;
    private long millis;
    private int counter;

    String next() {
      long now = System.currentTimeMillis();
      if (now > millis) {
        millis = now;
        counter = 0;
      } else if (++counter > MAX_COUNTER) {
        millis++;
        counter = 0;
      }
      long high = millis << 16 | 0x7000 | counter;
      long low = ThreadLocalRandom.current().nextLong() >>> 2 | 0x8000000000000000L;
      return new UUID(high, low).toString();
    }
  }

  // -- PACING --

  /**
   * {@code pages} is the size on disk and bounds the walk; the estimates only give the row density.
   */
  private record TableSize(long pages, long estimatedRows, long estimatedPages) {}

  private static TableSize tableSize(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT pg_relation_size(oid) / current_setting('block_size')::bigint,"
                    + " reltuples::bigint, relpages::bigint FROM pg_class"
                    + " WHERE oid = 'findings'::regclass")) {
      result.next();
      return new TableSize(result.getLong(1), result.getLong(2), result.getLong(3));
    }
  }

  private static String tid(long page) {
    return "(" + page + ",0)";
  }

  /**
   * Pauses, then waits while the WAL lag is above the bound. A lag that cannot be read stops the
   * wait for the rest of the run. Called between batches, so the transaction it opens holds no
   * write.
   */
  private void pace(Connection connection, WalGuard walGuard) {
    sleep(pauseMillis);
    if (!walGuard.available) {
      return;
    }
    try {
      long lag = walLagBytes(connection, walGuard.startWalFile);
      long nextLogAt = System.nanoTime();
      while (lag > walLagBoundBytes) {
        if (System.nanoTime() - nextLogAt >= 0) {
          log.info(
              "findings: waiting for WAL archiving or replicas, {} MB behind (bound {} MB)",
              lag / MEGABYTE,
              walLagBoundBytes / MEGABYTE);
          nextLogAt = System.nanoTime() + LOG_INTERVAL.toNanos();
        }
        sleep(Math.max(pauseMillis, 1_000));
        lag = walLagBytes(connection, walGuard.startWalFile);
      }
    } catch (SQLException e) {
      walGuard.available = false;
      rollbackQuietly(connection);
      log.warn(
          "findings: cannot read pg_stat_archiver / pg_stat_replication / pg_replication_slots,"
              + " pacing on the fixed pause alone: {}",
          e.getMessage());
    }
  }

  long walLagBytes(Connection connection, String startWalFile) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(WAL_LAG_QUERY)) {
      statement.setString(1, startWalFile);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        long lag = result.getLong(1);
        return result.wasNull() ? 0 : lag;
      }
    }
  }

  /** The pacing state of one run. */
  static final class WalGuard {
    private final String startWalFile;
    private boolean available = true;

    WalGuard(String startWalFile) {
      this.startWalFile = startWalFile;
    }
  }

  private static String currentWalFile(Connection connection) {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT pg_walfile_name(pg_current_wal_lsn())")) {
      result.next();
      return result.getString(1);
    } catch (SQLException e) {
      rollbackQuietly(connection);
      return null;
    }
  }

  // Ends a transaction a failed read aborted; it holds no write
  private static void rollbackQuietly(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException e) {
      log.warn("findings: rollback after a failed read: {}", e.getMessage());
    }
  }

  private static void sleep(int millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      // Stop here: the next start resumes from the rows left without a hash
      Thread.currentThread().interrupt();
      throw new IllegalStateException("findings identity conversion interrupted", e);
    }
  }
}
