package io.openaev.config.tls.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.RabbitMQSslConfiguration;
import io.openaev.config.RabbitmqConfig;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class RabbitMQSslConfigurationTest {

  private final X509TrustManager platformTrustManager = mock(X509TrustManager.class);
  private final RabbitMQSslConfiguration sslConfiguration =
      new RabbitMQSslConfiguration(platformTrustManager);

  @Test
  @DisplayName("Without a RabbitMQ trust store, the platform trust manager is used")
  void usesPlatformTrustManagerWithoutTrustStore() throws Exception {
    RabbitmqConfig config = new RabbitmqConfig();

    TrustManager[] trustManagers = sslConfiguration.trustManagers(config);

    assertThat(trustManagers).containsExactly(platformTrustManager);
  }

  @Test
  @DisplayName("A configured RabbitMQ trust store takes precedence")
  void usesConfiguredTrustStoreWhenPresent() throws Exception {
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
  @DisplayName("configureSsl hands the factory an SSL context instead of the trust-all default")
  void configureSslSetsAnSslContext() throws Exception {
    ConnectionFactory factory = mock(ConnectionFactory.class);

    sslConfiguration.configureSsl(factory, new RabbitmqConfig());

    verify(factory).useSslProtocol(any(SSLContext.class));
  }
}
