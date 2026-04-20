package io.openaev.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.QueueConfig;
import io.openaev.config.RabbitmqConfig;
import io.openaev.driver.RabbitmqDriver;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.service.queue.QueueExecution;
import io.openaev.service.queue.Queueable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Facade for all RabbitMQ interactions.
 *
 * <p>This is the <b>only</b> class that application code should depend on for messaging. No other
 * service, controller, or helper should import {@code com.rabbitmq.client.*} or {@link
 * RabbitmqConfig} directly.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Publishing messages to exchanges
 *   <li>Declaring exchanges, queues, and bindings
 *   <li>Health-checking the broker
 *   <li>Exposing broker metadata (prefix, connection info, version)
 * </ul>
 *
 * @see io.openaev.driver.RabbitmqDriver for connection factory creation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitmqService {

  /** Routing key suffix used for constructing the full routing key. */
  public static final String ROUTING_KEY = "_push_routing_";

  /** Exchange key suffix used for constructing the full exchange name. */
  public static final String EXCHANGE_KEY = "_amqp.connector.exchange";

  private static final String EXECUTION_PREFIX = "_execution_";
  private static final String EXCHANGE_SUFFIX_PREFIX = "_amqp.";
  private static final String EXCHANGE_SUFFIX_POSTFIX = ".exchange";
  private static final String TOPIC_EXCHANGE_TYPE = "topic";
  private static final String DIRECT_EXCHANGE_TYPE = "direct";
  private static final String QUORUM_QUEUE_TYPE = "quorum";
  private static final String X_QUEUE_TYPE = "x-queue-type";
  private static final String INJECTOR_PREFIX = "_injector_";

  private final RabbitmqConfig rabbitmqConfig;
  private final ConnectionFactory connectionFactory;
  private final RabbitmqDriver rabbitmqDriver;

  // -- CONFIGURATION --

  /**
   * Returns the configured queue/exchange prefix.
   *
   * @return the prefix string
   */
  public String getPrefix() {
    return rabbitmqConfig.getPrefix();
  }

  /**
   * Returns an immutable snapshot of the broker connection details (hostname, vhost, SSL, port,
   * credentials).
   *
   * <p>Use this method instead of accessing individual connection properties.
   *
   * @return a {@link BrokerConnectionInfo} containing the current connection settings
   */
  public BrokerConnectionInfo getConnectionInfo() {
    return new BrokerConnectionInfo(
        rabbitmqConfig.getHostname(),
        rabbitmqConfig.getVhost(),
        rabbitmqConfig.isSsl(),
        rabbitmqConfig.getPort(),
        rabbitmqConfig.getUser(),
        rabbitmqConfig.getPass());
  }

  // -- PUBLISH --

  /**
   * Publishes a JSON message to RabbitMQ for a specific inject type.
   *
   * @param injectType the type of inject, used to construct the routing key
   * @param publishedJson the JSON payload to publish
   * @throws IOException if an I/O error occurs during publishing
   * @throws TimeoutException if the connection or publishing times out
   */
  public void publish(String injectType, String publishedJson)
      throws IOException, TimeoutException {
    if (injectType == null || injectType.isBlank()) {
      throw new IllegalArgumentException("injectType cannot be null or empty");
    }
    if (publishedJson == null || publishedJson.isBlank()) {
      throw new IllegalArgumentException("publishedJson cannot be null or empty");
    }

    try (Connection connection = connectionFactory.newConnection();
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

  // -- QUEUE MANAGEMENT --

  /**
   * Creates a new {@link BatchQueueService} for the given element type and queue configuration.
   *
   * <p>This factory method hides the underlying {@link RabbitmqDriver} and queue-prefix details
   * from callers. Application code should use this method instead of constructing {@code
   * BatchQueueService} directly, so that no service or controller needs to depend on {@link
   * RabbitmqDriver}.
   *
   * @param <T> the type of element processed by the queue (must implement {@link Queueable})
   * @param clazz the class of element that will be deserialized from queue messages
   * @param queueExecution the callback to handle batches of elements (may be {@code null} if set
   *     later via {@link BatchQueueService#setQueueExecution})
   * @param mapper the Jackson {@link ObjectMapper} used for serialization/deserialization
   * @param queueConfig the queue configuration (name, worker count, QoS, etc.)
   * @return a fully initialized and connected {@link BatchQueueService}
   * @throws IOException if an I/O error occurs while connecting to RabbitMQ
   * @throws TimeoutException if the connection to RabbitMQ times out
   */
  public <T extends Queueable> BatchQueueService<T> createBatchQueueService(
      Class<T> clazz,
      QueueExecution<T> queueExecution,
      ObjectMapper mapper,
      QueueConfig queueConfig)
      throws IOException, TimeoutException {
    return new BatchQueueService<>(
        clazz, queueExecution, rabbitmqConfig.getPrefix(), mapper, queueConfig, rabbitmqDriver);
  }

  /**
   * Creates a tenant-scoped {@link BatchQueueService} whose queues are namespaced under the given
   * tenant. Queue names follow the pattern {@code <prefix>-<tenantId>_execution_<queueName>}.
   *
   * @param <T> the type of element processed by the queue
   * @param clazz the class of element that will be deserialized from queue messages
   * @param queueExecution the callback to handle batches of elements
   * @param mapper the Jackson {@link ObjectMapper}
   * @param queueConfig the queue configuration
   * @param tenantId the tenant identifier used in queue naming
   * @return a fully initialized and connected {@link BatchQueueService}
   * @throws IOException if an I/O error occurs while connecting to RabbitMQ
   * @throws TimeoutException if the connection to RabbitMQ times out
   */
  public <T extends Queueable> BatchQueueService<T> createBatchQueueService(
      Class<T> clazz,
      QueueExecution<T> queueExecution,
      ObjectMapper mapper,
      QueueConfig queueConfig,
      String tenantId)
      throws IOException, TimeoutException {
    String tenantPrefix = rabbitmqConfig.getPrefix() + "-" + tenantId;
    return new BatchQueueService<>(
        clazz, queueExecution, tenantPrefix, mapper, queueConfig, rabbitmqDriver);
  }

  /**
   * Declares a set of tenant-scoped RabbitMQ queues (exchange + queue + binding) for each provided
   * queue configuration, plus the tenant-scoped connector exchange.
   *
   * <p>Idempotent: if the queues/exchanges already exist with the same settings the calls are
   * no-ops.
   *
   * <p>Exchange type is {@code "topic"} and queue type comes from {@link
   * RabbitmqConfig#getQueueType()} to match what {@link BatchQueueService} declares internally.
   *
   * @param tenantId the tenant ID used to namespace the queues
   * @param queueConfigs the queue configurations to declare
   */
  public void declareQueuesForTenant(String tenantId, List<QueueConfig> queueConfigs)
      throws IOException, TimeoutException {
    if (queueConfigs == null || queueConfigs.isEmpty()) {
      return;
    }

    String tenantPrefix = rabbitmqConfig.getPrefix() + "-" + tenantId;
    // Must match BatchQueueService.createChannels() which hardcodes "quorum"
    Map<String, Object> queueOptions = new HashMap<>();
    queueOptions.put(X_QUEUE_TYPE, QUORUM_QUEUE_TYPE);

    try (Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel()) {
      for (QueueConfig config : queueConfigs) {
        String queueName = tenantPrefix + EXECUTION_PREFIX + config.getQueueName();
        String exchangeName =
            tenantPrefix + EXCHANGE_SUFFIX_PREFIX + config.getQueueName() + EXCHANGE_SUFFIX_POSTFIX;
        String routingKey = tenantPrefix + ROUTING_KEY + config.getQueueName();

        channel.exchangeDeclare(exchangeName, TOPIC_EXCHANGE_TYPE, true);
        channel.queueDeclare(queueName, true, false, false, queueOptions);
        channel.queueBind(queueName, exchangeName, routingKey);
        log.debug(
            "Declared queue '{}' with exchange '{}' for tenant '{}'",
            queueName,
            exchangeName,
            tenantId);
      }

      // Declare tenant-scoped connector exchange
      String connectorExchange = tenantPrefix + EXCHANGE_KEY;
      channel.exchangeDeclare(connectorExchange, DIRECT_EXCHANGE_TYPE, true);
      log.debug("Declared connector exchange '{}' for tenant '{}'", connectorExchange, tenantId);
    }
  }

  /**
   * Deletes all tenant-scoped queues and exchanges for each provided queue configuration.
   *
   * @param tenantId the tenant ID whose queues should be deleted
   * @param queueConfigs the queue configurations to delete
   */
  public void deleteQueuesForTenant(String tenantId, List<QueueConfig> queueConfigs)
      throws IOException, TimeoutException {
    if (queueConfigs == null || queueConfigs.isEmpty()) {
      return;
    }

    String tenantPrefix = rabbitmqConfig.getPrefix() + "-" + tenantId;

    try (Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel()) {
      for (QueueConfig config : queueConfigs) {
        String queueName = tenantPrefix + EXECUTION_PREFIX + config.getQueueName();
        String exchangeName =
            tenantPrefix + EXCHANGE_SUFFIX_PREFIX + config.getQueueName() + EXCHANGE_SUFFIX_POSTFIX;

        channel.queueDelete(queueName);
        channel.exchangeDelete(exchangeName);
        log.debug(
            "Deleted queue '{}' and exchange '{}' for tenant '{}'",
            queueName,
            exchangeName,
            tenantId);
      }
    }
  }

  /**
   * Registers an injector queue by creating a connection, declaring the exchange/queue/binding, and
   * returning the full queue name.
   *
   * @param identifier the identifier used for the queue and routing key
   * @return the full queue name (prefixed)
   * @throws IOException if an I/O error occurs
   * @throws TimeoutException if the connection times out
   */
  public String registerQueue(String identifier) throws IOException, TimeoutException {
    String queueName = INJECTOR_PREFIX + identifier;
    try (Connection connection = connectionFactory.newConnection()) {
      createChannel(connection, queueName, identifier);
    }
    return rabbitmqConfig.getPrefix() + queueName;
  }

  // -- HEALTH & METADATA --

  /** Cached broker version (thread-safe lazy initialization). */
  private volatile String cachedVersion;

  /**
   * Returns the RabbitMQ server version obtained from the AMQP connection handshake.
   *
   * <p>The version is fetched lazily on first call and cached for the lifetime of the application.
   * Returns {@code null} if the broker is unreachable or the version property is unavailable.
   *
   * @return the RabbitMQ version string, or {@code null} if unavailable
   */
  public String getVersion() {
    if (cachedVersion == null && rabbitmqConfig.getHostname() != null) {
      try (Connection connection = connectionFactory.newConnection()) {
        Map<String, Object> serverProperties = connection.getServerProperties();
        if (serverProperties != null && serverProperties.containsKey("version")) {
          cachedVersion = serverProperties.get("version").toString();
        }
      } catch (IOException | TimeoutException e) {
        log.warn("Unable to retrieve RabbitMQ version from broker", e);
      }
    }
    return cachedVersion;
  }

  /**
   * Checks the health of the RabbitMQ broker by opening and immediately closing a connection and
   * channel.
   *
   * @throws IOException if the broker is unreachable
   * @throws TimeoutException if the connection times out
   */
  public void checkHealth() throws IOException, TimeoutException {
    try (Connection connection = connectionFactory.newConnection()) {
      connection.createChannel().close();
    }
  }

  // -- INTERNAL --

  /**
   * Creates a channel on the given connection and declares the exchange, queue, and binding.
   *
   * @param connection the RabbitMQ connection to create the channel on
   * @param queueName the queue name suffix (will be prefixed)
   * @param routingKey the routing key suffix (will be prefixed)
   */
  private void createChannel(Connection connection, String queueName, String routingKey)
      throws IOException {
    String fullQueueName = rabbitmqConfig.getPrefix() + queueName;
    String fullRoutingKey = rabbitmqConfig.getPrefix() + ROUTING_KEY + routingKey;
    String fullExchangeKey = rabbitmqConfig.getPrefix() + EXCHANGE_KEY;

    Map<String, Object> queueOptions = new HashMap<>();
    queueOptions.put(X_QUEUE_TYPE, rabbitmqConfig.getQueueType());

    try (Channel channel = connection.createChannel()) {
      channel.exchangeDeclare(fullExchangeKey, DIRECT_EXCHANGE_TYPE, true);
      channel.queueDeclare(fullQueueName, true, false, false, queueOptions);
      channel.queueBind(fullQueueName, fullExchangeKey, fullRoutingKey);
    } catch (TimeoutException e) {
      throw new IOException("Timeout while creating channel", e);
    }
  }
}
