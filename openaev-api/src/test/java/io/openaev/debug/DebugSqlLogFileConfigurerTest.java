package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

@DisplayName("DebugSqlLogFileConfigurer")
class DebugSqlLogFileConfigurerTest {

  private DebugSqlLogFileConfigurer configurer;

  @AfterEach
  void cleanup() {
    if (configurer != null) {
      configurer.stop();
    }
  }

  private Logger sqlLogger() {
    return (Logger) LoggerFactory.getLogger("io.openaev.debug.sql");
  }

  private Logger ormLogger() {
    return (Logger) LoggerFactory.getLogger("io.openaev.debug.orm");
  }

  private RollingFileAppender<?> sqlFileAppenderUnder(Path dir) {
    var appenders = sqlLogger().iteratorForAppenders();
    while (appenders.hasNext()) {
      if (appenders.next() instanceof RollingFileAppender<?> rfa
          && rfa.getFile() != null
          && rfa.getFile().startsWith(dir.toString())) {
        return rfa;
      }
    }
    throw new AssertionError("no SQL file appender under " + dir);
  }

  @Test
  @DisplayName("routes SQL logs to a rotated file, off the console (additivity false)")
  void routesToFile(@TempDir Path tmp) throws Exception {
    sqlLogger().setLevel(Level.INFO);
    configurer = new DebugSqlLogFileConfigurer(tmp.toString(), new DebugProperties.Sql(), false);
    configurer.start();

    assertThat(configurer.isAttached()).isTrue();
    assertThat(sqlLogger().isAdditive()).as("SQL flood must stay off the console").isFalse();

    LoggerFactory.getLogger("io.openaev.debug.sql").info("sql line for the file");

    Path file = tmp.resolve("openaev-debug-sql.log");
    assertThat(Files.exists(file)).isTrue();
    assertThat(Files.readString(file)).contains("sql line for the file");
  }

  @Test
  @DisplayName("applies the configured rotation onto a size+time based policy")
  void appliesConfiguredRotation(@TempDir Path tmp) {
    // maxHistory is the readable proxy for the whole rotation config: the three values are set on
    // the same policy from the same config object, so a wired maxHistory proves the plumbing (file
    // size and total-size cap have no public getter on the logback policy to assert directly).
    DebugProperties.Sql cfg = new DebugProperties.Sql();
    cfg.setMaxFileSize(DataSize.ofMegabytes(10));
    cfg.setMaxHistory(3);
    cfg.setTotalSizeCap(DataSize.ofGigabytes(5));
    configurer = new DebugSqlLogFileConfigurer(tmp.toString(), cfg, false);
    configurer.start();

    // Find the appender this configurer created by its file (a cached debug-on E2E context may have
    // attached another appender of the same name to the shared sql logger).
    RollingFileAppender<?> appender = sqlFileAppenderUnder(tmp);
    assertThat(appender.getRollingPolicy()).isInstanceOf(SizeAndTimeBasedRollingPolicy.class);
    SizeAndTimeBasedRollingPolicy<?> policy =
        (SizeAndTimeBasedRollingPolicy<?>) appender.getRollingPolicy();

    assertThat(policy.getMaxHistory()).isEqualTo(3);
  }

  @Test
  @DisplayName("corrects maxHistory=0 (unbounded in Logback, disables cleanup) to the default")
  void correctsZeroMaxHistory(@TempDir Path tmp) {
    // In Logback, maxHistory=0 means unbounded history and disables the cleanup pass entirely,
    // including the total size cap: it must never be handed to the policy as-is.
    DebugProperties.Sql cfg = new DebugProperties.Sql();
    cfg.setMaxHistory(0);
    configurer = new DebugSqlLogFileConfigurer(tmp.toString(), cfg, false);
    configurer.start();

    RollingFileAppender<?> appender = sqlFileAppenderUnder(tmp);
    SizeAndTimeBasedRollingPolicy<?> policy =
        (SizeAndTimeBasedRollingPolicy<?>) appender.getRollingPolicy();
    assertThat(policy.getMaxHistory()).isEqualTo(new DebugProperties.Sql().getMaxHistory());
  }

