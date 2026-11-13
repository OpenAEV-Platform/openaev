package io.openaev.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Produces the {@link HttpClient} bean used by RabbitmqManagementClient. */
@Configuration
public class RabbitmqManagementClientConfig {

  /** Management API client; with SSL, it trusts the RabbitMQ trust store and the platform one. */
  @Bean
  public HttpClient rabbitmqManagementHttpClient(
      RabbitmqConfig rabbitmqConfig, RabbitMQSslConfiguration rabbitMQSslConfiguration) {
    HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30));
    if (rabbitmqConfig.isSsl()) {
      try {
        builder.sslContext(rabbitMQSslConfiguration.managementSslContext(rabbitmqConfig));
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to configure SSL for the RabbitMQ management API client", e);
      }
    }
    return builder.build();
  }
}
