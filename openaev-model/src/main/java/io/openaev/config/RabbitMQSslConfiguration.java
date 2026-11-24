package io.openaev.config;

import com.rabbitmq.client.ConnectionFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
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
    factory.useSslProtocol(sslContext(rabbitmqConfig));
    log.debug("SSL configured for RabbitMQ connection");
  }

  /** SSL context of the AMQP connection: the RabbitMQ trust store, else the platform one. */
  public SSLContext sslContext(RabbitmqConfig rabbitmqConfig) throws Exception {
    boolean customTrustStore = hasCustomTrustStore(rabbitmqConfig);
    SSLContext sslContext = SSLContext.getInstance(customTrustStore ? "TLSv1.3" : "TLS");
    sslContext.init(defaultKeyManagers(), trustManagers(rabbitmqConfig), null);
    return sslContext;
  }

  /** SSL context of the management API calls: the RabbitMQ trust store or the platform one. */
  public SSLContext managementSslContext(RabbitmqConfig rabbitmqConfig) throws Exception {
    if (!hasCustomTrustStore(rabbitmqConfig)) {
      return sslContext(rabbitmqConfig);
    }
    X509TrustManager dedicated =
        Arrays.stream(trustManagers(rabbitmqConfig))
            .filter(X509TrustManager.class::isInstance)
            .map(X509TrustManager.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No X509 trust manager in trust store"));
    SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
    TrustManager[] trustManagers = {eitherOf(dedicated, platformTrustManager)};
    sslContext.init(defaultKeyManagers(), trustManagers, null);
    return sslContext;
  }

  /** A trust manager that accepts what the first or, failing that, the second one accepts. */
  private static X509TrustManager eitherOf(X509TrustManager first, X509TrustManager second) {
    return new X509TrustManager() {
      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType)
          throws CertificateException {
        try {
          first.checkServerTrusted(chain, authType);
        } catch (CertificateException e) {
          second.checkServerTrusted(chain, authType);
        }
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType)
          throws CertificateException {
        try {
          first.checkClientTrusted(chain, authType);
        } catch (CertificateException e) {
          second.checkClientTrusted(chain, authType);
        }
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        X509Certificate[] a = first.getAcceptedIssuers();
        X509Certificate[] b = second.getAcceptedIssuers();
        X509Certificate[] all = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
      }
    };
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

  /**
   * The key managers of the JVM default SSL context, read from the standard {@code
   * javax.net.ssl.keyStore} system properties, so the client certificate a broker may require is
   * still presented, as it was with the JVM default context.
   */
  private static KeyManager[] defaultKeyManagers() throws Exception {
    return keyManagers(
        System.getProperty("javax.net.ssl.keyStore"),
        System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType()),
        System.getProperty("javax.net.ssl.keyStorePassword"));
  }

  /**
   * The key managers of a client key store.
   *
   * @param keyStorePath the key store path, {@code NONE} for a key store with no file
   * @param keyStoreType the key store type
   * @param keyStorePassword the key store password, may be null
   * @return the key managers, or null when no key store is set (no client certificate)
   * @throws Exception if the key store cannot be read
   */
  public static KeyManager[] keyManagers(
      String keyStorePath, String keyStoreType, String keyStorePassword) throws Exception {
    if (keyStorePath == null || keyStorePath.isBlank()) {
      return null;
    }
    char[] password = keyStorePassword != null ? keyStorePassword.toCharArray() : null;
    KeyStore keyStore = KeyStore.getInstance(keyStoreType);
    if ("NONE".equals(keyStorePath)) {
      keyStore.load(null, password);
    } else {
      try (InputStream keyStoreStream = new FileInputStream(keyStorePath)) {
        keyStore.load(keyStoreStream, password);
      }
    }
    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, password);
    return keyManagerFactory.getKeyManagers();
  }

  private static boolean hasCustomTrustStore(RabbitmqConfig rabbitmqConfig) {
    return rabbitmqConfig.getTrustStore() != null && rabbitmqConfig.getTrustStore().exists();
  }
}
