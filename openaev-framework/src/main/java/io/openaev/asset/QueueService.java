package io.openaev.asset;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.RabbitmqConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for publishing messages to RabbitMQ.
 *
 * <p>This service handles the connection management and message publishing to RabbitMQ exchanges.
 * It supports both SSL and non-SSL connections based on configuration.
 *
 * <p>Messages are published to a topic exchange with routing keys based on the inject type,
 * allowing subscribers to selectively consume messages for specific injection types.
 *
 * <p><b>Thread Safety:</b> This service creates new connections for each publish operation, making
 * it safe for concurrent use. For high-throughput scenarios, consider implementing connection
 * pooling.
 *
 * @see RabbitmqConfig for connection configuration
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

  /** Routing key suffix used for constructing the full routing key. */
  public static final String ROUTING_KEY = "_push_routing_";

  /** Exchange key suffix used for constructing the full exchange name. */
  public static final String EXCHANGE_KEY = "_amqp.connector.exchange";

  private final RabbitmqConfig rabbitmqConfig;

  /**
   * Publishes a JSON message to RabbitMQ for a specific inject type.
   *
   * @param injectType the type of inject, used to construct the routing key
   * @param publishedJson the JSON payload to publish
   * @throws IOException if an I/O error occurs during publishing
   * @throws TimeoutException if the connection or publishing times out
   * @throws IllegalArgumentException if injectType or publishedJson is null or empty
   */
  public void publish(String injectType, String publishedJson)
      throws IOException, TimeoutException {
    if (injectType == null || injectType.isBlank()) {
      throw new IllegalArgumentException("injectType cannot be null or empty");
    }
    if (publishedJson == null || publishedJson.isBlank()) {
      throw new IllegalArgumentException("publishedJson cannot be null or empty");
    }

    ConnectionFactory factory = createConnectionFactory();

    try (Connection connection = factory.newConnection();
        Channel channel = connection.createChannel()) {
      String routingKey = rabbitmqConfig.getPrefix() + ROUTING_KEY + injectType;
      String exchangeKey = rabbitmqConfig.getPrefix() + EXCHANGE_KEY;
      channel.basicPublish(
          exchangeKey, routingKey, null, publishedJson.getBytes(StandardCharsets.UTF_8));
      log.debug(
          "Successfully published message to exchange '{}' with routing key '{}'",
          exchangeKey,
          routingKey);
    } catch (IOException ex) {
      log.error(
          "I/O error publishing to RabbitMQ exchange '{}' with routing key '{}'",
          rabbitmqConfig.getPrefix() + EXCHANGE_KEY,
          rabbitmqConfig.getPrefix() + ROUTING_KEY + injectType,
          ex);
      throw ex;
    } catch (TimeoutException ex) {
      log.error("Timeout while publishing to RabbitMQ for inject type '{}'", injectType, ex);
      throw ex;
    }
  }

  /**
   * Creates and configures a RabbitMQ ConnectionFactory based on the current configuration.
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

    // Configure SSL if enabled
    if (rabbitmqConfig.isSsl()) {
      try {
        configureSsl(factory);
      } catch (Exception e) {
        log.error("Failed to configure SSL for RabbitMQ connection", e);
        throw new IllegalStateException("Failed to configure SSL for RabbitMQ", e);
      }
    }

    return factory;
  }

  /**
   * Configures SSL settings for the ConnectionFactory.
   *
   * @param factory the ConnectionFactory to configure
   * @throws Exception if SSL configuration fails
   */
  private void configureSsl(ConnectionFactory factory) throws Exception {
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

  public Channel createChannel(Connection connection, String queueName, String routingKey)
      throws IOException {
    String fullQueueName = rabbitmqConfig.getPrefix() + queueName;
    String fullRoutingKey = rabbitmqConfig.getPrefix() + ROUTING_KEY + routingKey;
    String fullExchangeKey = rabbitmqConfig.getPrefix() + EXCHANGE_KEY;

    Map<String, Object> queueOptions = new HashMap<>();
    queueOptions.put("x-queue-type", rabbitmqConfig.getQueueType());

    try (Channel channel = connection.createChannel()) {
      channel.exchangeDeclare(fullExchangeKey, "direct", true);
      channel.queueDeclare(fullQueueName, true, false, false, queueOptions);
      channel.queueBind(fullQueueName, fullExchangeKey, fullRoutingKey);
      return channel;
    } catch (TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
