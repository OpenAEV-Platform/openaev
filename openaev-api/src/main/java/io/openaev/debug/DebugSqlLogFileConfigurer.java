package io.openaev.debug;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;

/**
 * Routes the verbose SQL log ({@code io.openaev.debug.sql}) to a rotated file under {@code
 * output-dir}, off the console, so it does not flood stdout. If the dir is not writable it warns
 * and leaves the logger on the console.
 */
public class DebugSqlLogFileConfigurer {

  private static final org.slf4j.Logger log =
      LoggerFactory.getLogger(DebugSqlLogFileConfigurer.class);
  private static final String SQL_LOGGER = "io.openaev.debug.sql";
  private static final String FILE_NAME = "openaev-debug-sql.log";

  private final String outputDirPath;

  private RollingFileAppender<ILoggingEvent> appender;
  private volatile boolean attached;

  public DebugSqlLogFileConfigurer(String outputDirPath) {
    this.outputDirPath = outputDirPath;
  }

  public boolean isAttached() {
    return attached;
  }

  @PostConstruct
  public synchronized void start() {
    if (attached) {
      return;
    }
    Path dir = Path.of(outputDirPath);
    try {
      Files.createDirectories(dir);
      if (!Files.isWritable(dir)) {
        throw new IOException("not writable: " + dir.toAbsolutePath());
      }
    } catch (IOException e) {
      log.warn(
          "Debug mode: SQL log directory is not writable ({}). SQL statements stay on the console "
              + "instead of a rotated file. Point openaev.debug.output-dir at a writable volume.",
          e.getMessage());
      return;
    }

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Path file = dir.resolve(FILE_NAME);

    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern(
        "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [trace=%X{traceId:-} tenant=%X{tenant:-}] %msg%n");
    encoder.start();

    RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
    fileAppender.setContext(context);
    fileAppender.setName("openaev-debug-sql");
    fileAppender.setFile(file.toString());
    fileAppender.setEncoder(encoder);

    SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy = new SizeAndTimeBasedRollingPolicy<>();
    policy.setContext(context);
    policy.setParent(fileAppender);
    policy.setFileNamePattern(dir.resolve("openaev-debug-sql.%d{yyyy-MM-dd}.%i.log").toString());
    policy.setMaxFileSize(FileSize.valueOf("50MB"));
    policy.setMaxHistory(7);
    policy.setTotalSizeCap(FileSize.valueOf("500MB"));
    policy.start();

    fileAppender.setRollingPolicy(policy);
    fileAppender.start();

    Logger sqlLogger = context.getLogger(SQL_LOGGER);
    sqlLogger.setAdditive(false); // off the console
    sqlLogger.addAppender(fileAppender);

    this.appender = fileAppender;
    this.attached = true;
    log.warn(
        "Debug mode: SQL statements are written to the rotated file {}", file.toAbsolutePath());
  }

  @PreDestroy
  public synchronized void stop() {
    if (!attached) {
      return;
    }
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger sqlLogger = context.getLogger(SQL_LOGGER);
    sqlLogger.detachAppender(appender);
    sqlLogger.setAdditive(true);
    appender.stop();
    appender = null;
    attached = false;
  }
}
