package io.openaev.debug;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug-mode lifecycle: startup banner, repeated warning, JFR start/stop, and the auto-disable
 * timer.
 */
public class DebugModeManager {

  private static final Logger log = LoggerFactory.getLogger(DebugModeManager.class);

  private final DebugProperties properties;
  private final JfrRecordingManager jfrRecordingManager;
  private final DebugRuntimeState runtimeState;
  private final boolean pyroscopeEnabled;

  private ScheduledExecutorService scheduler;

  public DebugModeManager(
      DebugProperties properties,
      JfrRecordingManager jfrRecordingManager,
      DebugRuntimeState runtimeState,
      boolean pyroscopeEnabled) {
    this.properties = properties;
    this.jfrRecordingManager = jfrRecordingManager;
    this.runtimeState = runtimeState;
    this.pyroscopeEnabled = pyroscopeEnabled;
  }

  @PostConstruct
  public void start() {
    // One profiler at a time: JFR yields to the Pyroscope agent.
    if (pyroscopeEnabled) {
      if (properties.getJfr().isEnabled()) {
        log.warn(
            "Debug mode: the Pyroscope agent is enabled, so the JDK JFR recording is NOT started "
                + "(a single profiler runs at a time). Disable pyroscope.agent.enabled to use JFR.");
      }
    } else {
      jfrRecordingManager.start();
    }
    logBanner(); // after the profiler decision, so it reflects the real JFR status
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "openaev-debug-manager");
              t.setDaemon(true);
              return t;
            });
    startRepeatingWarning();
    scheduleAutoDisable();
  }

  @PreDestroy
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    boolean wasRecording = jfrRunning();
    jfrRecordingManager.stop();
    log.warn(wasRecording ? "Debug mode: stopped, JFR recording flushed." : "Debug mode: stopped.");
  }

  private void scheduleAutoDisable() {
    long seconds = properties.getAutoDisableAfter().toSeconds();
    if (seconds <= 0) {
      return;
    }
    scheduler.schedule(this::autoDisable, seconds, TimeUnit.SECONDS);
  }

  private void autoDisable() {
    runtimeState.deactivate();
    jfrRecordingManager.stop();
    log.warn(
        "Debug mode: auto-disabled after {} (openaev.debug.auto-disable-after). Verbose tracing "
            + "stopped; restart without the flag to fully remove the datasource proxy.",
        properties.getAutoDisableAfter());
  }

  private void startRepeatingWarning() {
    long intervalSeconds = Math.max(1, properties.getWarningInterval().toSeconds());
    scheduler.scheduleAtFixedRate(
        () -> {
          if (!runtimeState.isActive()) {
            return; // auto-disabled
          }
          log.warn(
              "Debug mode is ACTIVE (openaev.debug.enabled=true). Verbose debug tracing is "
                  + "running with extra overhead. This must NOT be left on in production.");
        },
        intervalSeconds,
        intervalSeconds,
        TimeUnit.SECONDS);
  }

  private void logBanner() {
    String rule = "=".repeat(76);
    StringBuilder b = new StringBuilder("\n").append(rule).append("\n");
    b.append("OpenAEV DEBUG MODE IS ACTIVE (openaev.debug.enabled=true)\n");
    if (properties.getSql().isEnabled()) {
      b.append("  - SQL statements are logged with timing and (masked) parameters\n");
    }
    if (jfrRunning()) {
      b.append("  - A Java Flight Recorder recording is running\n");
    } else if (pyroscopeEnabled) {
      b.append("  - Profiling is delegated to the Pyroscope agent (JFR not started)\n");
    }
    b.append("  - This adds overhead and writes extra files; do NOT use in production\n");
    b.append("  - Disable by removing openaev.debug.enabled / OPENAEV_DEBUG_ENABLED\n");
    b.append(rule);
    log.warn(b.toString());
  }

  private boolean jfrRunning() {
    return jfrRecordingManager.getStatus() == JfrRecordingManager.Status.RUNNING;
  }
}