  @Test
  @DisplayName("sanitizes invalid rotation values (positives, cap >= file size)")
  void sanitizesInvalidRotation() {
    DebugProperties.Sql cfg = new DebugProperties.Sql();
    cfg.setMaxFileSize(DataSize.ofBytes(0));
    cfg.setMaxHistory(-3);
    cfg.setTotalSizeCap(DataSize.ofMegabytes(-1));
    DebugProperties.Sql defaults = new DebugProperties.Sql();

    DebugProperties.Sql sanitized = DebugSqlLogFileConfigurer.sanitizedRotation(cfg);

    assertThat(sanitized.getMaxFileSize()).isEqualTo(defaults.getMaxFileSize());
    assertThat(sanitized.getMaxHistory()).isEqualTo(defaults.getMaxHistory());
    assertThat(sanitized.getTotalSizeCap()).isEqualTo(defaults.getTotalSizeCap());
  }

  @Test
  @DisplayName("raises a total-size-cap smaller than max-file-size (blocks the rolling policy)")
  void raisesTooSmallTotalSizeCap() {
    DebugProperties.Sql cfg = new DebugProperties.Sql();
    cfg.setMaxFileSize(DataSize.ofMegabytes(500));
    cfg.setTotalSizeCap(DataSize.ofMegabytes(100));

    DebugProperties.Sql sanitized = DebugSqlLogFileConfigurer.sanitizedRotation(cfg);

    assertThat(sanitized.getTotalSizeCap()).isEqualTo(DataSize.ofMegabytes(500));
  }

  @Test
  @DisplayName("keeps valid rotation values unchanged")
  void keepsValidRotation() {
    DebugProperties.Sql cfg = new DebugProperties.Sql();
    cfg.setMaxFileSize(DataSize.ofMegabytes(10));
    cfg.setMaxHistory(3);
    cfg.setTotalSizeCap(DataSize.ofGigabytes(5));

    DebugProperties.Sql sanitized = DebugSqlLogFileConfigurer.sanitizedRotation(cfg);

    assertThat(sanitized.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
    assertThat(sanitized.getMaxHistory()).isEqualTo(3);
    assertThat(sanitized.getTotalSizeCap()).isEqualTo(DataSize.ofGigabytes(5));
  }

  @Test
  @DisplayName("routes the ORM summary to its own file when summary-to-file is on")
  void routesOrmToFileWhenEnabled(@TempDir Path tmp) throws Exception {
    ormLogger().setLevel(Level.INFO);
    configurer = new DebugSqlLogFileConfigurer(tmp.toString(), new DebugProperties.Sql(), true);
    configurer.start();

    assertThat(ormLogger().isAdditive()).as("ORM summary off the console").isFalse();
    LoggerFactory.getLogger("io.openaev.debug.orm").info("orm summary for the file");

    Path file = tmp.resolve("openaev-debug-orm.log");
    assertThat(Files.exists(file)).isTrue();
    assertThat(Files.readString(file)).contains("orm summary for the file");
  }

  @Test
  @DisplayName("leaves the ORM logger on the console when summary-to-file is off (default)")
  void ormStaysOnConsoleByDefault(@TempDir Path tmp) {
    ormLogger().setAdditive(true);
    configurer = new DebugSqlLogFileConfigurer(tmp.toString(), new DebugProperties.Sql(), false);
    configurer.start();

    assertThat(ormLogger().isAdditive()).isTrue();
    assertThat(Files.exists(tmp.resolve("openaev-debug-orm.log"))).isFalse();
  }

  @Test
  @DisplayName("restores the console route on stop (both loggers)")
  void restoresOnStop(@TempDir Path tmp) {
    configurer = new DebugSqlLogFileConfigurer(tmp.toString(), new DebugProperties.Sql(), true);
    configurer.start();
    assertThat(sqlLogger().isAdditive()).isFalse();
    assertThat(ormLogger().isAdditive()).isFalse();

    configurer.stop();

    assertThat(configurer.isAttached()).isFalse();
    assertThat(sqlLogger().isAdditive()).as("SQL console route restored").isTrue();
    assertThat(ormLogger().isAdditive()).as("ORM console route restored").isTrue();
  }

  @Test
  @DisplayName("falls back to the console (no crash) when the directory is not writable")
  void fallsBackWhenNotWritable(@TempDir Path tmp) throws IOException {
    Path blockingFile = Files.createFile(tmp.resolve("not-a-dir"));
    Path unwritable = blockingFile.resolve("debug");
    configurer =
        new DebugSqlLogFileConfigurer(unwritable.toString(), new DebugProperties.Sql(), false);

    configurer.start(); // must not throw

    assertThat(configurer.isAttached()).isFalse();
    assertThat(sqlLogger().isAdditive()).as("SQL logs keep going to the console").isTrue();
  }
}
