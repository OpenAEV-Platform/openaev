package io.openaev.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Produces the {@link HttpClient} bean used by RabbitmqManagementClient. */
@Configuration
public class RabbitmqManagementClientConfig {

  /**
   * The HTTP client of the RabbitMQ management API. When {@code openaev.rabbitmq.ssl} is on, it
   * verifies the broker with the SSL context of the AMQP connection: the dedicated {@code
   * openaev.rabbitmq.trust-store} when configured, otherwise the platform trust manager (the JVM
   * trust store plus {@code openaev.extra-trusted-certs-dir}).
   *
   * @param rabbitmqConfig the RabbitMQ configuration
   * @param rabbitMQSslConfiguration the SSL configuration shared with the AMQP connection
   * @return the management API HTTP client
   */
  @Bean
  public HttpClient rabbitmqManagementHttpClient(
      RabbitmqConfig rabbitmqConfig, RabbitMQSslConfiguration rabbitMQSslConfiguration) {
    HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30));
    if (rabbitmqConfig.isSsl()) {
      try {
        builder.sslContext(rabbitMQSslConfiguration.sslContext(rabbitmqConfig));
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to configure SSL for the RabbitMQ management API client", e);
      }
    }
    return builder.build();
  }
}
