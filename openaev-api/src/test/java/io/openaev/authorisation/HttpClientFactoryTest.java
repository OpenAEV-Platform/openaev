package io.openaev.authorisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HttpClientFactory")
class HttpClientFactoryTest {

  private static X509TrustManager jdkTrustManager() throws Exception {
    TrustManagerFactory trustManagers =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagers.init((KeyStore) null);
    return (X509TrustManager) trustManagers.getTrustManagers()[0];
  }

  @Test
  @org.junit.jupiter.api.Timeout(30)
  @DisplayName("a bounded client gives up on a host that never completes the TLS handshake")
  void boundedClientGivesUpOnAStalledHandshake() throws Exception {
    HttpClientFactory factory = new HttpClientFactory(jdkTrustManager());
    List<Socket> accepted = new CopyOnWriteArrayList<>();
    ExecutorService acceptor = Executors.newSingleThreadExecutor();
    // The address the request targets: a server on another loopback family would refuse the
    // connection at once and never reach a handshake.
    InetAddress loopback = InetAddress.getByName("127.0.0.1");
    try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
      // Accepts the connection and never answers the ClientHello.
      acceptor.submit(
          () -> {
            try {
              accepted.add(server.accept());
            } catch (IOException ignored) {
              // The server closes at the end of the test.
            }
          });
      Instant start = Instant.now();
      try (CloseableHttpClient client = factory.httpClientNoRetry(Timeout.ofMilliseconds(500))) {
        HttpGet get =
            new HttpGet("https://" + loopback.getHostAddress() + ":" + server.getLocalPort() + "/");
        assertThatThrownBy(() -> client.execute(get, response -> response.getCode()))
            .isInstanceOf(IOException.class);
      }
      Duration elapsed = Duration.between(start, Instant.now());
      // Connected, then waited out the bound on the handshake: not a refused connection.
      assertThat(accepted).hasSize(1);
      assertThat(elapsed)
          .isGreaterThanOrEqualTo(Duration.ofMillis(400))
          .isLessThan(Duration.ofSeconds(10));
    } finally {
      for (Socket socket : accepted) {
        socket.close();
      }
      acceptor.shutdownNow();
    }
  }
}
