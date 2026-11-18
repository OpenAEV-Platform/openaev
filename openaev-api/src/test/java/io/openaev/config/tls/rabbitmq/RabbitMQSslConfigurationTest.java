package io.openaev.config.tls.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.rabbitmq.client.ConnectionFactory;
import io.openaev.authorisation.TlsConfig;
import io.openaev.config.RabbitMQSslConfiguration;
import io.openaev.config.RabbitmqConfig;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
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
class RabbitMQSslConfigurationTest {

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

  private static X509TrustManager platformTrustManager(String extraTrustedCertsDir)
      throws Exception {
    TlsConfig tlsConfig = new TlsConfig();
    ReflectionTestUtils.setField(tlsConfig, "extraTrustedCertsDir", extraTrustedCertsDir);
    return tlsConfig.tlsContextCustom();
  }

  private void handshake(ConnectionFactory factory) throws Exception {
    int port = tlsServer.getLocalPort();
    try (Socket socket = factory.getSocketFactory().createSocket("localhost", port)) {
      ((SSLSocket) socket).startHandshake();
    }
  }

  @Test
  @DisplayName("Without a RabbitMQ trust store, the platform trust manager is used")
  void usesPlatformTrustManagerWithoutTrustStore() throws Exception {
    X509TrustManager platformTrustManager = mock(X509TrustManager.class);
    RabbitMQSslConfiguration sslConfiguration = new RabbitMQSslConfiguration(platformTrustManager);

    TrustManager[] trustManagers = sslConfiguration.trustManagers(new RabbitmqConfig());

    assertThat(trustManagers).containsExactly(platformTrustManager);
  }

  @Test
  @DisplayName("A configured RabbitMQ trust store takes precedence")
  void usesConfiguredTrustStoreWhenPresent() throws Exception {
    X509TrustManager platformTrustManager = mock(X509TrustManager.class);
    RabbitMQSslConfiguration sslConfiguration = new RabbitMQSslConfiguration(platformTrustManager);
    KeyStore emptyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    emptyStore.load(null, null);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    emptyStore.store(bytes, "changeit".toCharArray());
    RabbitmqConfig config = new RabbitmqConfig();
    config.setTrustStore(new ByteArrayResource(bytes.toByteArray()));
    config.setTrustStorePassword("changeit");

    TrustManager[] trustManagers = sslConfiguration.trustManagers(config);

    assertThat(trustManagers).isNotEmpty().doesNotContain(platformTrustManager);
    assertThat(((X509TrustManager) trustManagers[0]).getAcceptedIssuers())
        .isEqualTo(new X509Certificate[0]);
  }

  @Test
  @DisplayName("A broker signed by a CA the platform does not trust is refused")
  void refusesBrokerWithUntrustedCertificate() throws Exception {
    RabbitMQSslConfiguration sslConfiguration =
        new RabbitMQSslConfiguration(platformTrustManager(UNRELATED_CA_DIR));
    ConnectionFactory factory = new ConnectionFactory();

    sslConfiguration.configureSsl(factory, new RabbitmqConfig());

    assertThatThrownBy(() -> handshake(factory)).isInstanceOf(SSLHandshakeException.class);
  }

  @Test
  @DisplayName("A broker signed by a CA of extra-trusted-certs-dir is accepted")
  void acceptsBrokerSignedByExtraTrustedCertificate() throws Exception {
    RabbitMQSslConfiguration sslConfiguration =
        new RabbitMQSslConfiguration(platformTrustManager(LOCALHOST_CA_DIR));
    ConnectionFactory factory = new ConnectionFactory();

    sslConfiguration.configureSsl(factory, new RabbitmqConfig());

    handshake(factory);
  }

  @Test
  @DisplayName("Without javax.net.ssl.keyStore, no client certificate is configured")
  void noKeyManagersWithoutKeyStore() throws Exception {
    assertThat(RabbitMQSslConfiguration.keyManagers(null, "PKCS12", null)).isNull();
    assertThat(RabbitMQSslConfiguration.keyManagers(" ", "PKCS12", null)).isNull();
  }

  @Test
  @DisplayName("With a client key store, its key managers are kept for the broker")
  void keepsKeyManagersOfClientKeyStore() throws Exception {
    KeyManager[] keyManagers = RabbitMQSslConfiguration.keyManagers("NONE", "PKCS12", "changeit");

    assertThat(keyManagers).isNotEmpty();
    assertThat(keyManagers[0]).isInstanceOf(X509KeyManager.class);
  }
}
