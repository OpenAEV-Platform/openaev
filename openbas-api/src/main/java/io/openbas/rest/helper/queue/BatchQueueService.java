package io.openbas.rest.helper.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import io.openbas.config.QueueConfig;
import io.openbas.config.RabbitmqConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BatchQueueService<T> {

  private final Class<T> clazz;
  private final QueueExecution<T> queueExecution;

  public static final String ROUTING_KEY = "_push_routing_%s";
  public static final String EXCHANGE_KEY = "_amqp.%s.exchange";
  public static final String QUEUE_NAME = "_execution_%s";

  protected ObjectMapper mapper;

  private final RabbitmqConfig rabbitmqConfig;

  private Connection connection;
  private Channel publisherChannel;
  private final String routingKey;
  private final String exchangeName;
  private final String queueName;

  private final BlockingQueue<T> queue;

  private final Map<T, DeliveryContext> deliveryTable = new HashMap<>();

  private final QueueConfig queueConfig;
  private final ScheduledExecutorService reconnectionExecutor;
  private final ShutdownListener shutdownListener;

  private final List<Channel> consumerChannels = new ArrayList<>();
  private final AtomicBoolean insertInProgress = new AtomicBoolean(false);

  /**
   * Public constructor of the BatchQueueService
   *
   * @param clazz the class of element that will be processed
   * @param queueExecution the method to handle a list of the class element
   * @param rabbitmqConfig the rabbitmq config object
   * @param mapper the mapper to use
   * @param queueConfig the queue config to use
   * @throws IOException In case of issue when communicating with rabbitMQ
   * @throws TimeoutException In case of a non responding rabbitMQ
   */
  public BatchQueueService(
      Class<T> clazz,
      QueueExecution<T> queueExecution,
      RabbitmqConfig rabbitmqConfig,
      ObjectMapper mapper,
      QueueConfig queueConfig)
      throws IOException, TimeoutException {
    this.clazz = clazz;
    this.queueExecution = queueExecution;
    this.mapper = mapper;
    this.queueConfig = queueConfig;
    this.rabbitmqConfig = rabbitmqConfig;
    shutdownListener = this::handleConnectionShutdown;
    exchangeName =
        rabbitmqConfig.getPrefix()
            + String.format(BatchQueueService.EXCHANGE_KEY, queueConfig.getQueueName());
    routingKey =
        rabbitmqConfig.getPrefix()
            + String.format(BatchQueueService.ROUTING_KEY, queueConfig.getQueueName());
    queueName =
        rabbitmqConfig.getPrefix()
            + String.format(BatchQueueService.QUEUE_NAME, queueConfig.getQueueName());

    // The queue that will contain the object we need to process
    queue = new LinkedBlockingQueue<>();

    establishConnection();

    // A scheduler to handle batches that did not reached the critical mass
    ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
    scheduledExecutor.scheduleAtFixedRate(
        this::processBufferedBatch,
        this.queueConfig.getConsumerFrequency(),
        this.queueConfig.getConsumerFrequency(),
        TimeUnit.MILLISECONDS);

    // Reconnection executor that we will start if we ever lose connection
    this.reconnectionExecutor = Executors.newScheduledThreadPool(1);
  }

  /**
   * Method to establish connection with rabbitMQ
   *
   * @throws IOException in case of issue while connecting to the server
   * @throws TimeoutException in case of issue while connecting to the server
   */
  private void establishConnection() throws IOException, TimeoutException {
    // Init a Connection factory
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbitmqConfig.getHostname());
    factory.setPort(rabbitmqConfig.getPort());
    factory.setUsername(rabbitmqConfig.getUser());
    factory.setPassword(rabbitmqConfig.getPass());
    factory.setVirtualHost(rabbitmqConfig.getVhost());
    factory.setAutomaticRecoveryEnabled(false);
    factory.setNetworkRecoveryInterval(5000);
    factory.setRequestedHeartbeat(30);
    factory.setConnectionTimeout(10000);
    connection = factory.newConnection();

    // Handle shutdown
    connection.addShutdownListener(shutdownListener);

    // Create consumers that will handle the processing
    createChannels();
  }

  /**
   * Creates a consumer for the queue
   *
   * @throws IOException In case of issue when communicating with rabbitMQ
   */
  private void createChannels() throws IOException {
    try {
      // Creation of the channels, exchange and queue
      publisherChannel = connection.createChannel();
      publisherChannel.basicQos(queueConfig.getPublisherQos()); // Per publisher limit
      publisherChannel.exchangeDeclare(exchangeName, "topic", true);
      Map<String, Object> arguments = new HashMap<>();
      arguments.put("x-queue-type", "quorum");
      publisherChannel.queueDeclare(queueName, true, false, false, arguments);
      publisherChannel.queueBind(queueName, exchangeName, routingKey);

      consumerChannels.clear();

      for (int i = 0; i < queueConfig.getConsumerNumber(); ++i) {
        Channel consumerChannel = connection.createChannel();
        consumerChannels.add(consumerChannel);
        consumerChannel.basicQos(queueConfig.getConsumerQos());

        // What to do when a message is consumed
        DeliverCallback deliverCallback =
            (consumerTag, delivery) -> {
              // We get the object to process
              String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
              log.trace("Received message from queue {} : '{}'", queueName, message);

              // Unmarshalling of our object and setting it in the queue for processing
              T element = mapper.readValue(message, clazz);
              try {
                queue.put(element);
              } catch (InterruptedException e) {
                log.error(String.format("Error processing message: %s", e.getMessage()), e);
                // Nack the message and sending it back to the queue
                consumerChannel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                return;
              }

              // Add the message and delivery tag into a hashmap that will allow us to ack when
              // we've inserted in base
              deliveryTable.put(
                  element,
                  DeliveryContext.builder()
                      .tag(delivery.getEnvelope().getDeliveryTag())
                      .deliveryChannel(consumerChannel)
                      .build());

              // If we reach a critical mass, we take care of it immediately
              if (queue.size() > this.queueConfig.getMaxSize()) {
                processBufferedBatch();
              }
            };

        CancelCallback cancelCallback =
            consumerTag -> log.warn("Consumer {} was cancelled", consumerTag);

        // Setting up the consumer itself
        consumerChannel.basicConsume(
            queueName,
            false,
            String.format("consumer-%s-%d", queueConfig.getQueueName(), i),
            false,
            false,
            null,
            deliverCallback,
            cancelCallback);
      }
    } catch (IOException e) {
      log.error("Error creating consumer: {}", e.getMessage(), e);
      throw e;
    }
  }

  /**
   * Handle the connection shutdown
   *
   * @param cause the cause of the shutdown
   */
  private void handleConnectionShutdown(ShutdownSignalException cause) {
    // If we're just closing openbas, all is good
    if (cause.isInitiatedByApplication()) {
      log.info("Connection shut down by application");
      return;
    }

    // Otherwise, we lost the connection to the server
    log.error("Connection lost unexpectedly: {}", cause.getMessage(), cause);
    connection.removeShutdownListener(shutdownListener);

    // Start trying to reconnect
    reconnectionExecutor.schedule(this::attemptReconnection, 10000, TimeUnit.MILLISECONDS);
  }

  /** Reconnection attempt */
  private void attemptReconnection() {
    log.info("Attempting RabbitMQ reconnection");

    try {
      // Close the resources
      closeResources();

      // Trying to reestablish connection
      establishConnection();

      log.info("Reconnection successful");

    } catch (Exception e) {
      log.error(String.format("Reconnection attempt failed: %s", e.getMessage()), e);

      // We failed. We schedule a new try ...
      reconnectionExecutor.schedule(this::attemptReconnection, 10, TimeUnit.SECONDS);
    }
  }

  /** Close the resources */
  private void closeResources() throws IOException, TimeoutException {
    try {
      // Close consumer channels
      for (Channel channel : consumerChannels) {
        if (channel != null && channel.isOpen()) {
          channel.close();
        }
      }

      // Closing the publishing channel
      if (publisherChannel != null && publisherChannel.isOpen()) {
        publisherChannel.close();
      }

      // Close the connection if it's open
      if (connection != null && connection.isOpen()) {
        connection.close();
      }
    } catch (Exception e) {
      log.warn("Error closing resources: {}", e.getMessage());
      throw e;
    } finally {
      consumerChannels.clear();
    }
  }

  /**
   * Process messages in the queue buffer. It will only process as many messages as what's
   * configures in openbas.queue-config.<name of the queue>.max-size
   */
  public void processBufferedBatch() {
    if (insertInProgress.compareAndSet(false, true)) {
      do {
        // Draining the queue into the list with a max size
        List<T> currentBatch = new ArrayList<>();
        queue.drainTo(currentBatch);

        // If the list is not empty, we process it
        if (!currentBatch.isEmpty()) {
          log.info("Processing batch of {}", currentBatch.size());
          try {
            queueExecution.perform(currentBatch);
          } catch (Exception e) {
            log.error("Error processing batch - Error during ingestion", e);
          }
        }

        // Sending Ack for all the processed element in the batch
        for (T element : currentBatch) {
          try {
            DeliveryContext elementToAck = deliveryTable.remove(element);
            if (elementToAck != null) {
              elementToAck.getDeliveryChannel().basicAck(elementToAck.getTag(), false);
            }
          } catch (IOException e) {
            log.error(
                String.format(
                    "Error processing batch - Cannot Ack the message: %s", e.getMessage()),
                e);
          }
        }
      } while (!queue.isEmpty());
      insertInProgress.set(false);
    }
  }

  /**
   * Publish a stringified object of type T into the queue
   *
   * @param publishedJson the stringified T object
   * @throws IOException in case of error during the publish
   */
  public void publish(String publishedJson) throws IOException {
    try {
      publisherChannel.basicPublish(exchangeName, routingKey, null, publishedJson.getBytes());
    } catch (IOException e) {
      log.error(String.format("Error publishing batch: %s", e.getMessage()), e);
      throw e;
    }
  }
}
