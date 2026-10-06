package io.openaev.service.stix;

import io.openaev.service.stix.IocValidationPlanner.HostResolver;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The DNS answers for the URL host names of one IOC validation request, gathered before the
 * transaction of its intake or approval opens, so that no database connection or lock waits for a
 * DNS server.
 *
 * <p>Each host is resolved once, in parallel, within {@link #DEADLINE} overall. A name not answered
 * in time, or whose lookup failed, is not {@link HostResolver#answered answered}: the tests that
 * depend on its addresses do not run, unlike a name that does not exist, which the egress proxy
 * resolves again at execution. An answer arriving later is ignored, and a name that was not
 * announced is never looked up afterwards: it is not answered either. The lookups run on one pool
 * of {@link #THREADS} threads shared by every request: a lookup cannot be interrupted, so the pool
 * is what bounds the threads a slow or silent DNS server can hold, and its queue is bounded and
 * purged of the lookups cancelled at each deadline.
 */
public final class IocValidationHostAnswers {

  static final Duration DEADLINE = Duration.ofSeconds(5);
  static final int THREADS = 16;
  private static final int QUEUE_CAPACITY = 1_000;

  private static final ThreadPoolExecutor POOL = pool();

  private final Map<String, List<InetAddress>> answers;

  private IocValidationHostAnswers(Map<String, List<InetAddress>> answers) {
    this.answers = answers;
  }

  /** Resolves the host names with the resolver of the OpenAEV server. */
  public static IocValidationHostAnswers resolve(Collection<String> hostNames) {
    return resolve(hostNames, HostResolver.SYSTEM, DEADLINE);
  }

  static IocValidationHostAnswers resolve(
      Collection<String> hostNames, HostResolver resolver, Duration deadline) {
    List<String> names = List.copyOf(new LinkedHashSet<>(hostNames));
    Map<String, List<InetAddress>> answers = new HashMap<>();
    if (!names.isEmpty()) {
      try {
        List<Future<List<InetAddress>>> lookups =
            POOL.invokeAll(
                names.stream()
                    .map(host -> (Callable<List<InetAddress>>) () -> resolver.resolve(host))
                    .toList(),
                deadline.toMillis(),
                TimeUnit.MILLISECONDS);
        for (int index = 0; index < names.size(); index++) {
          Future<List<InetAddress>> lookup = lookups.get(index);
          if (!lookup.isCancelled()) {
            try {
              answers.put(names.get(index), List.copyOf(lookup.get()));
            } catch (ExecutionException e) {
              // a failed lookup is not an answer
            }
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (RejectedExecutionException e) {
        // The shared queue is full (DNS server silent for a while): no answer, as after the
        // deadline
      } finally {
        // A cancelled lookup would otherwise stay queued until a resolver thread is free again
        POOL.purge();
      }
    }
    return new IocValidationHostAnswers(Map.copyOf(answers));
  }

  /**
   * The resolver the plans are built with, which never looks a name up: it reads the gathered
   * answers, so it can be used inside a transaction. A host whose lookup timed out, failed or was
   * not queued, and a host that was not announced (the settings changed since the answers were
   * gathered), is not {@link HostResolver#answered answered}.
   */
  HostResolver resolver() {
    return new HostResolver() {
      @Override
      public List<InetAddress> resolve(String host) {
        return answers.getOrDefault(host, List.of());
      }

      @Override
      public boolean answered(String host) {
        return answers.containsKey(host);
      }
    };
  }

  /** The lookups waiting for a resolver thread. */
  static int queuedLookups() {
    return POOL.getQueue().size();
  }

  private static ThreadPoolExecutor pool() {
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            THREADS,
            THREADS,
            30,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            Thread.ofPlatform().daemon().name("ioc-validation-dns-", 0).factory());
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }
}
