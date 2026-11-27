package io.openaev.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake search engine that holds every request, to check the client bounds each wait. A {@link
 * #silent()} server never answers. A {@link #busyFor(long)} server streams each response one byte
 * at a time for the given duration, so the request keeps its pooled connection busy without hitting
 * the socket timeout, then ends it.
 */
public final class SlowEngineServer implements AutoCloseable {

  private static final long TRICKLE_INTERVAL_MS = 200;
  private static final long FIRST_REQUEST_WAIT_SECONDS = 10;

  private final long busyMs;
  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final AtomicInteger requestCount = new AtomicInteger();
  private final CountDownLatch firstRequest = new CountDownLatch(1);

  public static SlowEngineServer silent() throws IOException {
    return new SlowEngineServer(-1);
  }

  public static SlowEngineServer busyFor(long busyMs) throws IOException {
    return new SlowEngineServer(busyMs);
  }

  private SlowEngineServer(long busyMs) throws IOException {
    this.busyMs = busyMs;
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", this::hold);
    // One thread per request: a held request must not block the next connection.
    server.setExecutor(executor);
    server.start();
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Number of requests that reached the server, so that got a pooled connection. */
  public int requestCount() {
    return requestCount.get();
  }

  public void awaitFirstRequest() throws InterruptedException {
    if (!firstRequest.await(FIRST_REQUEST_WAIT_SECONDS, TimeUnit.SECONDS)) {
      throw new IllegalStateException("No request reached the slow engine server");
    }
  }

  private void hold(HttpExchange exchange) throws IOException {
    requestCount.incrementAndGet();
    firstRequest.countDown();
    try {
      if (busyMs < 0) {
        new CountDownLatch(1).await();
      }
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, 0);
      OutputStream body = exchange.getResponseBody();
      for (long sent = 0; sent < busyMs; sent += TRICKLE_INTERVAL_MS) {
        body.write(' ');
        body.flush();
        Thread.sleep(TRICKLE_INTERVAL_MS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      // The client gave up and closed the connection.
    } finally {
      exchange.close();
    }
  }

  @Override
  public void close() {
    executor.shutdownNow();
    server.stop(0);
  }
}
