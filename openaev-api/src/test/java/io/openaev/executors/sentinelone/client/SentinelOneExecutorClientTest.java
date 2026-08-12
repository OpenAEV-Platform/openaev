package io.openaev.executors.sentinelone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.model.SentinelOneAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SentinelOneExecutorClientTest {

  @Mock private SentinelOneExecutorConfig config;
  @Mock private HttpClientFactory httpClientFactory;
  @Mock private CloseableHttpClient httpClient;

  private SentinelOneExecutorClient client;

  @BeforeEach
  void setUp() throws Exception {
    when(config.getApiUrl()).thenReturn("https://example.sentinelone/");
    when(config.getApiKey()).thenReturn("api-key");
    when(config.getAccountId()).thenReturn("account-1");
    when(config.getSiteId()).thenReturn(null);
    when(config.getGroupId()).thenReturn(null);
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    client = new SentinelOneExecutorClient(config, httpClientFactory);
  }

  @Test
  void given_responseWithoutPagination_should_collectAgentsWithoutFailing() throws Exception {
    when(httpClient.execute(any(HttpGet.class), anyResponseHandler()))
        .thenReturn("{\"data\":[{\"uuid\":\"agent-1\"}]}");

    Set<SentinelOneAgent> agents = client.agents();

    assertEquals(1, agents.size());
    assertEquals("agent-1", agents.iterator().next().getUuid());
    verify(httpClient, times(1)).execute(any(HttpGet.class), anyResponseHandler());
  }

  @Test
  void given_multiplePages_should_requestEachPageWithSingleCursorParam() throws Exception {
    List<String> requestedUris = new ArrayList<>();
    AtomicInteger callIndex = new AtomicInteger();
    when(httpClient.execute(any(HttpGet.class), anyResponseHandler()))
        .thenAnswer(
            invocation -> {
              HttpGet request = invocation.getArgument(0);
              requestedUris.add(request.getUri().toString());
              if (callIndex.getAndIncrement() == 0) {
                return "{\"data\":[{\"uuid\":\"agent-1\"}],\"pagination\":{\"nextCursor\":\"cursor-1\"}}";
              }
              return "{\"data\":[{\"uuid\":\"agent-2\"}],\"pagination\":{\"nextCursor\":null}}";
            });

    Set<SentinelOneAgent> agents = client.agents();

    assertEquals(2, agents.size());
    assertEquals(2, requestedUris.size());
    assertTrue(requestedUris.get(0).endsWith("agents?isActive=true&accountIds=account-1"));
    assertTrue(
        requestedUris
            .get(1)
            .endsWith("agents?isActive=true&accountIds=account-1&cursor=cursor-1"));
    requestedUris.forEach(uri -> assertEquals(1, countOccurrences(uri, "&cursor=")));
  }

  @Test
  void given_repeatedCursor_should_stopAfterCursorAlreadyVisited() throws Exception {
    AtomicInteger callIndex = new AtomicInteger();
    when(httpClient.execute(any(HttpGet.class), anyResponseHandler()))
        .thenAnswer(
            invocation -> {
              if (callIndex.getAndIncrement() == 0) {
                return "{\"data\":[{\"uuid\":\"agent-1\"}],\"pagination\":{\"nextCursor\":\"cursor-repeat\"}}";
              }
              return "{\"data\":[{\"uuid\":\"agent-2\"}],\"pagination\":{\"nextCursor\":\"cursor-repeat\"}}";
            });

    Set<SentinelOneAgent> agents = client.agents();

    assertEquals(2, agents.size());
    verify(httpClient, times(2)).execute(any(HttpGet.class), anyResponseHandler());
  }

  private static int countOccurrences(String input, String token) {
    int count = 0;
    int position = 0;
    while ((position = input.indexOf(token, position)) != -1) {
      count++;
      position += token.length();
    }
    return count;
  }

  @SuppressWarnings("unchecked")
  private static HttpClientResponseHandler<String> anyResponseHandler() {
    return (HttpClientResponseHandler<String>) any(HttpClientResponseHandler.class);
  }
}
