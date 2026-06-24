package io.openaev.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@Slf4j
public class ThreadPoolTaskSchedulerConfig {

  @Value("${spring.datasource.hikari.maximum-pool-size:20}")
  private int dbPoolSize;

  // optional manual override per environment
  @Value("${manager.integrations.concurrency:#{null}}")
  private Integer concurrencyOverride;

  @Bean
  public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
    ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    threadPoolTaskScheduler.setPoolSize(20);
    threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
    threadPoolTaskScheduler.setErrorHandler(
        t -> log.error("Error during scheduled task : {}", t.getMessage(), t));
    return threadPoolTaskScheduler;
  }

  /** Dedicated executor for stream events */
  @Bean(name = "streamExecutor")
  public Executor streamExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("Stream-");

    // If we have more event to deal with than the available size in the waiting queue, we discard
    // the oldest to prevent overloading the stream. This also helps a little preventing
    // overloading the tab of a user connected when having a lot of events
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());

    executor.initialize();
    return executor;
  }

  /** Dedicated executor for manager integrations tenant sync dispatches. */
  @Bean(name = "managerIntegrationsExecutor")
  public Executor managerIntegrationsExecutor() {
    // Bottleneck is the shared DB connection pool, not CPU.
    // Default to ~40% of the pool so the rest of the app keeps connections available.
    int concurrency =
        concurrencyOverride != null ? concurrencyOverride : Math.max(1, dbPoolSize * 2 / 5);
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(concurrency);
    executor.setMaxPoolSize(concurrency);
    executor.setQueueCapacity(0);
    executor.setKeepAliveSeconds(30);
    executor.setAllowCoreThreadTimeOut(true);
    executor.setThreadNamePrefix("ManagerIntegrations-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
