package io.openaev.xtmhub.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
@DisplayName("XtmHubCollector tests")
class XtmHubCollectorTest {

  @Mock private ThreadPoolTaskScheduler taskScheduler;
  @Mock private XtmHubConnectivityCollectorService connectivityCollectorService;
  @Mock private XtmHubNewsFeedCollectorService newsFeedCollectorService;

  private XtmHubCollectorConfig config;
  private XtmHubCollector collector;

  @BeforeEach
  void setUp() {
    config = new XtmHubCollectorConfig();
    config.setConnectivityCheckInterval(7200);
    config.setNewsFeedInterval(1800);
    collector =
        new XtmHubCollector(
            config, taskScheduler, connectivityCollectorService, newsFeedCollectorService);
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
    verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
  }

  @Test
  @DisplayName("Given the collector is enabled, should schedule both tasks with their intervals")
  void given_collectorEnabled_should_scheduleBothTasks() {
    // Arrange
    config.setEnable(true);

    // Act
    collector.init();

    // Assert
    verify(taskScheduler)
        .scheduleAtFixedRate(connectivityCollectorService, Duration.ofSeconds(7200));
    verify(taskScheduler)
        .scheduleWithFixedDelay(newsFeedCollectorService, Duration.ofSeconds(1800));
  }

  @Test
  @DisplayName("Given non-positive intervals, should schedule the tasks with the default")
  void given_nonPositiveIntervals_should_useDefault() {
    // Arrange
    config.setEnable(true);
    config.setConnectivityCheckInterval(-5);
    config.setNewsFeedInterval(0);
    Duration defaultInterval = Duration.ofSeconds(XtmHubCollectorConfig.DEFAULT_INTERVAL);

    // Act
    collector.init();

    // Assert
    verify(taskScheduler).scheduleAtFixedRate(connectivityCollectorService, defaultInterval);
    verify(taskScheduler).scheduleWithFixedDelay(newsFeedCollectorService, defaultInterval);
  }

  @Test
  @DisplayName("Given a missing or non-positive interval, should resolve the default")
  void given_invalidInterval_should_resolveDefault() {
    // Arrange
    config.setConnectivityCheckInterval(null);
    config.setNewsFeedInterval(-1);

    // Act & Assert
    assertThat(config.getEffectiveConnectivityCheckInterval())
        .isEqualTo(XtmHubCollectorConfig.DEFAULT_INTERVAL);
    assertThat(config.getEffectiveNewsFeedInterval())
        .isEqualTo(XtmHubCollectorConfig.DEFAULT_INTERVAL);
  }
}
