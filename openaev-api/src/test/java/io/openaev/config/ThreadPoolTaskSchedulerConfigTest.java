package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ThreadPoolTaskSchedulerConfig executors")
class ThreadPoolTaskSchedulerConfigTest {

  @Test
  @DisplayName("injectExecutionExecutor is sized by the inject.execution.concurrency override")
  void injectExecutionExecutorUsesConfiguredConcurrency() {
    ThreadPoolTaskSchedulerConfig config = new ThreadPoolTaskSchedulerConfig();
    ReflectionTestUtils.setField(config, "injectExecutionConcurrencyOverride", 7);

    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.injectExecutionExecutor();

    assertThat(executor.getCorePoolSize()).isEqualTo(7);
    assertThat(executor.getMaxPoolSize()).isEqualTo(7);
    executor.shutdown();
  }

  @Test
  @DisplayName("injectExecutionExecutor defaults to the available CPU cores when unset")
  void injectExecutionExecutorDefaultsToAvailableProcessors() {
    ThreadPoolTaskSchedulerConfig config = new ThreadPoolTaskSchedulerConfig();

    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.injectExecutionExecutor();

    int expected = Math.max(1, Runtime.getRuntime().availableProcessors());
    assertThat(executor.getCorePoolSize()).isEqualTo(expected);
    assertThat(executor.getMaxPoolSize()).isEqualTo(expected);
    executor.shutdown();
  }

  @Test
  @DisplayName("injectExecutionExecutor never goes below one thread, whatever the override")
  void injectExecutionExecutorClampsNonPositiveOverride() {
    ThreadPoolTaskSchedulerConfig config = new ThreadPoolTaskSchedulerConfig();
    ReflectionTestUtils.setField(config, "injectExecutionConcurrencyOverride", 0);

    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.injectExecutionExecutor();

    assertThat(executor.getCorePoolSize()).isEqualTo(1);
    assertThat(executor.getMaxPoolSize()).isEqualTo(1);
    executor.shutdown();
  }
}
