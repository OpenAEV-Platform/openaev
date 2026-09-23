package io.openaev.config;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Per-thread attribution store that carries a JPA write's production entry frame from the moment
 * the application asks for it (at {@code persist}/{@code merge}) to the moment its SQL executes at
 * a later flush. Without it the detector reads the entry frame off the stack at flush time, which
 * for a lazily flushed insert is a test or framework frame and so waives the write as test-driven,
 * the exact class the gate exists to catch (an insert issued inside a request but flushed after it
 * returns).
 *
 * <p>Two steps, because the row's key is only reliably known once, at flush:
 *
 * <ol>
 *   <li>{@link WriteAttrHibernateListeners} calls {@link #askedToWrite} at {@code persist}/{@code
 *       merge} with the entity instance (for a merge, both the instance handed in and the managed
 *       copy Hibernate returns, since the copy is what reaches the flush) and the outermost
 *       production frame then on the stack (null when the write was asked for straight from test
 *       code). Keyed by entity identity. A loaded entity is captured the same way through {@link
 *       #loaded}, with the frame of the statement that hydrated it ({@link #statementAsked}), so a
 *       dirty-checked update or a collection change of an entity a request loaded is attributed to
 *       that request.
 *   <li>At {@code pre-insert}/{@code pre-update}, {@link #promoteToRow} moves that frame to a
 *       {@code table + row key} entry, the same key the trigger emits in its warning. {@link
 *       WriteAttrDetectorListener} then looks it up by {@link #capturedFrame} when it reads the
 *       warning.
 * </ol>
 *
 * <p>A many-to-many link row has no entity and no single-column key, so it cannot be promoted. Its
 * owner was captured, though: at {@code pre-collection-recreate}/{@code pre-collection-update},
 * {@link #bindOwner} binds the join table to the owner's frame for the duration of the flush, and
 * the listener falls back to that binding when the row lookup misses. Two owners of the same join
 * table with different frames in one flush make the binding ambiguous, which reads as not captured.
 *
 * <p>A synchronous write (native SQL, {@code JdbcTemplate}) has no persist event, so its row is
 * never captured here; the listener falls back to the live stack, which for an immediate execution
 * is already the asking stack. Everything is a {@link ThreadLocal}: a {@code @Transactional} test
 * runs its request and its flush on one thread, and the maps are cleared per test by {@link
 * WriteAttrDetectorRecorder#start()}. Nothing here throws; a lookup miss is a fallback, never a
 * failure.
 */
final class WriteAttrEntryFrames {

  /** Entity instance identity to the production frame captured when the write was asked for. */
  private static final ThreadLocal<Map<Object, FrameHolder>> ASKED =
      ThreadLocal.withInitial(IdentityHashMap::new);

  /** {@code table + '\0' + row key} to the captured frame, promoted at flush. */
  private static final ThreadLocal<Map<String, FrameHolder>> BY_ROW =
      ThreadLocal.withInitial(HashMap::new);

  /** Join table to its owner's captured frame, bound for the current flush only. */
  private static final ThreadLocal<Map<String, FrameHolder>> BY_FLUSH_TABLE =
      ThreadLocal.withInitial(HashMap::new);

  /**
   * The production entry frame of the statement this thread last sent to the database, which is the
   * frame that loads every entity hydrated from that statement's result. One stack walk per
   * statement, taken by the datasource listener, instead of one per loaded entity: a query that
   * returns thousands of rows must not cost thousands of stack walks.
   */
  private static final ThreadLocal<FrameHolder> STATEMENT = new ThreadLocal<>();

  /** Distinguishes "captured, frame is null (test-driven)" from "not captured at all". */
  private record FrameHolder(String frame) {}

  /** Owners of one join table disagreed on their frame within one flush: not captured. */
  private static final FrameHolder AMBIGUOUS = new FrameHolder("\0ambiguous");

  private WriteAttrEntryFrames() {}

  /**
   * Records that the application asked to write {@code entity}, attributing it to {@code
   * productionEntryFrame} (may be null when no production frame is on the stack, i.e. a test-driven
   * write).
   */
  static void askedToWrite(Object entity, String productionEntryFrame) {
    if (entity != null) {
      ASKED.get().put(entity, new FrameHolder(productionEntryFrame));
    }
  }

  /** Records the production entry frame of the statement about to execute on this thread. */
  static void statementAsked(String productionEntryFrame) {
    STATEMENT.set(new FrameHolder(productionEntryFrame));
  }

  /**
   * Records that {@code entity} was hydrated from the last statement of this thread, attributing it
   * to that statement's frame. A no-op when no statement was seen while recording (an entity served
   * from a cache), so that the entity stays uncaptured rather than misattributed.
   */
  static void loaded(Object entity) {
    FrameHolder statement = STATEMENT.get();
    if (entity != null && statement != null) {
      ASKED.get().put(entity, statement);
    }
  }

  /**
   * At flush, binds the entity's captured frame to its {@code table + row key} so the SQL-side
   * reader can find it by the identity the trigger emits. A no-op when the entity was never asked
   * for through {@code persist}/{@code merge} (a dirty update with no explicit call), leaving the
   * reader to fall back to the live stack.
   */
  static void promoteToRow(String table, String rowKey, Object entity) {
    if (table == null || rowKey == null || entity == null) {
      return;
    }
    FrameHolder held = ASKED.get().get(entity);
    if (held != null) {
      BY_ROW.get().put(rowKey(table, rowKey), held);
    }
  }

  /**
   * At flush, binds a join table to the captured frame of the collection owner whose rows are about
   * to be written. A no-op when the owner was never asked for through {@code persist}/{@code merge}
   * (loaded, then its collection changed): the link rows then fall back to the live stack.
   */
  static void bindOwner(String table, Object owner) {
    if (table == null || owner == null) {
      return;
    }
    FrameHolder held = ASKED.get().get(owner);
    if (held == null) {
      return;
    }
    Map<String, FrameHolder> bindings = BY_FLUSH_TABLE.get();
    FrameHolder existing = bindings.get(table.toLowerCase(Locale.ROOT));
    if (existing == null) {
      bindings.put(table.toLowerCase(Locale.ROOT), held);
    } else if (existing != AMBIGUOUS && !Objects.equals(existing.frame(), held.frame())) {
      bindings.put(table.toLowerCase(Locale.ROOT), AMBIGUOUS);
    }
  }

  /** Drops the join-table bindings of the flush that just completed. */
  static void clearFlushBindings() {
    BY_FLUSH_TABLE.get().clear();
  }

  /**
   * True when the written row came from a JPA write the listeners saw (its row was promoted, or its
   * join table is bound to one owner in the current flush), so its captured frame is authoritative
   * and the live stack must not be consulted. False for a synchronous write (native SQL, {@code
   * JdbcTemplate}), whose live stack is already the asking stack.
   */
  static boolean isCaptured(String table, String id) {
    return holder(table, id) != null;
  }

  /**
   * The production entry frame captured for a written row, meaningful only when {@link #isCaptured}
   * is true. Null means the JPA write was asked for straight from test code with no production
   * frame on the stack, which the gate then waives as test-driven.
   */
  static String capturedFrame(String table, String id) {
    FrameHolder held = holder(table, id);
    return held == null ? null : held.frame();
  }

  /** Drops this thread's captured attributions. Called per test so nothing leaks across tests. */
  static void clear() {
    ASKED.get().clear();
    BY_ROW.get().clear();
    BY_FLUSH_TABLE.get().clear();
    STATEMENT.remove();
  }

  private static FrameHolder holder(String table, String id) {
    if (table == null) {
      return null;
    }
    if (id != null) {
      FrameHolder byRow = BY_ROW.get().get(rowKey(table, id));
      if (byRow != null) {
        return byRow;
      }
    }
    FrameHolder byTable = BY_FLUSH_TABLE.get().get(table.toLowerCase(Locale.ROOT));
    return byTable == AMBIGUOUS ? null : byTable;
  }

  private static String rowKey(String table, String id) {
    return table.toLowerCase(Locale.ROOT) + '\0' + id;
  }
}
