package io.openaev.service.stix;

import io.openaev.service.stix.IocValidationPlanner.HostResolver;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The DNS answers for the URL host names of one IOC validation request, gathered before the
 * transaction of its intake or approval opens, so that no database connection or lock waits for a
 * DNS server.
 *
 * <p>Each host is resolved once, in parallel, within {@link #DEADLINE} overall. A name not answered
 * in time counts as a name that does not resolve from the OpenAEV server (the egress proxy resolves
 * it again at execution anyway), and an answer arriving later is ignored. The lookups run on one
 * pool of {@link #THREADS} threads shared by every request: a lookup cannot be interrupted, so the
 * pool is what bounds the threads a slow or silent DNS server can hold.
 */
public final class IocValidationHostAnswers {

  static final Duration DEADLINE = Duration.ofSeconds(5);
  static final int THREADS = 16;

  private static final ExecutorService POOL = pool();

  private final Set<String> hosts;
  private final Map<String, List<InetAddress>> answers;
  private final HostResolver fallback;

  private IocValidationHostAnswers(
      Set<String> hosts, Map<String, List<InetAddress>> answers, HostResolver fallback) {
    this.hosts = hosts;
    this.answers = answers;
    this.fallback = fallback;
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
              // a failed lookup counts as a name that does not resolve
            }
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return new IocValidationHostAnswers(Set.copyOf(names), Map.copyOf(answers), resolver);
  }

  /**
   * The resolver the plans are built with: the gathered answers, or a direct lookup for a host that
   * was not announced.
   */
  HostResolver resolver() {
    return host ->
        hosts.contains(host) ? answers.getOrDefault(host, List.of()) : fallback.resolve(host);
  }

  private static ExecutorService pool() {
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            THREADS,
            THREADS,
            30,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            Thread.ofPlatform().daemon().name("ioc-validation-dns-", 0).factory());
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }
}
