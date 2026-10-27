package io.openaev.scheduler;

import static io.openaev.scheduler.PlatformTriggers.CREDENTIALS_STATUS_VALIDATION_DEFAULT_CRON;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.CronExpression;

/**
 * Guards the cadence of the credential status validation job: each run makes one outbound call per
 * stale credential, so it must stay every 6 hours. Quartz cron expressions start with a seconds
 * field: a step written in the second field targets the minutes, not the hours.
 */
@DisplayName("Credentials status validation cron")
class CredentialsStatusValidationCronTest {

  private static final Instant MIDNIGHT = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  @DisplayName("Given no configured cron, should default to every 6 hours")
  void given_noConfiguredCron_should_defaultToEverySixHours() throws Exception {
    // Act & Assert
    assertFiresEverySixHours(CREDENTIALS_STATUS_VALIDATION_DEFAULT_CRON);
  }

  @Test
  @DisplayName("Given the production config, should run every 6 hours")
  void given_productionConfig_should_runEverySixHours() throws Exception {
    // Arrange
    // The test classpath shadows application.properties with the test config: read the production
    // file directly.
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }

    // Act
    String cron = props.getProperty("openaev.credentials.status-validation.cron");

    // Assert
    assertThat(cron).isEqualTo(CREDENTIALS_STATUS_VALIDATION_DEFAULT_CRON);
    assertFiresEverySixHours(cron);
  }

  private static void assertFiresEverySixHours(String cron) throws Exception {
    CronExpression expression = new CronExpression(cron);
    expression.setTimeZone(TimeZone.getTimeZone("UTC"));

    Date first = expression.getNextValidTimeAfter(Date.from(MIDNIGHT));
    Date second = expression.getNextValidTimeAfter(first);

    assertThat(first.toInstant()).isEqualTo(MIDNIGHT.plus(Duration.ofHours(6)));
    assertThat(second.toInstant()).isEqualTo(MIDNIGHT.plus(Duration.ofHours(12)));
  }
}
