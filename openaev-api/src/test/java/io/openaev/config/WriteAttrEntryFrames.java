package io.openaev.config;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-thread attribution store that carries a JPA write's production entry frame from the moment
 * the application asks for it (at {@code persist}/{@code merge}) to the moment its SQL executes at
 * a later flush. Without it the detector reads the entry frame off the stack at flush time, which
 * for a lazily flushed insert is a test or framework frame and so waives the write as test-driven,
 * the exact class the gate exists to catch (an insert issued inside a request but flushed after it
 * returns).
 *
 * <p>Two steps, because the row's primary key is only reliably known once, at flush:
 *
 * <ol>
 *   <li>{@link WriteAttrHibernateListeners} calls {@link #askedToWrite} at {@code persist}/{@code
 *       merge} with the entity instance and the outermost production frame then on the stack (null
 *       when the write was asked for straight from test code). Keyed by entity identity.
 *   <li>At {@code pre-insert}/{@code pre-update}, {@link #promoteToRow} moves that frame to a
 *       {@code table + id} key, the same key the trigger emits in its warning. {@link
 *       WriteAttrDetectorListener} then looks it up by {@link #frameForRow} when it reads the
 *       warning.
 * </ol>
 *
 * <p>A synchronous write (native SQL, {@code JdbcTemplate}) has no persist event, so its {@code
 * table + id} is never captured here; the listener falls back to the live stack, which for an
 * immediate execution is already the asking stack. Everything is a {@link ThreadLocal}: a
 * {@code @Transactional} test runs its request and its flush on one thread, and the maps are
 * cleared per test by {@link WriteAttrDetectorRecorder#start()}. Nothing here throws; a lookup miss
 * is a fallback, never a failure.
 */
final class WriteAttrEntryFrames {

  /** Entity instance identity to the production frame captured when the write was asked for. */
  private static final ThreadLocal<Map<Object, FrameHolder>> ASKED =
      ThreadLocal.withInitial(IdentityHashMap::new);

  /** {@code table + '\0' + id} to the captured frame, promoted at flush. */
  private static final ThreadLocal<Map<String, FrameHolder>> BY_ROW =
      ThreadLocal.withInitial(HashMap::new);

  /** Distinguishes "captured, frame is null (test-driven)" from "not captured at all". */
  private record FrameHolder(String frame) {}

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

  /**
   * At flush, binds the entity's captured frame to its {@code table + id} row key so the SQL-side
   * reader can find it by the identity the trigger emits. A no-op when the entity was never asked
   * for through {@code persist}/{@code merge} (a dirty update with no explicit call), leaving the
   * reader to fall back to the live stack.
   */
  static void promoteToRow(String table, Object id, Object entity) {
    if (table == null || id == null || entity == null) {
      return;
    }
    FrameHolder held = ASKED.get().get(entity);
    if (held != null) {
      BY_ROW.get().put(rowKey(table, id.toString()), held);
    }
  }

  /**
   * True when the written row came from a JPA write the listeners saw, so its captured frame is
   * authoritative and the live stack must not be consulted. False for a synchronous write (native
   * SQL, {@code JdbcTemplate}), whose live stack is already the asking stack.
   */
  static boolean isCaptured(String table, String id) {
    return table != null && id != null && BY_ROW.get().containsKey(rowKey(table, id));
  }

  /**
   * The production entry frame captured for a written row, meaningful only when {@link #isCaptured}
   * is true. Null means the JPA write was asked for straight from test code with no production
   * frame on the stack, which the gate then waives as test-driven.
   */
  static String capturedFrame(String table, String id) {
    if (table == null || id == null) {
      return null;
    }
    FrameHolder held = BY_ROW.get().get(rowKey(table, id));
    return held == null ? null : held.frame();
  }

  /** Drops this thread's captured attributions. Called per test so nothing leaks across tests. */
  static void clear() {
    ASKED.get().clear();
    BY_ROW.get().clear();
  }

  private static String rowKey(String table, String id) {
    return table.toLowerCase(Locale.ROOT) + '\0' + id;
  }
}
