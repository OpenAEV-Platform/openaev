package io.openaev.config.tls.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.authorisation.TlsConfig;
import io.openaev.config.RabbitMQSslConfiguration;
import io.openaev.config.RabbitmqConfig;
import io.openaev.config.RabbitmqManagementClientConfig;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RabbitmqManagementClientConfigTest {

  private static final String LOCALHOST_CA_DIR = "src/test/resources/tls/localhost";
  private static final String UNRELATED_CA_DIR = "src/test/resources/tls/extra-certs/one";

  private ClientAndServer tlsServer;

  @BeforeAll
  void startTlsServer() {
    tlsServer =
        new ClientAndServer(
            Configuration.configuration()
                .certificateAuthorityCertificate(LOCALHOST_CA_DIR + "/ca.pem")
                .x509CertificatePath(LOCALHOST_CA_DIR + "/localhost.crt")
                .privateKeyPath(LOCALHOST_CA_DIR + "/localhost.key")
                .rebuildTLSContext(true),
            PortFactory.findFreePort());
  }

  @AfterAll
  void stopTlsServer() {
    if (tlsServer != null) {
      tlsServer.stop();
    }
  }

  private static RabbitMQSslConfiguration sslConfiguration(String extraTrustedCertsDir)
      throws Exception {
    TlsConfig tlsConfig = new TlsConfig();
    ReflectionTestUtils.setField(tlsConfig, "extraTrustedCertsDir", extraTrustedCertsDir);
    X509TrustManager platformTrustManager = tlsConfig.tlsContextCustom();
    return new RabbitMQSslConfiguration(platformTrustManager);
  }

  private static RabbitmqConfig sslRabbitmqConfig() {
    RabbitmqConfig rabbitmqConfig = new RabbitmqConfig();
    rabbitmqConfig.setSsl(true);
    return rabbitmqConfig;
  }

  private static HttpClient managementHttpClient(
      RabbitmqConfig rabbitmqConfig, RabbitMQSslConfiguration sslConfiguration) {
    return new RabbitmqManagementClientConfig()
        .rabbitmqManagementHttpClient(rabbitmqConfig, sslConfiguration);
  }

  private HttpResponse<String> callManagementApi(HttpClient httpClient) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("https://localhost:" + tlsServer.getLocalPort() + "/api/queues"))
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("Management API with a CA of extra-trusted-certs-dir is reached over TLS")
  void reachesManagementApiSignedByExtraTrustedCertificate() throws Exception {
    HttpClient httpClient =
        managementHttpClient(sslRabbitmqConfig(), sslConfiguration(LOCALHOST_CA_DIR));

    HttpResponse<String> response = callManagementApi(httpClient);

    assertThat(response.statusCode()).isPositive();
  }

  @Test
  @DisplayName("Management API signed by a CA the platform does not trust is refused")
  void refusesManagementApiWithUntrustedCertificate() throws Exception {
    HttpClient httpClient =
        managementHttpClient(sslRabbitmqConfig(), sslConfiguration(UNRELATED_CA_DIR));

    assertThatThrownBy(() -> callManagementApi(httpClient)).isInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("With a RabbitMQ trust store, the platform trust store still applies")
  void keepsPlatformTrustWithDedicatedTrustStore() throws Exception {
    KeyStore unrelatedStore = KeyStore.getInstance(KeyStore.getDefaultType());
    unrelatedStore.load(null, null);
    try (InputStream cert = new FileInputStream(UNRELATED_CA_DIR + "/cert.pem")) {
      unrelatedStore.setCertificateEntry(
          "unrelated", CertificateFactory.getInstance("X.509").generateCertificate(cert));
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    unrelatedStore.store(bytes, "changeit".toCharArray());
    RabbitmqConfig rabbitmqConfig = sslRabbitmqConfig();
    rabbitmqConfig.setTrustStore(new ByteArrayResource(bytes.toByteArray()));
    rabbitmqConfig.setTrustStorePassword("changeit");
    HttpClient httpClient =
        managementHttpClient(rabbitmqConfig, sslConfiguration(LOCALHOST_CA_DIR));

    HttpResponse<String> response = callManagementApi(httpClient);

    assertThat(response.statusCode()).isPositive();
  }

  @Test
  @DisplayName("Without SSL, the management API client keeps the JVM default SSL context")
  void keepsDefaultSslContextWithoutSsl() throws Exception {
    HttpClient httpClient =
        managementHttpClient(new RabbitmqConfig(), sslConfiguration(UNRELATED_CA_DIR));

    assertThat(httpClient.sslContext()).isSameAs(SSLContext.getDefault());
  }
}
