package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

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

  @Test
  @DisplayName("routes SQL logs to a rotated file, off the console (additivity false)")
  void routesToFile(@TempDir Path tmp) throws Exception {
    sqlLogger().setLevel(Level.INFO);
    configurer = new DebugSqlLogFileConfigurer(tmp.toString());
    configurer.start();

    assertThat(configurer.isAttached()).isTrue();
    assertThat(sqlLogger().isAdditive()).as("SQL flood must stay off the console").isFalse();

    LoggerFactory.getLogger("io.openaev.debug.sql").info("sql line for the file");

    Path file = tmp.resolve("openaev-debug-sql.log");
    assertThat(Files.exists(file)).isTrue();
    assertThat(Files.readString(file)).contains("sql line for the file");
  }

  @Test
  @DisplayName("restores the console route on stop")
  void restoresOnStop(@TempDir Path tmp) {
    configurer = new DebugSqlLogFileConfigurer(tmp.toString());
    configurer.start();
    assertThat(sqlLogger().isAdditive()).isFalse();

    configurer.stop();

    assertThat(configurer.isAttached()).isFalse();
    assertThat(sqlLogger().isAdditive()).as("console route restored").isTrue();
  }

  @Test
  @DisplayName("falls back to the console (no crash) when the directory is not writable")
  void fallsBackWhenNotWritable(@TempDir Path tmp) throws IOException {
    Path blockingFile = Files.createFile(tmp.resolve("not-a-dir"));
    Path unwritable = blockingFile.resolve("debug");
    configurer = new DebugSqlLogFileConfigurer(unwritable.toString());

    configurer.start(); // must not throw

    assertThat(configurer.isAttached()).isFalse();
    assertThat(sqlLogger().isAdditive()).as("SQL logs keep going to the console").isTrue();
  }
}
