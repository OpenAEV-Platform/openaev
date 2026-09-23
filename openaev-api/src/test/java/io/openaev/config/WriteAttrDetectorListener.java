package io.openaev.config;

import io.openaev.config.WriteAttrSignature.Relation;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

/**
 * Write-attribution detector listener, the write-side twin of {@link FailClosedDetectorListener}.
 * The detection itself is a DB trigger ({@link WriteAttrDetectorTrigger}), which sees {@code
 * NEW.tenant_id} directly and so is immune to statement shape; this listener is only the Java-side
 * reader. In {@code afterQuery} it scans the executed statement's JDBC warning chain for the
 * trigger's {@code [WRITEATTR]} marker, parses the offending table, written row id, written tenant
 * and scope, classifies the write and records a violation via {@link WriteAttrDetectorRecorder}.
 *
 * <p>Two things are decided here rather than in SQL:
 *
 * <ul>
 *   <li>A null-tenant write is a misattribution only on a strict table (one that can never hold a
 *       platform row). Strict versus dual is an entity-model property, so the trigger raises every
 *       null write and this listener drops the dual-scope ones. See {@link
 *       WriteAttrTableClassifier}.
 *   <li>The waiver key is the outermost production entry frame captured when the application asked
 *       for the write. For a JPA write that is captured at {@code persist}/{@code merge} by {@link
 *       WriteAttrHibernateListeners} and looked up here by {@code table + id}, because a JPA insert
 *       flushes lazily and the live stack at flush is a test or framework frame. For a synchronous
 *       write (native SQL, {@code JdbcTemplate}) there is no capture, and the live stack is already
 *       the asking stack, so it is used directly. The innermost caller is kept for triage only.
 * </ul>
 */
public class WriteAttrDetectorListener implements QueryExecutionListener {

  static final String MARKER = WriteAttrDetectorTrigger.MARKER;
  private static final boolean SUITE_WIDE =
      "on".equals(System.getProperty("openaev.writeattr.detector"));

  private static final Pattern SIGNAL =
      Pattern.compile("\\[WRITEATTR] table=(\\S+) id=(\\S+) tenant=(\\S+) scope=(.*)");

  private final Supplier<WriteAttrTableClassifier> classifierSupplier;
  private volatile WriteAttrTableClassifier classifier;

  /**
   * @param classifierSupplier resolves the strict/dual classification lazily, once the context is
   *     refreshed. The entity model and the schema are only available then, not at bean
   *     post-processor construction.
   */
  public WriteAttrDetectorListener(Supplier<WriteAttrTableClassifier> classifierSupplier) {
    this.classifierSupplier = classifierSupplier;
  }

  /** Builds a supplier from a Spring context's entity model and datasource. */
  static Supplier<WriteAttrTableClassifier> classifierFrom(
      Supplier<EntityManagerFactory> emf, Supplier<DataSource> dataSource) {
    return () -> WriteAttrTableClassifier.from(emf.get(), dataSource.get());
  }

  @Override
  public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    // The signal is read in afterQuery, from the warnings the trigger raised while the statement
    // ran. Here, one stack walk per statement gives the entities it hydrates their loading frame.
    if (WriteAttrDetectorRecorder.isRecording()) {
      WriteAttrEntryFrames.statementAsked(WriteAttrStack.entryFrame());
    }
  }

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    Statement statement = execInfo.getStatement();
    if (statement == null) {
      return;
    }
    try {
      SQLWarning warning = statement.getWarnings();
      if (warning == null) {
        return;
      }
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      String innermostCaller = WriteAttrStack.innermostCaller(stack);
      for (SQLWarning w = warning; w != null; w = w.getNextWarning()) {
        String message = w.getMessage();
        if (message == null) {
          continue;
        }
        Matcher matcher = SIGNAL.matcher(message);
        if (matcher.find()) {
          record(
              matcher.group(1),
              matcher.group(2),
              matcher.group(3),
              matcher.group(4),
              stack,
              innermostCaller);
        }
      }
      // Clear so a reused prepared statement does not re-surface the same warning on a later,
      // in-scope write.
      statement.clearWarnings();
    } catch (SQLException e) {
      // Fail safe: never turn an observation error into a test failure.
    }
  }

  private void record(
      String table,
      String id,
      String writtenTenant,
      String scope,
      StackTraceElement[] stack,
      String innermostCaller) {
    Relation relation = WriteAttrSignature.relationOf(writtenTenant);
    // A null tenant is by-design on a dual-scope table (a platform row); only flag it on a strict
    // one, which can never hold a platform row.
    if (relation == Relation.NULL && !classifier().flagsNullTenant(table)) {
      return;
    }
    String entryFrame = resolveEntryFrame(table, id, stack);
    WriteAttrDetectorRecorder.record(
        new WriteAttrDetectorRecorder.Violation(
            table, writtenTenant, scope, relation, entryFrame, innermostCaller));
    if (SUITE_WIDE) {
      System.out.println(
          MARKER
              + " table="
              + table
              + " relation="
              + relation
              + " tenant="
              + writtenTenant
              + " scope="
              + scope
              + " entry="
              + entryFrame
              + " innermost="
              + innermostCaller);
    }
  }

  /**
   * The production entry frame the gate keys on, in this order:
   *
   * <ol>
   *   <li>the frame captured when the application asked to write this row (by {@code table + id},
   *       or by the join table's owner during the current flush), authoritative even when null (a
   *       captured test-driven write);
   *   <li>else the live stack's production entry frame: for a synchronous write (native SQL, {@code
   *       JdbcTemplate}) the live stack is the asking stack;
   *   <li>else, when the statement runs inside a Hibernate flush, an {@code unattributed(...)} key
   *       naming the test that flushed it: nobody captured the write and the stack cannot say who
   *       asked for it (a dirty-checked update, an owner loaded rather than persisted), so it is
   *       reported rather than dropped;
   *   <li>else null: a synchronous write issued by test code itself, waived.
   * </ol>
   */
  private static String resolveEntryFrame(String table, String id, StackTraceElement[] stack) {
    if (WriteAttrEntryFrames.isCaptured(table, id)) {
      return WriteAttrEntryFrames.capturedFrame(table, id);
    }
    String live = WriteAttrStack.entryFrame(stack);
    if (live != null) {
      return live;
    }
    return WriteAttrStack.unattributed(stack);
  }

  private WriteAttrTableClassifier classifier() {
    WriteAttrTableClassifier local = classifier;
    if (local == null) {
      local = classifierSupplier.get();
      classifier = local;
    }
    return local;
  }
}
