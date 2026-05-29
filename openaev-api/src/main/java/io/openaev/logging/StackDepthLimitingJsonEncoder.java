package io.openaev.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

/**
 * Extends the built-in Logback {@link JsonEncoder} to limit the depth of stack traces and cause
 * chains in JSON log output.
 *
 * <p>Deep stack traces (e.g. from Spring proxy chains or Hibernate cascades) can produce JSON log
 * lines that exceed Loki's {@code max_line_size}, causing silent mid-JSON truncation and {@code
 * JSONParserErr} on the Grafana side.
 *
 * <p>When truncation is needed, the encoder keeps the <b>top</b> frames (where the exception was
 * thrown) and the <b>bottom</b> frames (request entry point / your controller), removing the middle
 * section which is typically framework plumbing (Spring AOP proxies, CGLIB, reflection). A marker
 * frame is inserted at the cut point indicating how many frames were removed.
 *
 * <p>Configuration (logback-spring.xml):
 *
 * <pre>{@code
 * <encoder class="io.openaev.logging.StackDepthLimitingJsonEncoder">
 *     <withThrowable>true</withThrowable>
 *     <maxStackDepth>30</maxStackDepth>
 *     <maxCauseDepth>5</maxCauseDepth>
 * </encoder>
 * }</pre>
 *
 * @see <a href="https://github.com/OpenCTI-Platform/opencti/issues/4199">OpenCTI #4199</a>
 */
public class StackDepthLimitingJsonEncoder extends JsonEncoder {

  static final int DEFAULT_MAX_STACK_DEPTH = 80;
  static final int DEFAULT_MAX_CAUSE_DEPTH = 10;

  private int maxStackDepth = DEFAULT_MAX_STACK_DEPTH;
  private int maxCauseDepth = DEFAULT_MAX_CAUSE_DEPTH;

  public void setMaxStackDepth(int maxStackDepth) {
    this.maxStackDepth = Math.max(1, maxStackDepth);
  }

  public void setMaxCauseDepth(int maxCauseDepth) {
    this.maxCauseDepth = Math.max(1, maxCauseDepth);
  }

  @Override
  public byte[] encode(ILoggingEvent event) {
    if (event.getThrowableProxy() == null) {
      return super.encode(event);
    }
    IThrowableProxy truncated =
        new TruncatedThrowableProxy(event.getThrowableProxy(), maxStackDepth, maxCauseDepth, 0);
    return super.encode(new TruncatedThrowableEvent(event, truncated));
  }

  /**
   * Wrapper around {@link IThrowableProxy} that limits the number of stack frames per throwable and
   * the depth of the cause chain.
   */
  static class TruncatedThrowableProxy implements IThrowableProxy {
    private final IThrowableProxy delegate;
    private final int maxStackDepth;
    private final int maxCauseDepth;
    private final int causeLevel;

    TruncatedThrowableProxy(
        IThrowableProxy delegate, int maxStackDepth, int maxCauseDepth, int causeLevel) {
      this.delegate = delegate;
      this.maxStackDepth = maxStackDepth;
      this.maxCauseDepth = maxCauseDepth;
      this.causeLevel = causeLevel;
    }

    @Override
    public String getMessage() {
      return delegate.getMessage();
    }

    @Override
    public String getClassName() {
      return delegate.getClassName();
    }

    @Override
    public StackTraceElementProxy[] getStackTraceElementProxyArray() {
      StackTraceElementProxy[] original = delegate.getStackTraceElementProxyArray();
      if (original == null || original.length <= maxStackDepth) {
        return original;
      }
      // Keep top frames (error origin) and bottom frames (request entry point).
      // The middle is typically framework plumbing (Spring AOP, CGLIB, reflection).
      int keepTop = maxStackDepth * 2 / 3;
      int keepBottom = maxStackDepth - keepTop;
      int truncatedCount = original.length - keepTop - keepBottom;

      StackTraceElementProxy[] result = new StackTraceElementProxy[keepTop + 1 + keepBottom];
      System.arraycopy(original, 0, result, 0, keepTop);
      result[keepTop] = new TruncationMarkerProxy(truncatedCount);
      System.arraycopy(original, original.length - keepBottom, result, keepTop + 1, keepBottom);
      return result;
    }

