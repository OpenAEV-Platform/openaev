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

  // Optional per-environment override for the managerIntegrationsExecutor thread-pool size.
  // When unset (the default), concurrency is auto-tuned to ~40% of the Hikari pool so that
  // tenant-sync threads do not starve the rest of the application.
  // Set manager.integrations.concurrency (or env var MANAGER_INTEGRATIONS_CONCURRENCY) to a
  // positive integer to pin the pool size explicitly — useful when the auto-computed value is
  // either too conservative or causes contention on a specific deployment.
  // The boxed Integer (rather than primitive int) is intentional: it allows the SpEL default
  // #{null} to work, avoiding a startup failure when the property is absent.
  @Value("${manager.integrations.concurrency:#{null}}")
  private Integer concurrencyOverride;

  // Optional per-environment override for the injectExecutionExecutor thread-pool size.
  // When unset (the default), concurrency matches the number of available CPU cores, which is
  // the effective parallelism the previous ForkJoinPool.commonPool-based fan-out provided.
  // Set inject.execution.concurrency (or env var INJECT_EXECUTION_CONCURRENCY) to a positive
  // integer to pin the pool size explicitly.
  // The boxed Integer (rather than primitive int) is intentional: it allows the SpEL default
  // #{null} to work, avoiding a startup failure when the property is absent.
  @Value("${inject.execution.concurrency:#{null}}")
  private Integer injectExecutionConcurrencyOverride;

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

  /** Dedicated executor for the notifications engine (live trigger matching + dispatch). */
  @Bean(name = "notificationEngineExecutor")
  public Executor notificationEngineExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("NotificationEngine-");
    // Under event storms (bulk imports), drop the oldest notification evaluations rather than
    // blocking writers or exhausting memory.
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
        concurrencyOverride != null
            ? Math.max(1, concurrencyOverride)
            : Math.max(1, dbPoolSize * 2 / 5);
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

  /**
   * Dedicated executor for the scheduled inject execution fan-out (issue #236). Replaces the nested
   * {@code parallelStream()} in {@code InjectsExecutionJob}, which ran inject dispatch on the
   * shared {@code ForkJoinPool.commonPool} and therefore competed with every other commonPool user
   * and could not be sized per deployment.
   */
  @Bean(name = "injectExecutionExecutor")
  public Executor injectExecutionExecutor() {
    // Default to the CPU core count: the parallelism the commonPool-based fan-out effectively
    // had (commonPool workers plus the calling scheduler thread).
    int concurrency =
        injectExecutionConcurrencyOverride != null
            ? Math.max(1, injectExecutionConcurrencyOverride)
            : Math.max(1, Runtime.getRuntime().availableProcessors());
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(concurrency);
    executor.setMaxPoolSize(concurrency);
    executor.setQueueCapacity(0);
    executor.setKeepAliveSeconds(30);
    executor.setAllowCoreThreadTimeOut(true);
    executor.setThreadNamePrefix("InjectExecution-");
    // When the pool is saturated the scheduler thread runs the inject itself: injects are never
    // dropped, and the dispatch loop is naturally throttled instead of queueing without bound.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
