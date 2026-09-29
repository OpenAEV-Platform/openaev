package io.openaev.authorisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.X509TrustManager;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HttpClientFactory")
class HttpClientFactoryTest {

  private static final X509TrustManager ANY_CERTIFICATE =
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };

  @Test
  @org.junit.jupiter.api.Timeout(30)
  @DisplayName("a bounded client gives up on a host that never completes the TLS handshake")
  void boundedClientGivesUpOnAStalledHandshake() throws Exception {
    HttpClientFactory factory = new HttpClientFactory(ANY_CERTIFICATE);
    List<Socket> accepted = new CopyOnWriteArrayList<>();
    ExecutorService acceptor = Executors.newSingleThreadExecutor();
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
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
        HttpGet get = new HttpGet("https://127.0.0.1:" + server.getLocalPort() + "/");
        assertThatThrownBy(() -> client.execute(get, response -> response.getCode()))
            .isInstanceOf(IOException.class);
      }
      assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(10));
    } finally {
      for (Socket socket : accepted) {
        socket.close();
      }
      acceptor.shutdownNow();
    }
  }
}
