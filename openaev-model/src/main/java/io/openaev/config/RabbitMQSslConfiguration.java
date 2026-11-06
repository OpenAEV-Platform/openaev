package io.openaev.config;

import com.rabbitmq.client.ConnectionFactory;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitMQSslConfiguration {

  /**
   * The platform trust manager built by {@link io.openaev.authorisation.TlsConfig}: the JVM default
   * trust store plus the certificates of {@code openaev.extra-trusted-certs-dir}.
   */
  private final X509TrustManager platformTrustManager;

  /**
   * Configures SSL settings for the ConnectionFactory.
   *
   * @param factory the ConnectionFactory to configure
   * @throws Exception if SSL configuration fails
   */
  public void configureSsl(ConnectionFactory factory, RabbitmqConfig rabbitmqConfig)
      throws Exception {
    boolean customTrustStore = hasCustomTrustStore(rabbitmqConfig);
    SSLContext sslContext = SSLContext.getInstance(customTrustStore ? "TLSv1.3" : "TLS");
    sslContext.init(null, trustManagers(rabbitmqConfig), null);
    factory.useSslProtocol(sslContext);
    log.debug("SSL configured for RabbitMQ connection");
  }

  /**
   * The trust managers the RabbitMQ connection verifies the broker with: the dedicated RabbitMQ
   * trust store when one is configured, otherwise the platform trust manager, so the extra trusted
   * certificates (custom or corporate CAs) apply to the broker as to every other TLS connection.
   *
   * @param rabbitmqConfig the RabbitMQ configuration
   * @return the trust managers to initialise the SSL context with
   * @throws Exception if the configured trust store cannot be read
   */
  public TrustManager[] trustManagers(RabbitmqConfig rabbitmqConfig) throws Exception {
    if (!hasCustomTrustStore(rabbitmqConfig)) {
      return new TrustManager[] {platformTrustManager};
    }
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    try (InputStream trustStoreStream = rabbitmqConfig.getTrustStore().getInputStream()) {
      String password = rabbitmqConfig.getTrustStorePassword();
      trustStore.load(trustStoreStream, password != null ? password.toCharArray() : null);
    }
    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);
    return trustManagerFactory.getTrustManagers();
  }

  private static boolean hasCustomTrustStore(RabbitmqConfig rabbitmqConfig) {
    return rabbitmqConfig.getTrustStore() != null && rabbitmqConfig.getTrustStore().exists();
  }
}
