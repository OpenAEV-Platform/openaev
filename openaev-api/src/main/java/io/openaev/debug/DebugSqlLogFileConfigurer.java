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
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

/**
 * Routes the verbose debug logs to rotated files under {@code output-dir}, off the console, so they
 * do not flood stdout (and, on instances shipping the console to centralised logging, that
 * pipeline). The SQL log ({@code io.openaev.debug.sql}) is always routed; the per-request ORM
 * summary ({@code io.openaev.debug.orm}) is routed too when {@code
 * openaev.debug.orm.summary-to-file} is on. File rotation is configurable via {@code
 * openaev.debug.sql.*}; invalid rotation values are corrected to safe ones (with a warning) rather
 * than fed to Logback, where they would silently disable cleanup or prevent the rolling policy from
 * starting. If the dir is not writable it warns and leaves the loggers on the console.
 */
public class DebugSqlLogFileConfigurer {

  private static final org.slf4j.Logger log =
      LoggerFactory.getLogger(DebugSqlLogFileConfigurer.class);

  private static final String SQL_LOGGER = "io.openaev.debug.sql";
  private static final String SQL_FILE = "openaev-debug-sql";
  private static final String SQL_PATTERN =
      "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level "
          + "[trace=%X{traceId:-} tenant=%X{tenant:-} user=%X{user:-}] %msg%n";

  private static final String ORM_LOGGER = "io.openaev.debug.orm";
  private static final String ORM_FILE = "openaev-debug-orm";
  private static final String ORM_PATTERN =
      "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [trace=%X{traceId:-} tenant=%X{tenant:-}] %msg%n";

  private final String outputDirPath;
  private final DebugProperties.Sql sqlConfig;
  private final boolean ormSummaryToFile;

  private final List<RoutedLogger> routed = new ArrayList<>();
  private volatile boolean attached;

  public DebugSqlLogFileConfigurer(
      String outputDirPath, DebugProperties.Sql sqlConfig, boolean ormSummaryToFile) {
    this.outputDirPath = outputDirPath;
    this.sqlConfig = sqlConfig;
    this.ormSummaryToFile = ormSummaryToFile;
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
          "Debug mode: log directory is not writable ({}). Debug logs stay on the console instead "
              + "of a rotated file. Point openaev.debug.output-dir at a writable volume.",
          e.getMessage());
      return;
    }

    DebugProperties.Sql rotation = sanitizedRotation(sqlConfig);

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    routeToFile(context, dir, rotation, SQL_LOGGER, SQL_FILE, SQL_PATTERN);
    if (ormSummaryToFile) {
      routeToFile(context, dir, rotation, ORM_LOGGER, ORM_FILE, ORM_PATTERN);
    }
    attached = true;
  }

  /**
   * Returns rotation settings that are safe to hand to Logback, correcting invalid values with a
   * warning: a non-positive {@code max-file-size} or {@code total-size-cap} falls back to its
   * default; {@code max-history} must be at least 1 (in Logback, {@code 0} means unbounded history
   * and disables the cleanup pass entirely, including the total size cap); {@code total-size-cap}
   * is raised to {@code max-file-size} when smaller (a smaller cap prevents the rolling policy from
   * starting).
   */
  static DebugProperties.Sql sanitizedRotation(DebugProperties.Sql config) {
    DebugProperties.Sql defaults = new DebugProperties.Sql();
    DebugProperties.Sql sanitized = new DebugProperties.Sql();
    boolean corrected = false;

    DataSize maxFileSize = config.getMaxFileSize();
    if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
      maxFileSize = defaults.getMaxFileSize();
      corrected = true;
    }
    sanitized.setMaxFileSize(maxFileSize);

    int maxHistory = config.getMaxHistory();
    if (maxHistory <= 0) {
      maxHistory = defaults.getMaxHistory();
      corrected = true;
    }
    sanitized.setMaxHistory(maxHistory);

    DataSize totalSizeCap = config.getTotalSizeCap();
    if (totalSizeCap == null || totalSizeCap.toBytes() <= 0) {
      totalSizeCap = defaults.getTotalSizeCap();
      corrected = true;
    }
    if (totalSizeCap.toBytes() < maxFileSize.toBytes()) {
      totalSizeCap = maxFileSize;
      corrected = true;
    }
    sanitized.setTotalSizeCap(totalSizeCap);

    if (corrected) {
      log.warn(
          "Debug mode: invalid SQL log rotation settings corrected. Effective values: "
              + "max-file-size={}, max-history={}, total-size-cap={} "
              + "(max-file-size and total-size-cap must be positive, max-history at least 1, "
              + "and total-size-cap at least max-file-size).",
          sanitized.getMaxFileSize(),
          sanitized.getMaxHistory(),
          sanitized.getTotalSizeCap());
    }
    return sanitized;
  }

  private void routeToFile(
      LoggerContext context,
      Path dir,
      DebugProperties.Sql rotation,
      String loggerName,
      String baseName,
      String pattern) {
    Path file = dir.resolve(baseName + ".log");

    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern(pattern);
    encoder.start();

    RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
    fileAppender.setContext(context);
    fileAppender.setName(baseName);
    fileAppender.setFile(file.toString());
    fileAppender.setEncoder(encoder);

    SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy = new SizeAndTimeBasedRollingPolicy<>();
    policy.setContext(context);
    policy.setParent(fileAppender);
    policy.setFileNamePattern(dir.resolve(baseName + ".%d{yyyy-MM-dd}.%i.log").toString());
    policy.setMaxFileSize(new FileSize(rotation.getMaxFileSize().toBytes()));
    policy.setMaxHistory(rotation.getMaxHistory());
    policy.setTotalSizeCap(new FileSize(rotation.getTotalSizeCap().toBytes()));
    policy.start();

    fileAppender.setRollingPolicy(policy);
    fileAppender.start();

    Logger logger = context.getLogger(loggerName);
    logger.setAdditive(false); // off the console
    logger.addAppender(fileAppender);

    routed.add(new RoutedLogger(loggerName, fileAppender));
    log.warn("Debug mode: {} is written to the rotated file {}", loggerName, file.toAbsolutePath());
  }

  @PreDestroy
  public synchronized void stop() {
    if (!attached) {
      return;
    }
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    for (RoutedLogger r : routed) {
      Logger logger = context.getLogger(r.loggerName());
      logger.detachAppender(r.appender());
      logger.setAdditive(true);
      r.appender().stop();
    }
    routed.clear();
    attached = false;
  }

  private record RoutedLogger(String loggerName, RollingFileAppender<ILoggingEvent> appender) {}
}
