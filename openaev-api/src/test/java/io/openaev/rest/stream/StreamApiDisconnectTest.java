package io.openaev.rest.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.service.StreamService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Checks that a stream consumer is dropped from {@link StreamService} once its client goes away.
 * The stream never completes on its own, so a client leaving is seen by Reactor as a cancel, not a
 * termination.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Stream consumer cleanup")
class StreamApiDisconnectTest extends IntegrationTest {

  private static final String STREAM_URI = "/api/stream";
  private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(10);

  @Autowired private StreamService streamService;

  @LocalServerPort private int port;

  @Value("${openaev.admin.token}")
  private String adminToken;

  @SuppressWarnings("unchecked")
  private Map<String, Object> consumers() {
    return (Map<String, Object>) ReflectionTestUtils.getField(streamService, "consumers");
  }

  private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(CLEANUP_TIMEOUT);
    while (Instant.now().isBefore(deadline)) {
      if (condition.getAsBoolean()) {
        return true;
      }
      Thread.sleep(200);
    }
    return condition.getAsBoolean();
  }

  @Nested
  @DisplayName("Reactor subscription")
  class ReactorSubscription {

    @Test
    @DisplayName("Cancelling the merged stream removes its consumer")
    void given_cancelledSubscription_should_removeConsumer() {
      // Arrange
      String sessionId = "cancel-" + System.nanoTime();
      Disposable subscription =
          Flux.merge(streamService.makeDataFlux(sessionId), streamService.makePingFlux())
              .subscribe();
      assertThat(consumers()).containsKey(sessionId);

      // Act
      subscription.dispose();

      // Assert
      assertThat(consumers()).doesNotContainKey(sessionId);
    }

    @Test
    @DisplayName("Completing the data stream removes its consumer")
    @SuppressWarnings("unchecked")
    void given_completedDataStream_should_removeConsumer() {
      // Arrange
      String sessionId = "complete-" + System.nanoTime();
      streamService.makeDataFlux(sessionId).subscribe();
      FluxSink<Object> sink =
          (FluxSink<Object>)
              ReflectionTestUtils.invokeMethod(consumers().get(sessionId), "fluxSink");

      // Act
      sink.complete();

      // Assert
      assertThat(consumers()).doesNotContainKey(sessionId);
    }

    @Test
    @DisplayName("Closing an old stream keeps the newer consumer of the same session")
    void given_reconnectOnSameSession_should_keepNewerConsumer() {
      // Arrange
      String sessionId = "reconnect-" + System.nanoTime();
      Disposable oldStream = streamService.makeDataFlux(sessionId).subscribe();
      Object oldConsumer = consumers().get(sessionId);
      Disposable newStream = streamService.makeDataFlux(sessionId).subscribe();
      Object newConsumer = consumers().get(sessionId);
      assertThat(newConsumer).isNotSameAs(oldConsumer);

      // Act
      oldStream.dispose();

      // Assert
      assertThat(consumers().get(sessionId)).isSameAs(newConsumer);
      newStream.dispose();
      assertThat(consumers()).doesNotContainKey(sessionId);
    }
  }

  @Nested
  @DisplayName("HTTP client")
  class HttpClient {

    @Test
    @DisplayName("Closing the connection removes the consumer")
    void given_clientDisconnect_should_removeConsumer() throws Exception {
      // Arrange
      int before = consumers().size();
      try (Socket socket = new Socket("localhost", port)) {
        // Bounds the reads below if the server keeps the connection open without sending.
        socket.setSoTimeout((int) CLEANUP_TIMEOUT.toMillis());
        OutputStream out = socket.getOutputStream();
        out.write(
            ("GET "
                    + STREAM_URI
                    + " HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Accept: text/event-stream\r\n"
                    + "Authorization: Bearer "
                    + adminToken
                    + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.flush();
        BufferedReader in =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        assertThat(in.readLine()).isEqualTo("HTTP/1.1 200 ");
        String line;
        while ((line = in.readLine()) != null && !line.startsWith("event:ping")) {
          // skip headers and events until the first ping
        }
        assertThat(line).isEqualTo("event:ping");
        assertThat(consumers()).hasSize(before + 1);

        // Act
      }

      // Assert
      assertThat(waitFor(() -> consumers().size() == before))
          .as("consumer still registered %s after the client disconnected", CLEANUP_TIMEOUT)
          .isTrue();
    }
  }
}
