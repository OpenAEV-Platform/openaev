package io.openaev.utils.helpers;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A local HTTP server standing in for a sovereign or mocked cloud, which is exactly what the
 * endpoint overrides of a credential are for.
 *
 * <p>Each path answers a canned response, and every request is recorded, so a test can prove that a
 * connectivity probe reached the overridden endpoint rather than the official one.
 */
public final class MockCloudServer implements AutoCloseable {

  private static final String LOOPBACK = "127.0.0.1";

  /** What the server received, reduced to what the tests assert on. */
  public record RecordedRequest(String method, String path, String authorization, String body) {}

  /** What the server answers. */
  public record CannedResponse(int status, String contentType, String body) {}

  private final HttpServer server;
  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

  private MockCloudServer(HttpServer server) {
    this.server = server;
  }

  /**
   * Starts a server on a free loopback port.
   *
   * @return the started server
   */
  public static MockCloudServer start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK, 0), 0);
    server.start();
    return new MockCloudServer(server);
  }

  /**
   * A URL on which nothing listens: a sovereign endpoint that does not answer.
   *
   * @param path the path to append
   * @return the URL of a closed loopback port
   */
  public static String unreachableUrl(String path) throws IOException {
    // The port is free once the socket closes: nothing listens on it any more.
    try (ServerSocket socket = new ServerSocket(0)) {
      return "http://" + LOOPBACK + ":" + socket.getLocalPort() + path;
    }
  }

  /**
   * Answers every request under {@code path} with the same response.
   *
   * @return this server, for chaining
   */
  public MockCloudServer respond(String path, int status, String contentType, String body) {
    return respond(path, request -> new CannedResponse(status, contentType, body));
  }

  /**
   * Answers every request under {@code path} with a response chosen from the request, for an API
   * that serves several actions on one path (AWS STS).
   *
   * @return this server, for chaining
   */
  public MockCloudServer respond(String path, Function<RecordedRequest, CannedResponse> answer) {
    server.createContext(
        path,
        exchange -> {
          RecordedRequest request =
              new RecordedRequest(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestHeaders().getFirst("Authorization"),
                  new String(exchange.getRequestBody().readAllBytes(), UTF_8));
          requests.add(request);
          CannedResponse response = answer.apply(request);
          byte[] body = response.body().getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", response.contentType());
          exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
          if (body.length > 0) {
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    return this;
  }

  /**
   * @param path the path to append
   * @return the URL of this server for that path
   */
  public String url(String path) {
    return "http://" + LOOPBACK + ":" + server.getAddress().getPort() + path;
  }

  /**
   * @return every request received so far, in arrival order
   */
  public List<RecordedRequest> requests() {
    return List.copyOf(requests);
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
