package io.openaev.driver;

import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.RabbitMQSslConfiguration;
import io.openaev.config.RabbitmqConfig;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Driver responsible for creating and configuring the RabbitMQ {@link ConnectionFactory}.
 *
 * <p>This component centralizes RabbitMQ connection configuration (host, port, credentials, SSL)
 * into a single place, following the same pattern as {@link MinioDriver} for MinIO.
 *
 * <p>The {@link ConnectionFactory} bean produced here is shared across all RabbitMQ consumers in
 * the application.
 *
 * @see RabbitmqConfig for connection configuration properties
 * @see RabbitMQSslConfiguration for SSL/TLS configuration
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitmqDriver {

  private final RabbitmqConfig rabbitmqConfig;
  private final RabbitMQSslConfiguration rabbitMQSslConfiguration;

  /**
   * Creates a base RabbitMQ {@link ConnectionFactory} configured with host, port, credentials,
   * virtual host, and SSL settings.
   *
   * @return a configured ConnectionFactory instance
   */
  public ConnectionFactory createConnectionFactory() {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbitmqConfig.getHostname());
    factory.setPort(rabbitmqConfig.getPort());
    factory.setUsername(rabbitmqConfig.getUser());
    factory.setPassword(rabbitmqConfig.getPass());
    factory.setVirtualHost(rabbitmqConfig.getVhost());

    if (rabbitmqConfig.isSsl()) {
      try {
        rabbitMQSslConfiguration.configureSsl(factory, rabbitmqConfig);
      } catch (Exception e) {
        log.error("Failed to configure SSL for RabbitMQ connection", e);
        throw new IllegalStateException("Failed to configure SSL for RabbitMQ", e);
      }
    }

    return factory;
  }

  /**
   * Creates a ConnectionFactory tuned for long-lived batch consumer/publisher connections. Each
   * call returns a fresh instance so no shared state is mutated.
   *
   * <p>Differences from the default factory:
   *
   * <ul>
   *   <li>Automatic recovery disabled (reconnection is handled manually)
   *   <li>Shorter heartbeat and connection timeout
   *   <li>Dedicated thread pool for the connection's I/O
   * </ul>
   *
   * @param threadPoolSize number of threads for the connection's shared executor (typically
   *     consumerCount + publisherCount)
   * @return a configured ConnectionFactory for batch use
   */
  public ConnectionFactory createBatchConnectionFactory(int threadPoolSize) {
    ConnectionFactory factory = createConnectionFactory();
    factory.setAutomaticRecoveryEnabled(false);
    factory.setNetworkRecoveryInterval(5000);
    factory.setRequestedHeartbeat(30);
    factory.setConnectionTimeout(10000);
    factory.setSharedExecutor(Executors.newFixedThreadPool(threadPoolSize));
    return factory;
  }

  /**
   * Produces a shared {@link ConnectionFactory} Spring bean.
   *
   * @return a configured ConnectionFactory
   */
  @Bean
  public ConnectionFactory connectionFactory() {
    return createConnectionFactory();
  }
}
