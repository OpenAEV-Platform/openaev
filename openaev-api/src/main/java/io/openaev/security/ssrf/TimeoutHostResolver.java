package io.openaev.security.ssrf;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounds how long a host lookup may block its caller.
 *
 * <p>{@link InetAddress#getAllByName(String)} has no timeout of its own: a slow or unresponsive DNS
 * server holds the calling thread for as long as the operating system resolver takes to give up,
 * which can be many seconds. Callers that cannot afford that (the report render's egress guard runs
 * on the browser driver's single dispatch thread, so a stuck lookup stalls every request of the
 * render) wrap their resolver in this one: past the timeout the lookup is reported as {@link
 * UnknownHostException}, like any other host that cannot be resolved.
 *
 * <p>The lookup itself is not interruptible, so a lookup that times out keeps running in the
 * background until the operating system returns. To keep a hostile name from piling up blocked
 * threads, the number of lookups running at once is capped and there is no queue: when every slot
 * is taken the lookup is refused immediately.
 *
 * <p>Nothing is cached: every call is a lookup.
 */
public final class TimeoutHostResolver implements HostResolver {

  private static final AtomicInteger POOL_ID = new AtomicInteger();

  private final HostResolver delegate;
  private final Duration timeout;
  private final ThreadPoolExecutor lookups;

  /**
   * @param delegate the resolver whose lookups are bounded
   * @param timeout how long a caller waits for a lookup
   * @param maxConcurrentLookups lookups allowed to run at once, timed-out ones included
   */
  public TimeoutHostResolver(
      final HostResolver delegate, final Duration timeout, final int maxConcurrentLookups) {
    this.delegate = delegate;
    this.timeout = timeout;
    int poolId = POOL_ID.incrementAndGet();
    AtomicInteger threadId = new AtomicInteger();
    ThreadFactory threadFactory =
        task -> {
          Thread thread =
              new Thread(task, "host-lookup-" + poolId + "-" + threadId.incrementAndGet());
          // Never keeps the JVM alive: a lookup stuck on a dead DNS server must not block shutdown.
          thread.setDaemon(true);
          return thread;
        };
    this.lookups =
        new ThreadPoolExecutor(
            maxConcurrentLookups,
            maxConcurrentLookups,
            10,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy());
    // Idle lookup threads go away on their own: no pool to close.
    this.lookups.allowCoreThreadTimeOut(true);
  }

  @Override
  public InetAddress[] resolve(final String host) throws UnknownHostException {
    Future<InetAddress[]> lookup;
    try {
      lookup = this.lookups.submit(() -> this.delegate.resolve(host));
    } catch (RejectedExecutionException e) {
      throw new UnknownHostException(
          "Too many host lookups are already waiting on DNS, refusing to look up " + host);
    }
    try {
      return lookup.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      lookup.cancel(true);
      throw new UnknownHostException(
          "Lookup of " + host + " timed out after " + this.timeout.toMillis() + " ms");
    } catch (InterruptedException e) {
      lookup.cancel(true);
      Thread.currentThread().interrupt();
      throw new UnknownHostException("Lookup of " + host + " was interrupted");
    } catch (ExecutionException e) {
      if (e.getCause() instanceof UnknownHostException unknownHost) {
        throw unknownHost;
      }
      throw new UnknownHostException("Lookup of " + host + " failed: " + e.getCause());
    }
  }
}
