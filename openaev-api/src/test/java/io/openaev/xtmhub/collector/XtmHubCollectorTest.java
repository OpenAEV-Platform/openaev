package io.openaev.xtmhub.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.openaev.service.NewsFeedItemService;
import io.openaev.xtmhub.XtmHubService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
@DisplayName("XtmHubCollector tests")
class XtmHubCollectorTest {

  @Mock private ThreadPoolTaskScheduler taskScheduler;
  @Mock private XtmHubService xtmHubService;
  @Mock private NewsFeedItemService newsFeedItemService;

  private XtmHubCollectorConfig config;
  private XtmHubCollector collector;

  @BeforeEach
  void setUp() {
    config = new XtmHubCollectorConfig();
    config.setConnectivityCheckInterval(7200);
    config.setNewsFeedInterval(1800);
    collector = new XtmHubCollector(config, taskScheduler, xtmHubService, newsFeedItemService);
  }

  private List<Duration> scheduledPeriods() {
    ArgumentCaptor<Duration> periods = ArgumentCaptor.forClass(Duration.class);
    verify(taskScheduler, times(2)).scheduleAtFixedRate(any(Runnable.class), periods.capture());
    return periods.getAllValues();
  }

  @Test
  @DisplayName("Given the collector is disabled, should schedule nothing")
  void given_collectorDisabled_should_scheduleNothing() {
    // Arrange
    config.setEnable(false);

    // Act
    collector.init();

    // Assert
    verify(taskScheduler, never()).scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
  }

  @Test
  @DisplayName("Given the collector is enabled, should schedule both tasks with their intervals")
  void given_collectorEnabled_should_scheduleBothTasks() {
    // Arrange
    config.setEnable(true);

    // Act
    collector.init();

    // Assert
    assertThat(scheduledPeriods())
        .containsExactly(Duration.ofSeconds(7200), Duration.ofSeconds(1800));
  }

  @Test
  @DisplayName("Given a non-positive news feed interval, should schedule it with the default")
  void given_nonPositiveNewsFeedInterval_should_useDefault() {
    // Arrange
    config.setEnable(true);
    config.setNewsFeedInterval(0);

    // Act
    collector.init();

    // Assert
    assertThat(scheduledPeriods())
        .containsExactly(
            Duration.ofSeconds(7200),
            Duration.ofSeconds(XtmHubCollectorConfig.DEFAULT_NEWS_FEED_INTERVAL));
  }

  @Test
  @DisplayName("Given a negative news feed interval, should use the default")
  void given_negativeNewsFeedInterval_should_useDefault() {
    // Arrange
    config.setNewsFeedInterval(-5);

    // Act & Assert
    assertThat(config.getEffectiveNewsFeedInterval())
        .isEqualTo(XtmHubCollectorConfig.DEFAULT_NEWS_FEED_INTERVAL);
  }
}
