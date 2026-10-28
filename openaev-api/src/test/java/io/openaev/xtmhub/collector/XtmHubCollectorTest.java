package io.openaev.xtmhub.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.openaev.xtmhub.XtmHubService;
import java.time.Duration;
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

  private XtmHubCollectorConfig config;
  private XtmHubCollector collector;

  @BeforeEach
  void setUp() {
    config = new XtmHubCollectorConfig();
    config.setConnectivityCheckInterval(7200);
    config.setNewsFeedInterval(1800);
    collector = new XtmHubCollector(config, taskScheduler, xtmHubService);
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
    ArgumentCaptor<Runnable> connectivityTask = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Runnable> newsFeedTask = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler)
        .scheduleAtFixedRate(connectivityTask.capture(), eq(Duration.ofSeconds(7200)));
    verify(taskScheduler)
        .scheduleWithFixedDelay(newsFeedTask.capture(), eq(Duration.ofSeconds(1800)));

    connectivityTask.getValue().run();
    verify(xtmHubService, times(1)).refreshConnectivityAllTenants();
    verify(xtmHubService, never()).syncNewsFeed();
    newsFeedTask.getValue().run();
    verify(xtmHubService, times(1)).syncNewsFeed();
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
    verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(defaultInterval));
    verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(defaultInterval));
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
