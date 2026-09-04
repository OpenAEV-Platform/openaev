package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.openaev.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

class RabbitmqServicePublishIntegrationTest extends IntegrationTest {

  @Autowired private RabbitmqService rabbitmqService;

  private final List<String> declaredQueues = new ArrayList<>();

  @AfterEach
  void deleteDeclaredQueues() {
    declaredQueues.forEach(rabbitmqService::safeDeleteQueue);
    declaredQueues.clear();
  }

  @DisplayName("A published message reaches the queue bound to the inject type")
  @Test
  void given_a_running_broker_when_publishing_then_the_message_reaches_the_queue()
      throws Exception {
    String injectType = declareQueueForInjectType();
    String payload = "{\"inject\":\"" + injectType + "\"}";

    rabbitmqService.publish(injectType, payload);

    awaitBodies(injectType, bodies -> assertThat(bodies).containsExactly(payload));
  }

  @DisplayName("Concurrent publishes all reach the queue, none is rejected by the bounded pool")
  @Test
  void given_as_many_callers_as_publish_threads_when_publishing_then_none_is_rejected()
      throws Exception {
    int publishThreads = (int) ReflectionTestUtils.getField(rabbitmqService, "publishThreads");
    String injectType = declareQueueForInjectType();
    List<String> payloads =
        IntStream.range(0, publishThreads).mapToObj(i -> "{\"inject\":\"" + i + "\"}").toList();

    ExecutorService callers = Executors.newFixedThreadPool(publishThreads);
    try {
      List<Callable<Void>> publishes =
          payloads.stream()
              .map(
                  payload ->
                      (Callable<Void>)
                          () -> {
                            rabbitmqService.publish(injectType, payload);
                            return null;
                          })
              .toList();
      for (Future<Void> publish : callers.invokeAll(publishes)) {
        publish.get();
      }
    } finally {
      callers.shutdownNow();
    }

    awaitBodies(
        injectType, bodies -> assertThat(bodies).containsExactlyInAnyOrderElementsOf(payloads));
  }

  /** Declares the queue {@link RabbitmqService#publish} routes to, and returns its inject type. */
  private String declareQueueForInjectType() throws Exception {
    String injectType = "test_publish_" + UUID.randomUUID().toString().replace("-", "");
    declaredQueues.add(rabbitmqService.registerQueue(injectType));
    return injectType;
  }

  private String queueOf(String injectType) {
    return rabbitmqService.getPrefix() + "_injector_" + injectType;
  }

  /** basicPublish returns before the broker has routed the message, so the read is retried. */
  private void awaitBodies(String injectType, Consumer<List<String>> assertion) {
    String queueName = queueOf(injectType);
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () ->
                assertion.accept(
                    rabbitmqService.drainQueue(queueName).stream()
                        .map(body -> new String(body, StandardCharsets.UTF_8))
                        .toList()));
  }
}
