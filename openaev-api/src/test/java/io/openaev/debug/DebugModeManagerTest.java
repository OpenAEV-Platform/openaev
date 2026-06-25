package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

@DisplayName("DebugModeManager (guardrail and lifecycle)")
class DebugModeManagerTest {

  private Logger debugLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    debugLogger = (Logger) LoggerFactory.getLogger("io.openaev.debug");
    debugLogger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    debugLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    debugLogger.detachAppender(appender);
  }

  private boolean anyMessageContains(String needle) {
    return new ArrayList<>(appender.list)
        .stream().anyMatch(e -> e.getFormattedMessage().contains(needle));
  }

  @Test
  @DisplayName("logs the banner, the Pyroscope coexistence warning, then a repeated warning")
  void logsBannerAndRepeatsWarning() throws Exception {
    DebugProperties properties = new DebugProperties();
    properties.setWarningInterval(Duration.ofSeconds(1));
    properties.getJfr().setEnabled(true); // exercises the Pyroscope coexistence branch

    DebugProperties.Jfr disabledJfr = new DebugProperties().getJfr();
    disabledJfr.setEnabled(false);
    JfrRecordingManager jfr = new JfrRecordingManager("target/debug-mgr-test", disabledJfr);

    DebugModeManager manager = new DebugModeManager(properties, jfr, new DebugRuntimeState(), true);
    try {
      manager.start();

      assertThat(anyMessageContains("OpenAEV DEBUG MODE IS ACTIVE")).isTrue();
      assertThat(anyMessageContains("JDK JFR recording is NOT started")).isTrue();
      // Banner reflects the real state: Pyroscope owns profiling, JFR is not claimed to be running.
      assertThat(anyMessageContains("Profiling is delegated to the Pyroscope agent")).isTrue();
      assertThat(anyMessageContains("A Java Flight Recorder recording is running")).isFalse();

      boolean repeated = false;
      for (int i = 0; i < 30 && !repeated; i++) {
        Thread.sleep(100);
        repeated = anyMessageContains("Verbose debug tracing is");
      }
      assertThat(repeated).as("the warning should repeat on the interval").isTrue();
    } finally {
      manager.stop();
    }

    assertThat(anyMessageContains("Debug mode: stopped")).isTrue();
  }

  @Test
  @DisplayName("no Pyroscope warning when Pyroscope is disabled")
  void noPyroscopeWarningWhenDisabled() {
    DebugProperties properties = new DebugProperties();
    properties.setWarningInterval(Duration.ofHours(1));
    DebugProperties.Jfr disabledJfr = new DebugProperties().getJfr();
    disabledJfr.setEnabled(false);

    DebugModeManager manager =
        new DebugModeManager(
            properties,
            new JfrRecordingManager("target/debug-mgr-test", disabledJfr),
            new DebugRuntimeState(),
            false);
    try {
      manager.start();
      List<ILoggingEvent> events = new ArrayList<>(appender.list);
      assertThat(events).anyMatch(e -> e.getFormattedMessage().contains("DEBUG MODE IS ACTIVE"));
      assertThat(events)
          .noneMatch(e -> e.getFormattedMessage().contains("JDK JFR recording is NOT started"));
      // JFR is disabled here, so the banner must not claim a recording is running.
      assertThat(events)
          .noneMatch(e -> e.getFormattedMessage().contains("A Java Flight Recorder recording"));
    } finally {
      manager.stop();
    }
  }

  @Test
  @DisplayName("a single profiler runs: JFR does not start when Pyroscope is enabled")
  void jfrYieldsToPyroscope(@TempDir Path tmp) {
    DebugProperties properties = new DebugProperties();
    properties.setWarningInterval(Duration.ofHours(1));
    DebugProperties.Jfr jfr = properties.getJfr();
    jfr.setSettings("default");
    JfrRecordingManager jfrManager = new JfrRecordingManager(tmp.toString(), jfr);

    DebugModeManager manager =
        new DebugModeManager(properties, jfrManager, new DebugRuntimeState(), true);
    try {
      manager.start();
      assertThat(jfrManager.getStatus())
          .as("JFR must not run alongside Pyroscope")
          .isEqualTo(JfrRecordingManager.Status.INACTIVE);
    } finally {
      manager.stop();
    }
  }

  @Test
  @DisplayName("auto-disables the verbose tracing after the configured delay")
  void autoDisables(@TempDir Path tmp) throws Exception {
    DebugProperties properties = new DebugProperties();
    properties.setWarningInterval(Duration.ofHours(1));
    properties.setAutoDisableAfter(Duration.ofSeconds(1));
    DebugProperties.Jfr jfr = properties.getJfr();
    jfr.setEnabled(false);
    DebugRuntimeState runtimeState = new DebugRuntimeState();

    DebugModeManager manager =
        new DebugModeManager(
            properties, new JfrRecordingManager(tmp.toString(), jfr), runtimeState, false);
    try {
      manager.start();
      assertThat(runtimeState.isActive()).isTrue();

      boolean disabled = false;
      for (int i = 0; i < 30 && !disabled; i++) {
        Thread.sleep(100);
        disabled = !runtimeState.isActive();
      }
      assertThat(disabled).as("runtime state should flip off after the delay").isTrue();
      assertThat(anyMessageContains("auto-disabled after")).isTrue();
    } finally {
      manager.stop();
    }
  }
}
