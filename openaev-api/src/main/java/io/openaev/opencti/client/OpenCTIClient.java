package io.openaev.opencti.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.opencti.client.mutations.Mutation;
import io.openaev.opencti.client.response.Response;
import io.openaev.opencti.client.response.ResponseFile;
import io.openaev.opencti.client.response.fields.Error;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OpenCTIClient {
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper mapper;

  /** Cancels the bounded requests that outlive their timeout. */
  private final ScheduledExecutorService requestDeadlines = requestDeadlineExecutor();

  /**
   * The bounded no-retry clients, one per timeout, kept so that bounded calls reuse their
   * connections; closed at shutdown.
   */
  private final Map<Duration, CloseableHttpClient> boundedClients = new ConcurrentHashMap<>();

  private static ScheduledExecutorService requestDeadlineExecutor() {
    ScheduledThreadPoolExecutor executor =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              Thread thread = new Thread(runnable, "opencti-request-deadline");
              thread.setDaemon(true);
              return thread;
            });
    // A request that completes early releases its deadline task, and the request it holds, at once
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }

  public Response execute(String url, String authToken, Mutation mutation) throws IOException {
    return execute(url, authToken, mutation.getQueryText(), mutation.getVariables());
  }

  public Response execute(String url, String authToken, String mutationBody, JsonNode variables)
      throws IOException {
    HttpPost request = buildRequest(url, authToken, mutationBody, variables);
    try (CloseableHttpClient client = httpClientFactory.httpClientCustom()) {
      return execute(request, client);
    }
  }

  /**
   * Same as {@link #execute(String, String, Mutation)}, bounded end to end by {@code timeout}: the
   * TCP connect, the TLS handshake and every socket read give up after it, the request is cancelled
   * once it has run for that long in total (a response trickling in never extends it), and it is
   * never retried automatically. For background callers that must not stall on an unreachable
   * OpenCTI. A cancelled request fails with an {@link IOException}.
   *
   * <p>The calls with the same timeout share one client, so they reuse its pooled connections
   * instead of paying a new TCP connect and TLS handshake each.
   */
  public Response execute(String url, String authToken, Mutation mutation, Duration timeout)
      throws IOException {
    Objects.requireNonNull(timeout, "timeout");
    CloseableHttpClient client =
        boundedClients.computeIfAbsent(
            timeout, bound -> httpClientFactory.httpClientNoRetry(Timeout.of(bound)));
    HttpPost request =
        buildRequest(url, authToken, mutation.getQueryText(), mutation.getVariables());
    ScheduledFuture<?> deadline =
        requestDeadlines.schedule(request::cancel, timeout.toMillis(), TimeUnit.MILLISECONDS);
    try {
      return execute(request, client);
    } finally {
      deadline.cancel(false);
    }
  }

  @PreDestroy
  void stop() {
    requestDeadlines.shutdownNow();
    closeBoundedClients();
  }

  /** Closes the bounded clients; the next bounded call opens a new one. */
  void closeBoundedClients() {
    for (Duration timeout : List.copyOf(boundedClients.keySet())) {
      CloseableHttpClient boundedClient = boundedClients.remove(timeout);
      if (boundedClient != null) {
        boundedClient.close(CloseMode.GRACEFUL);
      }
    }
  }

  private HttpPost buildRequest(
      String url, String authToken, String mutationBody, JsonNode variables)
      throws JsonProcessingException {
    HttpPost req = new HttpPost(url);
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(authToken));
    req.addHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
    req.addHeader(HttpHeaders.ACCEPT, "application/json");
    Map<String, JsonNode> payload = new HashMap<>();
    payload.put("query", mapper.valueToTree(mutationBody));
    if (variables != null) {
      payload.put("variables", variables);
    }
    req.setEntity(new StringEntity(mapper.writeValueAsString(payload)));
    return req;
  }

  public ResponseFile download(String url, String authToken) throws IOException {
    try (CloseableHttpClient client = httpClientFactory.httpClientCustom()) {
      HttpGet req = new HttpGet(url);
      req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(authToken));

      try (CloseableHttpResponse res = client.execute(req)) {
        int statusCode = res.getCode();
        if (statusCode != 200) {
          log.warn(
              String.format("Error downloading file from %s with status code %s", url, statusCode));
          return null;
        }

        HttpEntity entity = res.getEntity();
        byte[] content = entity.getContent().readAllBytes();

        ResponseFile responseFile = new ResponseFile();
        responseFile.setInputStream(new ByteArrayInputStream(content));
        responseFile.setSize(content.length);
        return responseFile;
      }
    }
  }

  public record ExtractedData(int status, String body) {}

  private Response execute(ClassicHttpRequest request, CloseableHttpClient client)
      throws IOException {
    // A client is shared by the tenants: every request keeps its own cookies
    HttpClientContext context = HttpClientContext.create();
    context.setCookieStore(new BasicCookieStore());
    try {
      ExtractedData ed =
          client.execute(
              request,
              context,
              classicResponse -> {
                HttpEntity entity = classicResponse.getEntity();
                return new ExtractedData(
                    classicResponse.getCode(), entity == null ? "" : EntityUtils.toString(entity));
              });
      if (ed.body == null || ed.body.isBlank()) {
        // Gateways answer 429 and 5xx without a body: the status alone tells the caller what
        // happened
        Response response = new Response();
        response.setStatus(ed.status);
        Error err = new Error();
        err.setMessage("Empty response body (HTTP %d)".formatted(ed.status));
        response.setErrors(List.of(err));
        return response;
      }
      try {
        JsonNode node = mapper.readTree(ed.body);
        if (!node.has("errors") && !node.has("data")) {
          throw new JsonMappingException(
              null, "Response body does not conform to a GraphQL response.");
        }
        Response response = mapper.treeToValue(node, Response.class);
        response.setStatus(ed.status);
        return response;
      } catch (JsonProcessingException e) {
        // if the response body cannot be deserialised as GraphQL response
        // then we need to cope a little bit and provide as much context as possible
        Response response = new Response();
        response.setStatus(ed.status);
        Error err = new Error();
        err.setMessage(e.getMessage());
        response.setErrors(List.of(err));
        // set the data field as the full response body as a string
        ObjectNode objNode = mapper.createObjectNode();
        objNode.set("response_body", mapper.convertValue(ed.body, JsonNode.class));
        response.setData(objNode);
        return response;
      }

    } catch (IOException e) {
      throw new ClientProtocolException(
          "Unexpected response for request on: " + request.getRequestUri(), e);
    }
  }
}
