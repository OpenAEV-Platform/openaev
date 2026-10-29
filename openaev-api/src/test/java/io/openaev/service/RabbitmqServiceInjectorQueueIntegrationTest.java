package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.IntegrationTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RabbitmqServiceInjectorQueueIntegrationTest extends IntegrationTest {

  @Autowired private RabbitmqService rabbitmqService;
  @Autowired private ConnectionFactory connectionFactory;

  private final List<String> declaredQueues = new ArrayList<>();

  @AfterEach
  void deleteDeclaredQueues() {
    declaredQueues.forEach(rabbitmqService::safeDeleteQueue);
    declaredQueues.clear();
  }

  @Nested
  @DisplayName("Deleting the queue of a deleted injector")
  class DeleteInjectorQueueIfUnused {

    @Test
    @DisplayName("A queue nobody reads is deleted with its pending injects")
    void given_unconsumed_queue_with_pending_inject_should_delete_queue() throws Exception {
      // Arrange
      String injectorId = registerInjectorQueue();
      rabbitmqService.publish(injectorId, "{\"inject\":\"stale\"}");

      // Act
      rabbitmqService.deleteInjectorQueueIfUnused(injectorId);

      // Assert
      assertThat(queueExists(queueOf(injectorId))).isFalse();
    }

    @Test
    @DisplayName("A queue still read by another deployment is kept")
    void given_consumed_queue_should_keep_queue() throws Exception {
      // Arrange
      String injectorId = registerInjectorQueue();
      try (Connection connection = connectionFactory.newConnection();
          Channel consumer = connection.createChannel()) {
        consumer.basicConsume(queueOf(injectorId), true, (tag, delivery) -> {}, tag -> {});

        // Act
        rabbitmqService.deleteInjectorQueueIfUnused(injectorId);

        // Assert
        assertThat(queueExists(queueOf(injectorId))).isTrue();
      }
    }

    @Test
    @DisplayName("A missing queue is ignored")
    void given_missing_queue_should_not_throw() {
      // Arrange
      String injectorId = UUID.randomUUID().toString();

      // Act & Assert
      assertThatNoException()
          .isThrownBy(() -> rabbitmqService.deleteInjectorQueueIfUnused(injectorId));
    }
  }

  private String registerInjectorQueue() throws Exception {
    String injectorId = UUID.randomUUID().toString();
    declaredQueues.add(rabbitmqService.registerQueue(injectorId));
    return injectorId;
  }

  private String queueOf(String injectorId) {
    return rabbitmqService.getPrefix() + "_injector_" + injectorId;
  }

  /** A passive declare fails with a 404 channel error when the queue does not exist. */
  private boolean queueExists(String queueName) throws Exception {
    try (Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel()) {
      channel.queueDeclarePassive(queueName);
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
