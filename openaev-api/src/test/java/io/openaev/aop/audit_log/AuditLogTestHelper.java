package io.openaev.aop.audit_log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.slf4j.LoggerFactory;

/**
 * Shared test utilities for audit-log integration tests.
 *
 * <p>Provides helpers for wiring a Logback {@link FileAppender} to the {@code AUDIT_LOG} logger and
 * asserting on log file content, so individual test classes avoid duplicating this boilerplate.
 */
public final class AuditLogTestHelper {

  private AuditLogTestHelper() {}

  /**
   * Registers a {@link FileAppender} on the {@code AUDIT_LOG} logger that writes to {@code
   * logFile}. Idempotent: no-op if an appender with {@code appenderName} already exists.
   *
   * <p>Call from {@code @BeforeAll}.
   */
  public static void setupFileAppender(Path logFile, String appenderName) throws Exception {
    Files.createDirectories(logFile.getParent());
    Files.deleteIfExists(logFile);

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger auditLog = context.getLogger("AUDIT_LOG");

    if (auditLog.getAppender(appenderName) == null) {
      PatternLayoutEncoder encoder = new PatternLayoutEncoder();
      encoder.setContext(context);
      encoder.setPattern("%msg%n");
      encoder.start();

      FileAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new FileAppender<>();
      appender.setName(appenderName);
      appender.setContext(context);
      appender.setFile(logFile.toString());
      appender.setAppend(true);
      appender.setEncoder(encoder);
      appender.start();

      auditLog.addAppender(appender);
      auditLog.setAdditive(false);
    }
  }

  /**
   * Stops and detaches the named appender from the {@code AUDIT_LOG} logger.
   *
   * <p>Call from {@code @AfterAll}.
   */
  public static void teardownFileAppender(String appenderName) {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger auditLog = context.getLogger("AUDIT_LOG");
    var appender = auditLog.getAppender(appenderName);
    if (appender != null) {
      appender.stop();
      auditLog.detachAppender(appenderName);
    }
  }

  /**
   * Asserts that new content was appended to {@code logFile} after {@code sizeBefore} and that it
   * contains every {@code expectedSnippets}. Waits up to 10 seconds for async flush.
   *
   * @return the new content appended after {@code sizeBefore}
   */
  public static String assertAuditLogContainsNewContent(
      Path logFile, long sizeBefore, String... expectedSnippets) {
    final String[] captured = new String[1];

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(logFile).exists();
              long sizeAfter = Files.size(logFile);
              assertThat(sizeAfter).isGreaterThan(sizeBefore);

              String fullContent = Files.readString(logFile, StandardCharsets.UTF_8);
              String newContent =
                  fullContent.substring((int) Math.min(sizeBefore, fullContent.length()));

              for (String expectedSnippet : expectedSnippets) {
                assertThat(newContent).contains(expectedSnippet);
              }
              captured[0] = newContent;
            });

    return captured[0];
  }

  /**
   * Asserts that no new content matching {@code unexpectedSnippet} was written to {@code logFile}
   * after {@code sizeBefore}. Should be called after {@link #assertAuditLogContainsNewContent} has
   * already confirmed the expected event was flushed, so any spurious sibling event is already
   * present.
   */
  public static void assertAuditLogDoesNotContainNewContent(
      Path logFile, long sizeBefore, String unexpectedSnippet) throws Exception {
    if (!Files.exists(logFile)) {
      return;
    }
    String fullContent = Files.readString(logFile, StandardCharsets.UTF_8);
    String newContent = fullContent.substring((int) Math.min(sizeBefore, fullContent.length()));
    assertThat(newContent).doesNotContain(unexpectedSnippet);
  }
}
