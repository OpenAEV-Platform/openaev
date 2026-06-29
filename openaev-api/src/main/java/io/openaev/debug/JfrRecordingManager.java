package io.openaev.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded JFR recording on the JDK {@code jdk.jfr} engine (no agent, no collector). The recording
 * is capped by size/age; periodic dumps are written to {@code .jfr} files and pruned past {@code
 * maxDumpFiles}/{@code maxTotalDumpSize}. A non-writable output dir fails loudly with {@link
 * Status#FAILED} without throwing out of startup.
 */
public class JfrRecordingManager {

  private static final Logger log = LoggerFactory.getLogger(JfrRecordingManager.class);
  private static final DateTimeFormatter FILE_STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  /** Lifecycle state of the recorder, exposed so it can be inspected and tested. */
  public enum Status {
    INACTIVE,
    RUNNING,
    FAILED
  }

  private final String outputDirPath;
  private final DebugProperties.Jfr config;
  private final AtomicInteger dumpCounter = new AtomicInteger();

  private volatile Status status = Status.INACTIVE;
  private volatile String lastError;
  private Path outputDir;
  private Recording recording;
  private ScheduledExecutorService scheduler;

  public JfrRecordingManager(String outputDirPath, DebugProperties.Jfr config) {
    this.outputDirPath = outputDirPath;
    this.config = config;
  }

  public Status getStatus() {
    return status;
  }

  public String getLastError() {
    return lastError;
  }

  /**
   * Starts the recording. Never throws: a failure is logged and reflected in {@link #getStatus()}.
   */
  public synchronized void start() {
    if (!config.isEnabled()) {
      log.info("Debug mode: JFR recording is disabled by configuration");
      return;
    }
    if (status == Status.RUNNING) {
      return;
    }
    try {
      this.outputDir = prepareWritableOutputDir();
      Configuration jfrConfiguration = Configuration.getConfiguration(config.getSettings());
      Recording newRecording = new Recording(jfrConfiguration);
      newRecording.setName("openaev-debug");
      newRecording.setToDisk(true);
      newRecording.setMaxSize(config.getMaxSize().toBytes());
      newRecording.setMaxAge(config.getMaxAge());
      newRecording.start();
      this.recording = newRecording;

      this.scheduler =
          Executors.newSingleThreadScheduledExecutor(
              r -> {
                Thread t = new Thread(r, "openaev-debug-jfr");
                t.setDaemon(true);
                return t;
              });
      long periodSeconds = Math.max(1, config.getDuration().toSeconds());
      scheduler.scheduleAtFixedRate(
          this::dumpQuietly, periodSeconds, periodSeconds, TimeUnit.SECONDS);

      this.status = Status.RUNNING;
      log.warn(
          "Debug mode: JFR recording started (settings={}, maxSize={}, maxAge={}, dumpEvery={}, dir={})",
          config.getSettings(),
          config.getMaxSize(),
          config.getMaxAge(),
          config.getDuration(),
          outputDir.toAbsolutePath());
    } catch (Exception e) {
      this.status = Status.FAILED;
      this.lastError = e.getMessage();
      log.error(
          "Debug mode: failed to start JFR recording. Profiling is disabled, the rest of the "
              + "application keeps running. Configure a writable 'openaev.debug.output-dir' "
              + "(mount a writable volume on a read-only container filesystem). Cause: {}",
          e.getMessage());
    }
  }

  /** Dumps a final snapshot and stops the recording. Safe to call when not running. */
  public synchronized void stop() {
    if (scheduler != null) {
      // Graceful shutdown lets an in-flight dump finish; only interrupt if it overruns.
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          log.warn("Debug mode: JFR dump thread did not finish in time; forcing shutdown");
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
      scheduler = null;
    }
    if (recording != null) {
      try {
        dump("final");
      } catch (Exception e) {
        log.error("Debug mode: failed to write final JFR dump: {}", e.getMessage());
      }
      recording.stop();
      recording.close();
      recording = null;
    }
    status = Status.INACTIVE;
  }

  private Path prepareWritableOutputDir() throws IOException {
    Path dir = Path.of(outputDirPath);
    Files.createDirectories(dir);
    if (!Files.isWritable(dir)) {
      throw new IOException("JFR output directory is not writable: " + dir.toAbsolutePath());
    }
    return dir;
  }

  private void dumpQuietly() {
    try {
      dump(String.valueOf(dumpCounter.incrementAndGet()));
    } catch (Exception e) {
      log.error("Debug mode: scheduled JFR dump failed: {}", e.getMessage());
    }
  }

  private void dump(String suffix) throws IOException {
    Path target =
        outputDir.resolve(
            "openaev-debug-" + LocalDateTime.now().format(FILE_STAMP) + "-" + suffix + ".jfr");
    // Dump a stopped snapshot so the live recording keeps running.
    try (Recording snapshot = recording.copy(true)) {
      snapshot.dump(target);
    }
    log.info("Debug mode: JFR snapshot written to {}", target.toAbsolutePath());
    enforceDumpRetention();
  }

  /** Deletes the oldest dumps so the count and total size stay under the configured caps. */
  private void enforceDumpRetention() {
    int maxFiles = config.getMaxDumpFiles();
    long maxBytes = config.getMaxTotalDumpSize().toBytes();
    try (Stream<Path> stream = Files.list(outputDir)) {
      List<Path> dumps =
          stream
              .filter(JfrRecordingManager::isDumpFile)
              .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
              .toList();
      long totalBytes = dumps.stream().mapToLong(p -> p.toFile().length()).sum();
      int remaining = dumps.size();
      for (Path oldest : dumps) {
        if (remaining <= maxFiles && totalBytes <= maxBytes) {
          break;
        }
        long len = oldest.toFile().length();
        if (Files.deleteIfExists(oldest)) {
          totalBytes -= len;
          remaining--;
          log.info("Debug mode: pruned old JFR dump {}", oldest.getFileName());
        }
      }
    } catch (IOException e) {
      log.warn("Debug mode: JFR dump retention failed: {}", e.getMessage());
    }
  }

  private static boolean isDumpFile(Path path) {
    String name = path.getFileName().toString();
    return name.startsWith("openaev-debug-") && name.endsWith(".jfr");
  }
}
