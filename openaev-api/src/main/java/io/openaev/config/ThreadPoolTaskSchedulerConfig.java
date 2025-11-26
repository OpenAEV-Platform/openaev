package io.openaev.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@Slf4j
public class ThreadPoolTaskSchedulerConfig {

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

    // Plus petit pool car beaucoup d'events
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("Stream-");

    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());

    executor.initialize();
    return executor;
  }
}
