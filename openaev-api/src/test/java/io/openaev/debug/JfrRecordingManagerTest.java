package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

@DisplayName("JfrRecordingManager")
class JfrRecordingManagerTest {

  private DebugProperties.Jfr config(Duration duration) {
    DebugProperties.Jfr jfr = new DebugProperties().getJfr();
    jfr.setDuration(duration);
    jfr.setMaxSize(DataSize.ofMegabytes(20));
    jfr.setMaxAge(Duration.ofMinutes(5));
    jfr.setSettings("default");
    return jfr;
  }

  @Test
  @DisplayName("starts a bounded recording and writes a final dump on stop")
  void startsAndDumps(@TempDir Path tmp) {
    JfrRecordingManager manager =
        new JfrRecordingManager(tmp.toString(), config(Duration.ofMinutes(10)));

    manager.start();
    assertThat(manager.getStatus()).isEqualTo(JfrRecordingManager.Status.RUNNING);

    manager.stop();
    assertThat(manager.getStatus()).isEqualTo(JfrRecordingManager.Status.INACTIVE);
    assertThat(tmp.toFile().listFiles())
        .anyMatch(f -> f.getName().startsWith("openaev-debug-") && f.getName().endsWith(".jfr"));
  }

  @Test
  @DisplayName("bounded timer writes periodic dumps while running")
  void periodicDumps(@TempDir Path tmp) throws Exception {
    JfrRecordingManager manager =
        new JfrRecordingManager(tmp.toString(), config(Duration.ofSeconds(1)));
    try {
      manager.start();
      assertThat(manager.getStatus()).isEqualTo(JfrRecordingManager.Status.RUNNING);

      boolean dumpAppeared = false;
      for (int i = 0; i < 50 && !dumpAppeared; i++) {
        Thread.sleep(100);
        dumpAppeared =
            Files.list(tmp).anyMatch(p -> p.getFileName().toString().matches(".*-\\d+\\.jfr"));
      }
      assertThat(dumpAppeared).as("a periodic dump file should appear").isTrue();
    } finally {
      manager.stop();
    }
  }

  @Test
  @DisplayName("fails loudly without crashing when the output dir cannot be created/written")
  void readOnlyFilesystem(@TempDir Path tmp) throws IOException {
    // A regular file used as the parent makes directory creation impossible for any user (incl.
    // root), deterministically reproducing the read-only / non-writable filesystem failure.
    Path blockingFile = Files.createFile(tmp.resolve("not-a-dir"));
    Path unwritable = blockingFile.resolve("jfr");
    JfrRecordingManager manager =
        new JfrRecordingManager(unwritable.toString(), config(Duration.ofMinutes(10)));

    manager.start(); // must not throw

    assertThat(manager.getStatus()).isEqualTo(JfrRecordingManager.Status.FAILED);
    assertThat(manager.getLastError()).isNotBlank();
  }

  @Test
  @DisplayName("retention bounds the number of dump files on disk")
  void boundsDumpFiles(@TempDir Path tmp) throws Exception {
    DebugProperties.Jfr jfr = config(Duration.ofSeconds(1));
    jfr.setMaxDumpFiles(2);
    jfr.setMaxTotalDumpSize(DataSize.ofGigabytes(10)); // count is the binding cap here
    JfrRecordingManager manager = new JfrRecordingManager(tmp.toString(), jfr);

    try {
      manager.start();
      Thread.sleep(3500); // let several periodic dumps + retention passes run
    } finally {
      manager.stop();
    }

    try (var files = Files.list(tmp)) {
      long jfrFiles = files.filter(p -> p.getFileName().toString().endsWith(".jfr")).count();
      assertThat(jfrFiles).isPositive().isLessThanOrEqualTo(2);
    }
  }

  @Test
  @DisplayName("does nothing when JFR is disabled")
  void disabled(@TempDir Path tmp) {
    DebugProperties.Jfr jfr = config(Duration.ofMinutes(10));
    jfr.setEnabled(false);
    JfrRecordingManager manager = new JfrRecordingManager(tmp.toString(), jfr);

    manager.start();

    assertThat(manager.getStatus()).isEqualTo(JfrRecordingManager.Status.INACTIVE);
  }
}
