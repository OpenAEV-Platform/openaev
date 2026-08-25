package io.openaev.scheduler;

import java.util.Properties;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.stereotype.Component;

/**
 * Creates Quartz scheduler factories with runtime-specified thread pool settings.
 *
 * <p>Note: the related thread pools will add their number of threads <i>on top of</i> the core
 * Quartz thread pool configured in Spring settings. For example: Spring sets 10 concurrent threads
 * for the core thread pool, and we request a new scheduler factory with a max thread number of 5:
 * there will be a total of 15 concurrent threads split across two different thread pools.
 */
@Component
public class CustomSchedulerFactoryFactory {

  /**
   * Create a new scheduler factory with a dedicated instance name, a discrete number of concurrent
   * threads and a discrete misfire threshold.
   *
   * <p>The instance name must be unique per scheduler: Quartz registers schedulers in a JVM-wide
   * repository keyed by instance name, and a second factory reusing an existing name would silently
   * return the first scheduler (ignoring the requested thread pool settings).
   *
   * @param instanceName unique Quartz scheduler instance name
   * @param maxThreads max number of concurrent threads in the scheduler's own thread pool
   * @param misfireThresholdMs threshold in milliseconds after which a delayed trigger is declared
   *     misfired and handled per its misfire instruction
   * @return scheduler factory configured with the given instance name, thread count and misfire
   *     threshold.
   */
  public SchedulerFactory get(String instanceName, int maxThreads, long misfireThresholdMs) {
    StdSchedulerFactory schedulerFactory = new StdSchedulerFactory();
    Properties properties = new Properties();
    properties.setProperty("org.quartz.scheduler.instanceName", instanceName);
    properties.setProperty("org.quartz.scheduler.makeSchedulerThreadDaemon", "true");
    properties.setProperty("org.quartz.threadPool.makeThreadsDaemons", "true");
    properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
    properties.setProperty("org.quartz.threadPool.threadCount", String.valueOf(maxThreads));
    properties.setProperty(
        "org.quartz.jobStore.misfireThreshold", String.valueOf(misfireThresholdMs));
    schedulerFactory.initialize(properties);
    return schedulerFactory;
  }
}
