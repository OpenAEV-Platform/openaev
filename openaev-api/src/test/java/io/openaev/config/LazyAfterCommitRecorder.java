package io.openaev.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects what {@link LazyAfterCommitProbeListener} sees: a read of a tenant-active table that an
 * HTTP request issues outside any declared transaction, typically Jackson initialising a lazy
 * association after the request's transaction has committed.
 *
 * <p>Static for the same reason as {@link FailClosedAccessRecorder}: the datasource-proxy builds a
 * single listener instance per context and a test reads what it recorded.
 */
public final class LazyAfterCommitRecorder {

  /** Where the read happened relative to the request. */
  public enum Phase {
    /** While the response body was being written, i.e. during Jackson serialisation. */
    SERIALIZATION,
    /** In handler code, after the transactional call it made had returned. */
    HANDLER
  }

  /**
   * What the evidence supports for one read.
   *
   * <ul>
   *   <li>{@code CONFIRMED_OBSERVED}: the read ran with no scope and returned fewer rows than the
   *       request's scope holds. This is what production does.
   *   <li>{@code CONFIRMED_REPLAYED}: a test transaction kept the request's scope alive, so the
   *       test itself saw the rows; replaying the same statement with the same parameters under an
   *       empty scope, which is what production runs, returned fewer.
   *   <li>{@code NO_ROWS_IN_SCOPE}: the shape is there but the request's scope holds no row for
   *       this read, so the test data cannot show a loss.
   *   <li>{@code NO_LOSS}: an empty scope returns as many rows as the request's scope.
   *   <li>{@code REPLAY_FAILED}: the statement could not be replayed; nothing is established.
   * </ul>
   */
  public enum Verdict {
    CONFIRMED_OBSERVED,
    CONFIRMED_REPLAYED,
    NO_ROWS_IN_SCOPE,
    NO_LOSS,
    REPLAY_FAILED
  }

  /**
   * One read of a tenant-active table issued by a request outside any declared transaction.
   *
   * @param endpoint HTTP method and the matched route pattern
   * @param handler controller class and method that served the request
   * @param phase where in the request the read happened
   * @param association the lazy collection role being initialised, or {@code null} when the read
   *     was not a collection initialisation
   * @param ownerId the id of the entity owning that collection, or {@code null}
   * @param tables the tenant-active tables the statement mentions
   * @param transactionActive whether a transaction was open on the thread; only a test transaction
   *     can make this true here, since no declared one is on the stack
   * @param scopeAtRead the tenant scope on the connection when the statement ran
   * @param requestScope the scope the request's transactional call set, or empty if none
   * @param rowsUnderRequestScope rows the statement returns under the request's scope, -1 when the
   *     replay failed
   * @param rowsUnderEmptyScope rows the statement returns under an empty scope, -1 when the replay
   *     failed
   * @param caller first application frame below the read, for triage
   * @param sql the statement as sent to the database
   */
  public record Incident(
      String endpoint,
      String handler,
      Phase phase,
      String association,
      String ownerId,
      String tables,
      boolean transactionActive,
      String scopeAtRead,
      String requestScope,
      int rowsUnderRequestScope,
      int rowsUnderEmptyScope,
      String caller,
      String sql) {

    public Verdict verdict() {
      if (rowsUnderRequestScope < 0 || rowsUnderEmptyScope < 0) {
        return Verdict.REPLAY_FAILED;
      }
      if (rowsUnderRequestScope == 0) {
        return Verdict.NO_ROWS_IN_SCOPE;
      }
      if (rowsUnderEmptyScope >= rowsUnderRequestScope) {
        return Verdict.NO_LOSS;
      }
      return transactionActive || !scopeAtRead.isEmpty()
          ? Verdict.CONFIRMED_REPLAYED
          : Verdict.CONFIRMED_OBSERVED;
    }

    /** One tab-separated line, the format the suite-wide run collects. */
    String toTsv() {
      return String.join(
          "\t",
          verdict().name(),
          endpoint,
          handler,
          phase.name(),
          String.valueOf(association),
          String.valueOf(ownerId),
          tables,
          String.valueOf(transactionActive),
          scopeAtRead,
          requestScope,
          String.valueOf(rowsUnderRequestScope),
          String.valueOf(rowsUnderEmptyScope),
          caller,
          sql.replaceAll("\\s+", " ").trim());
    }
  }

  private static final List<Incident> INCIDENTS = new CopyOnWriteArrayList<>();

  private LazyAfterCommitRecorder() {}

  /** Drops everything recorded so far. */
  public static void clear() {
    INCIDENTS.clear();
  }

  public static List<Incident> incidents() {
    return Collections.unmodifiableList(INCIDENTS);
  }

  static void record(Incident incident, String outputFile) {
    INCIDENTS.add(incident);
    if (outputFile != null && !outputFile.isBlank()) {
      append(Path.of(outputFile), incident.toTsv());
    }
  }

  private static synchronized void append(Path file, String line) {
    try {
      Files.writeString(
          file,
          line + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot append to " + file, e);
    }
  }
}
