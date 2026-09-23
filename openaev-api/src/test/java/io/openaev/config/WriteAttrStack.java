package io.openaev.config;

import java.util.List;

/**
 * Stack-trace reduction shared by the write-attribution detector: from the current thread's frames
 * it derives the outermost production entry frame (the waiver key) and the innermost application
 * caller (triage information). Kept in one place so the two capture points agree exactly: {@link
 * WriteAttrDetectorListener} for a write whose SQL runs synchronously (native, {@code
 * JdbcTemplate}), and {@link WriteAttrHibernateListeners} for a JPA write whose SQL is deferred to
 * a later flush.
 *
 * <p>Why two capture points need the same reduction: a JPA insert flushes lazily, so by the time
 * its INSERT executes the controller has returned and only test or framework frames remain on the
 * stack. Reading the entry frame at flush time therefore attributes the write to whatever ran the
 * flush (a test method), which waives exactly the class the gate exists to catch. The Hibernate
 * listener captures this reduction when the application <em>asks</em> for the write (at {@code
 * persist}/{@code merge}), where the production frame is still on the stack; a synchronous write is
 * captured here, at execution, where the stack is already correct.
 */
final class WriteAttrStack {

  private static final String DETECTOR_PACKAGE_PREFIX = "io.openaev.config.WriteAttr";

  /**
   * Request and transaction infrastructure that wraps every entry point, so it is on the stack
   * while the controller runs and stays there at a later flush. Excluded so the entry frame is the
   * application entry (the controller or background entry point), not a filter or aspect common to
   * all of them. Servlet filters are matched generically by their {@code doFilter} method rather
   * than by class, because any filter that wraps the chain, e.g. a crawler or CORS filter, would
   * otherwise be picked as the outermost application frame ahead of the controller it wraps.
   */
  private static final List<String> INFRA_FRAMES =
      List.of(
          "io.openaev.security.TokenAuthenticationFilter",
          "io.openaev.debug.OrmInsightFilter",
          "io.openaev.config.RollingSessionCookieFilter",
          "io.openaev.aop.");

  private static final List<String> FILTER_METHODS = List.of("doFilter", "doFilterInternal");

  /**
   * Frames present only while Hibernate flushes: the flush listener runs every deferred insert,
   * update and collection write of a session, including batched ones. A statement executed under
   * them was asked for earlier, somewhere else, and the live stack no longer says where.
   */
  private static final List<String> FLUSH_FRAMES =
      List.of(
          "org.hibernate.event.internal.AbstractFlushingEventListener",
          "org.hibernate.engine.spi.ActionQueue");

  /** Key prefix of a flush-time write nobody attributed: {@code unattributed(<flushing test>)}. */
  static final String UNATTRIBUTED_PREFIX = "unattributed(";

  private WriteAttrStack() {}

  /** The outermost production entry frame of the calling thread's current stack. */
  static String entryFrame() {
    return entryFrame(Thread.currentThread().getStackTrace());
  }

  /**
   * The outermost application frame that is not test, fixture or request/transaction
   * infrastructure: the controller method or background entry point that started the work. Null
   * when the stack holds no production entry (the work was driven straight from test or fixture
   * code), which the gate then waives.
   */
  static String entryFrame(StackTraceElement[] stack) {
    String outermost = null;
    for (StackTraceElement frame : stack) {
      if (isApplicationFrame(frame) && !isInfraFrame(frame) && !isTestFrame(frame)) {
        outermost = frameSignature(frame);
      }
    }
    return outermost;
  }

  /** First application frame that is not this detector: the flush or emit site, best effort. */
  static String innermostCaller(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      if (isApplicationFrame(frame)) {
        return frameSignature(frame);
      }
    }
    return "unknown";
  }

  /**
   * True when the calling thread is inside a Hibernate flush, i.e. the statement being executed is
   * a deferred JPA write rather than a synchronous one.
   */
  static boolean isFlushStack(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      String className = frame.getClassName();
      for (String flush : FLUSH_FRAMES) {
        if (className.startsWith(flush)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * The entry frame the gate keys an unattributed write on: a flush-time write the ask-time
   * listeners never captured and whose flush stack holds no production frame. Null when the stack
   * is not a flush (a synchronous test-driven write, waived as today).
   */
  static String unattributed(StackTraceElement[] stack) {
    if (!isFlushStack(stack)) {
      return null;
    }
    return UNATTRIBUTED_PREFIX + outermostTestFrame(stack) + ")";
  }

  /** The outermost test frame of the stack, without its line number; "unknown" when none. */
  static String outermostTestFrame(StackTraceElement[] stack) {
    String outermost = null;
    for (StackTraceElement frame : stack) {
      // Not isApplicationFrame: that excludes the detector's own classes, and its tests are test
      // frames like any other.
      if (frame.getClassName().startsWith("io.openaev.") && isTestFrame(frame)) {
        outermost = frame.getClassName() + "." + frame.getMethodName();
      }
    }
    return outermost == null ? "unknown" : outermost;
  }

  private static boolean isApplicationFrame(StackTraceElement frame) {
    String className = frame.getClassName();
    return className.startsWith("io.openaev.") && !className.startsWith(DETECTOR_PACKAGE_PREFIX);
  }

  private static boolean isInfraFrame(StackTraceElement frame) {
    if (FILTER_METHODS.contains(frame.getMethodName())) {
      return true;
    }
    String className = frame.getClassName();
    for (String infra : INFRA_FRAMES) {
      if (className.startsWith(infra)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTestFrame(StackTraceElement frame) {
    String className = frame.getClassName();
    if (className.contains(".fixtures.")
        || className.contains(".utilstest.")
        || className.contains(".composers.")) {
      return true;
    }
    String outer =
        className.contains("$") ? className.substring(0, className.indexOf('$')) : className;
    String simpleName = outer.substring(outer.lastIndexOf('.') + 1);
    return simpleName.endsWith("Test")
        || simpleName.endsWith("IT")
        || simpleName.endsWith("Benchmark");
  }

  private static String frameSignature(StackTraceElement frame) {
    return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
  }
}
