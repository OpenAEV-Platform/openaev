package io.openaev.asset;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.RabbitmqConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

  public static final String ROUTING_KEY = "_push_routing_";
  public static final String EXCHANGE_KEY = "_amqp.connector.exchange";

  private final RabbitmqConfig rabbitmqConfig;

  public void publish(String injectType, String publishedJson)
      throws IOException, TimeoutException {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbitmqConfig.getHostname());
    factory.setPort(rabbitmqConfig.getPort());
    factory.setUsername(rabbitmqConfig.getUser());
    factory.setPassword(rabbitmqConfig.getPass());
    factory.setVirtualHost(rabbitmqConfig.getVhost());

    try (Connection connection = factory.newConnection();
        Channel channel = connection.createChannel()) {
      String routingKey = rabbitmqConfig.getPrefix() + ROUTING_KEY + injectType;
      String exchangeKey = rabbitmqConfig.getPrefix() + EXCHANGE_KEY;
      channel.basicPublish(exchangeKey, routingKey, null, publishedJson.getBytes(StandardCharsets.UTF_8));
    } catch (IOException | TimeoutException ex) {
      log.error("Error publishing to RabbitMQ", ex);
      throw ex;
    }
  }
}