    @Override
    public int getCommonFrames() {
      return delegate.getCommonFrames();
    }

    @Override
    public IThrowableProxy getCause() {
      if (causeLevel >= maxCauseDepth) {
        return null;
      }
      IThrowableProxy cause = delegate.getCause();
      if (cause == null) {
        return null;
      }
      return new TruncatedThrowableProxy(cause, maxStackDepth, maxCauseDepth, causeLevel + 1);
    }

    @Override
    public IThrowableProxy[] getSuppressed() {
      IThrowableProxy[] suppressed = delegate.getSuppressed();
      if (suppressed == null || suppressed.length == 0) {
        return suppressed;
      }
      IThrowableProxy[] result = new IThrowableProxy[suppressed.length];
      for (int i = 0; i < suppressed.length; i++) {
        result[i] =
            new TruncatedThrowableProxy(
                suppressed[i], maxStackDepth, maxCauseDepth, causeLevel + 1);
      }
      return result;
    }

    @Override
    public boolean isCyclic() {
      return delegate.isCyclic();
    }
  }

  /**
   * Synthetic stack frame marker inserted at the truncation point. Overrides {@link
   * StackTraceElementProxy#getSTEAsString()} so the JSON output contains a human-readable indicator
   * instead of a bogus frame.
   */
  static class TruncationMarkerProxy extends StackTraceElementProxy {
    private static final StackTraceElement DUMMY =
        new StackTraceElement("truncated", "frames", null, -1);
    private final String marker;

    TruncationMarkerProxy(int truncatedCount) {
      super(DUMMY);
      this.marker = "... " + truncatedCount + " frames truncated (framework internals)";
    }

    @Override
    public String getSTEAsString() {
      return marker;
    }

    @Override
    public String toString() {
      return marker;
    }
  }

  /**
   * Delegates all {@link ILoggingEvent} methods to the original event, except {@link
   * #getThrowableProxy()} which returns the truncated proxy.
   */
  static class TruncatedThrowableEvent implements ILoggingEvent {
    private final ILoggingEvent delegate;
    private final IThrowableProxy throwableProxy;

    TruncatedThrowableEvent(ILoggingEvent delegate, IThrowableProxy throwableProxy) {
      this.delegate = delegate;
      this.throwableProxy = throwableProxy;
    }

    @Override
    public IThrowableProxy getThrowableProxy() {
      return throwableProxy;
    }

    @Override
    public String getThreadName() {
      return delegate.getThreadName();
    }

    @Override
    public Level getLevel() {
      return delegate.getLevel();
    }

    @Override
    public String getMessage() {
      return delegate.getMessage();
    }

    @Override
    public Object[] getArgumentArray() {
      return delegate.getArgumentArray();
    }

    @Override
    public String getFormattedMessage() {
      return delegate.getFormattedMessage();
    }

    @Override
    public String getLoggerName() {
      return delegate.getLoggerName();
    }

    @Override
    public LoggerContextVO getLoggerContextVO() {
      return delegate.getLoggerContextVO();
    }

    @Override
    public StackTraceElement[] getCallerData() {
      return delegate.getCallerData();
    }

    @Override
    public boolean hasCallerData() {
      return delegate.hasCallerData();
    }

    @Override
    public List<Marker> getMarkerList() {
      return delegate.getMarkerList();
    }

    @Override
    public Map<String, String> getMDCPropertyMap() {
      return delegate.getMDCPropertyMap();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<String, String> getMdc() {
      return delegate.getMdc();
    }

    @Override
    public long getTimeStamp() {
      return delegate.getTimeStamp();
    }

    @Override
    public int getNanoseconds() {
      return delegate.getNanoseconds();
    }

    @Override
    public Instant getInstant() {
      return delegate.getInstant();
    }

    @Override
    public long getSequenceNumber() {
      return delegate.getSequenceNumber();
    }

    @Override
    public List<KeyValuePair> getKeyValuePairs() {
      return delegate.getKeyValuePairs();
    }

    @Override
    public void prepareForDeferredProcessing() {
      delegate.prepareForDeferredProcessing();
    }
  }
}
