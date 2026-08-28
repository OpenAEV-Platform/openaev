package io.openaev.config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Test-only inspector that records the SQL Hibernate actually emits without altering it. Two modes,
 * usable together:
 *
 * <ul>
 *   <li><b>in-memory</b> — capture between {@link #start()} and {@link #stop()} (used by the
 *       per-entity gate), off by default so the SQL run at context start-up is ignored;
 *   <li><b>file</b> — when {@code -Dtenant.sql.capture.file=<path>} is set, every inspected
 *       statement is appended (one normalized line each) to that file, so a real test run can be
 *       captured and replayed (T1b).
 * </ul>
 *
 * <p>Registered through {@code hibernate.session_factory.statement_inspector}; Hibernate builds a
 * single instance, hence the static collectors.
 */
public class CapturingStatementInspector implements StatementInspector {

  private static final List<String> CAPTURED = new CopyOnWriteArrayList<>();
  private static volatile boolean capturing = false;

  private static final String CAPTURE_FILE = System.getProperty("tenant.sql.capture.file");
  private static final Object FILE_LOCK = new Object();
  private static BufferedWriter fileWriter;

  static void start() {
    CAPTURED.clear();
    capturing = true;
  }

  static void stop() {
    capturing = false;
  }

  static List<String> captured() {
    return Collections.unmodifiableList(CAPTURED);
  }

  @Override
  public String inspect(String sql) {
    if (sql != null) {
      if (capturing) {
        CAPTURED.add(sql);
      }
      if (CAPTURE_FILE != null) {
        appendToFile(sql);
      }
    }
    return sql;
  }

  private static void appendToFile(String sql) {
    String line = sql.replaceAll("\\s+", " ").trim();
    synchronized (FILE_LOCK) {
      try {
        writer().write(line);
        writer().write('\n');
        writer().flush();
      } catch (IOException e) {
        throw new UncheckedIOException("failed to capture SQL to " + CAPTURE_FILE, e);
      }
    }
  }

  private static BufferedWriter writer() throws IOException {
    if (fileWriter == null) {
      fileWriter =
          Files.newBufferedWriter(
              Path.of(CAPTURE_FILE),
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE,
              StandardOpenOption.APPEND);
      Runtime.getRuntime().addShutdownHook(new Thread(CapturingStatementInspector::closeQuietly));
    }
    return fileWriter;
  }

  private static void closeQuietly() {
    synchronized (FILE_LOCK) {
      if (fileWriter != null) {
        try {
          fileWriter.close();
        } catch (IOException ignored) {
          // best effort on shutdown
        }
      }
    }
  }
}
