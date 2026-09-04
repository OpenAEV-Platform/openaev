package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.config.RabbitmqConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RabbitmqServicePublishTest {

  private static final String INJECT_TYPE = "openaev_email";
  private static final String PAYLOAD = "{\"inject\":\"1\"}";

  private ConnectionFactory connectionFactory;
  private Connection connection;
  private Channel channel;
  private RabbitmqService rabbitmqService;

  private final CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void setUp() {
    RabbitmqConfig rabbitmqConfig = new RabbitmqConfig();
    rabbitmqConfig.setPrefix("openaev");
    connectionFactory = mock(ConnectionFactory.class);
    connection = mock(Connection.class);
    channel = mock(Channel.class);
    rabbitmqService = new RabbitmqService(rabbitmqConfig, connectionFactory, null, null);
    ReflectionTestUtils.setField(rabbitmqService, "publishTimeoutMs", 200L);
    ReflectionTestUtils.setField(rabbitmqService, "publishThreads", 1);
    rabbitmqService.initPublishExecutor();
  }

  @AfterEach
  void tearDown() {
    release.countDown();
    rabbitmqService.shutdownPublishExecutor();
  }

  private void brokerAnswers() throws Exception {
    when(connectionFactory.newConnection()).thenReturn(connection);
    when(connection.createChannel()).thenReturn(channel);
  }

  @DisplayName("A healthy broker gets the payload on the tenant exchange and routing key")
  @Test
  void given_a_healthy_broker_when_publishing_then_the_message_is_sent() throws Exception {
    brokerAnswers();

    rabbitmqService.publish(INJECT_TYPE, PAYLOAD);

    verify(channel)
        .basicPublish(
            eq("openaev" + RabbitmqService.EXCHANGE_KEY),
            eq("openaev" + RabbitmqService.ROUTING_KEY + INJECT_TYPE),
            eq(null),
            eq(PAYLOAD.getBytes(StandardCharsets.UTF_8)));
    verify(channel).close();
    verify(connection).close();
  }

  @DisplayName("A broker error is surfaced to the caller as is, not wrapped in a timeout")
  @Test
  void given_a_failing_broker_when_publishing_then_the_io_error_is_surfaced() throws Exception {
    when(connectionFactory.newConnection()).thenThrow(new IOException("connection refused"));

    assertThatThrownBy(() -> rabbitmqService.publish(INJECT_TYPE, PAYLOAD))
        .isInstanceOf(IOException.class)
        .hasMessage("connection refused");
  }

  @DisplayName("A broker that stops answering releases the caller once the timeout elapses")
  @Test
  void given_a_stalled_broker_when_publishing_then_the_caller_is_released() throws Exception {
    brokerAnswers();
    stallOnPublish();

    long start = System.nanoTime();
    assertThatThrownBy(() -> rabbitmqService.publish(INJECT_TYPE, PAYLOAD))
        .isInstanceOf(TimeoutException.class);

    assertThat(System.nanoTime() - start).isLessThan(TimeUnit.SECONDS.toNanos(5));
  }

  @DisplayName("Once every publish thread is stalled, the next publish fails fast")
  @Test
  void given_every_thread_stalled_when_publishing_then_the_next_call_is_rejected()
      throws Exception {
    brokerAnswers();
    CountDownLatch entered = stallOnPublish();

    assertThatThrownBy(() -> rabbitmqService.publish(INJECT_TYPE, PAYLOAD))
        .isInstanceOf(TimeoutException.class);
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> rabbitmqService.publish(INJECT_TYPE, PAYLOAD))
        .isInstanceOf(TimeoutException.class)
        .hasMessage("No RabbitMQ publish thread available");
  }

  @DisplayName("A non-positive timeout falls back to the default instead of expiring on the spot")
  @Test
  void given_a_negative_timeout_when_publishing_then_the_default_applies() throws Exception {
    ReflectionTestUtils.setField(rabbitmqService, "publishTimeoutMs", -1L);
    rabbitmqService.initPublishExecutor();
    brokerAnswers();

    rabbitmqService.publish(INJECT_TYPE, PAYLOAD);

    assertThat(ReflectionTestUtils.getField(rabbitmqService, "publishTimeoutMs"))
        .isEqualTo(30_000L);
  }

  @DisplayName("A non-positive pool size falls back to the default instead of failing at startup")
  @Test
  void given_a_zero_pool_size_when_starting_then_the_default_applies() {
    ReflectionTestUtils.setField(rabbitmqService, "publishThreads", 0);

    rabbitmqService.initPublishExecutor();

    assertThat(ReflectionTestUtils.getField(rabbitmqService, "publishThreads")).isEqualTo(20);
  }

  /** Swallows interrupts, like the uninterruptible socket write of a broker under an alarm. */
  private CountDownLatch stallOnPublish() throws IOException {
    CountDownLatch entered = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              entered.countDown();
              boolean released = false;
              while (!released) {
                try {
                  released = release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
              }
              return null;
            })
        .when(channel)
        .basicPublish(any(), any(), any(), any());
    return entered;
  }
}
