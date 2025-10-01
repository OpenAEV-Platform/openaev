package io.openaev.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.RabbitmqConfig;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueueService {

  public static final String ROUTING_KEY = "_push_routing_";
  public static final String EXCHANGE_KEY = "_amqp.connector.exchange";

  @Resource protected ObjectMapper mapper;

  @Resource private RabbitmqConfig rabbitmqConfig;

  public void publish(String injectType, String publishedJson)
      throws IOException, TimeoutException {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbitmqConfig.getHostname());
    factory.setPort(rabbitmqConfig.getPort());
    factory.setUsername(rabbitmqConfig.getUser());
    factory.setPassword(rabbitmqConfig.getPass());
    factory.setVirtualHost(rabbitmqConfig.getVhost());
    Connection connection = null;
    try {
      connection = factory.newConnection();
      Channel channel = connection.createChannel();
      String routingKey = rabbitmqConfig.getPrefix() + ROUTING_KEY + injectType;
      String exchangeKey = rabbitmqConfig.getPrefix() + EXCHANGE_KEY;
      channel.basicPublish(exchangeKey, routingKey, null, publishedJson.getBytes());
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (IOException ex) {
          log.error(
              "Unable to close RabbitMQ connection. You should worry as this could impact performance",
              ex);
        }
      }
    }
  }
}
