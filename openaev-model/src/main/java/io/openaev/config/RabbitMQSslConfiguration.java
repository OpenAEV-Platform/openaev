package io.openaev.config;

import com.rabbitmq.client.ConnectionFactory;
import java.io.InputStream;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RabbitMQSslConfiguration {
  /**
   * Configures SSL settings for the ConnectionFactory.
   *
   * @param factory the ConnectionFactory to configure
   * @throws Exception if SSL configuration fails
   */
  public void configureSsl(ConnectionFactory factory, RabbitmqConfig rabbitmqConfig)
      throws Exception {
    if (rabbitmqConfig.getTrustStore() != null && rabbitmqConfig.getTrustStore().exists()) {
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      try (InputStream trustStoreStream = rabbitmqConfig.getTrustStore().getInputStream()) {
        String password = rabbitmqConfig.getTrustStorePassword();
        trustStore.load(trustStoreStream, password != null ? password.toCharArray() : null);
      }

      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(trustStore);

      SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
      sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
      factory.useSslProtocol(sslContext);
    } else {
      // Use default SSL context if no custom trust store is provided
      factory.useSslProtocol();
    }
    log.debug("SSL configured for RabbitMQ connection");
  }
}
