package io.openaev.xtmhub.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("XtmHubCollectorConfig news feed cleanup tests")
class XtmHubCollectorConfigTest {

  // A leap year and a month of 31 days, to tell the calendar units from a fixed duration
  private static final Instant NOW = Instant.parse("2028-03-31T10:15:30Z");

  private static XtmHubCollectorConfig config(String value, String unit) {
    XtmHubCollectorConfig config = new XtmHubCollectorConfig();
    config.setNewsFeedCleanupIntervalValue(value);
    config.setNewsFeedCleanupIntervalUnit(unit);
    return config;
  }

  @ParameterizedTest(name = "Given {0} {1}, should keep the items created since {2}")
  @CsvSource({
    "90,seconds,2028-03-31T10:14:00Z",
    "90,minutes,2028-03-31T08:45:30Z",
    "36,hours,2028-03-29T22:15:30Z",
    "180,days,2027-10-03T10:15:30Z",
    "2,weeks,2028-03-17T10:15:30Z",
    "1,months,2028-02-29T10:15:30Z",
    "6,months,2027-09-30T10:15:30Z",
    "1,years,2027-03-31T10:15:30Z",
    "4,years,2024-03-31T10:15:30Z",
    "6,MONTHS,2027-09-30T10:15:30Z",
    "' 6 ',' Months ',2027-09-30T10:15:30Z"
  })
  void given_validRetention_should_computeTheCutoffOnTheUtcCalendar(
      String value, String unit, Instant expectedCutoff) {
    // Arrange
    XtmHubCollectorConfig config = config(value, unit);

    // Act
    Instant cutoff = config.newsFeedCleanupCutoff(NOW);

    // Assert
    assertThat(config.isNewsFeedCleanupIntervalValid()).isTrue();
    assertThat(cutoff).isEqualTo(expectedCutoff);
  }

  @ParameterizedTest(name = "Given value {0} and unit {1}, should fall back to 180 days")
  @CsvSource(
      value = {
        "abc,days",
        "1.5,days",
        "0,days",
        "-3,days",
        "99999999999,days",
        "'',days",
        "6,fortnights",
        "6,''",
        "abc,fortnights",
        "NULL,days",
        "6,NULL"
      },
      nullValues = "NULL")
  void given_invalidRetention_should_fallBackToTheDefaults(String value, String unit) {
    // Arrange
    XtmHubCollectorConfig config = config(value, unit);

    // Act
    Instant cutoff = config.newsFeedCleanupCutoff(NOW);

    // Assert: both fall back together, a valid half does not survive an invalid one
    assertThat(config.isNewsFeedCleanupIntervalValid()).isFalse();
    assertThat(cutoff).isEqualTo(Instant.parse("2027-10-03T10:15:30Z"));
  }

  @ParameterizedTest(name = "Given {0} {1}, should keep everything")
  @CsvSource({"2000,years", "2147483647,years", "2147483647,months"})
  void given_retentionBeyondTheEpoch_should_keepEverything(String value, String unit) {
    // Arrange
    XtmHubCollectorConfig config = config(value, unit);

    // Act
    Instant cutoff = config.newsFeedCleanupCutoff(NOW);

    // Assert: nothing is older than the epoch
    assertThat(config.isNewsFeedCleanupIntervalValid()).isTrue();
    assertThat(cutoff).isEqualTo(Instant.EPOCH);
  }

  @Test
  @DisplayName("Given an unconfigured retention, should default to 180 days")
  void given_unconfiguredRetention_should_defaultTo180Days() {
    // Arrange
    XtmHubCollectorConfig config = new XtmHubCollectorConfig();

    // Act
    Instant cutoff = config.newsFeedCleanupCutoff(NOW);

    // Assert
    assertThat(config.isNewsFeedCleanupIntervalValid()).isFalse();
    assertThat(XtmHubCollectorConfig.DEFAULT_NEWS_FEED_CLEANUP_VALUE).isEqualTo(180);
    assertThat(XtmHubCollectorConfig.DEFAULT_NEWS_FEED_CLEANUP_UNIT).isEqualTo("days");
    assertThat(cutoff).isEqualTo(Instant.parse("2027-10-03T10:15:30Z"));
  }
}
