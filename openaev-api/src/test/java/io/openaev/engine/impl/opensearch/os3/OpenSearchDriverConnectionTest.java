package io.openaev.engine.impl.opensearch.os3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;

import io.openaev.config.EngineConfig;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.EngineContext;
import io.openaev.engine.IndexResetEpochReassertion;
import io.openaev.engine.SlowEngineServer;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import os3.org.opensearch.client.opensearch.OpenSearchClient;

@DisplayName("OpenSearch client: bounded waits")
class OpenSearchDriverConnectionTest {

  private static final int TIMEOUT_MS = 1_000;
  // Longer than the timeout: the waiting request must give up before the connection is free.
  private static final long BUSY_MS = 3 * TIMEOUT_MS;
  // Far above the timeouts: reaching it means the client waits with no deadline.
  private static final Duration HANG = Duration.ofSeconds(15);

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private static OpenSearchClient client(String url) throws URISyntaxException {
    EngineConfig config = new EngineConfig();
    config.setUrl(url);
    config.setConnectTimeoutMs(TIMEOUT_MS);
    config.setSocketTimeoutMs(TIMEOUT_MS);
    config.setMaxConnections(1);
    return new OpenSearchDriver(
            mock(EngineContext.class),
            config,
            mock(IndexingStatusRepository.class),
            mock(IndexResetEpochReassertion.class))
        .standardClient();
  }

  @Test
  @DisplayName("A request to an engine that never answers fails after the socket timeout")
  void given_engineNeverAnswers_should_failAfterSocketTimeout() throws Exception {
    try (SlowEngineServer server = SlowEngineServer.silent()) {
      // Arrange
      OpenSearchClient client = client(server.url());

      // Act
      Exception error =
          assertTimeoutPreemptively(HANG, () -> assertThrows(Exception.class, client::info));

      // Assert
      assertThat(ExceptionUtils.getThrowableList(error))
          .hasAtLeastOneElementOfType(SocketTimeoutException.class);
      client._transport().close();
    }
  }

  @Test
  @DisplayName("A request waiting for the only pooled connection fails on its lease timeout")
  void given_onlyPooledConnectionBusy_should_failWaitingRequestOnLeaseTimeout() throws Exception {
    try (SlowEngineServer server = SlowEngineServer.busyFor(BUSY_MS)) {
      // Arrange: a first request keeps the only pooled connection busy
      OpenSearchClient client = client(server.url());
      executor.submit(() -> client.info());
      server.awaitFirstRequest();

      // Act
      Exception error =
          assertTimeoutPreemptively(HANG, () -> assertThrows(Exception.class, client::info));

      // Assert: it gave up waiting for the connection, it never reached the engine
      assertThat(server.requestCount()).isEqualTo(1);
      assertThat(ExceptionUtils.getThrowableList(error))
          .hasAtLeastOneElementOfType(TimeoutException.class);
      client._transport().close();
    }
  }
}
