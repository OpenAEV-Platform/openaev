package io.openaev.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.EngineConfig;
import io.openaev.exception.StartupException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Engine version probe (GET / on engine.url)")
class EngineVersionProbeTest {

  private static final String ELASTICSEARCH_9_ROOT =
      """
      {"name":"node-1","cluster_name":"docker-cluster",
       "version":{"number":"9.5.1","build_flavor":"default","build_type":"docker"},
       "tagline":"You Know, for Search"}
      """;
  private static final String OPENSEARCH_ROOT =
      """
      {"name":"node-1","cluster_name":"docker-cluster",
       "version":{"distribution":"opensearch","number":"3.9.0","build_type":"tar"},
       "tagline":"The OpenSearch Project: https://opensearch.org/"}
      """;
  // OpenSearch 1.x/2.x with compatibility.override_main_response_version=true: no distribution.
  private static final String OPENSEARCH_COMPATIBILITY_ROOT =
      """
      {"name":"node-1","cluster_name":"docker-cluster","cluster_uuid":"Kq3pGx7vR9ytWn2c0mJbLQ",
       "version":{"number":"7.10.2","build_type":"tar",
        "build_hash":"6c1a3c5fb5e3ad7ab0aa4b36b9a0ef7d5f5dcc5a","build_date":"2025-02-25T21:58:09Z",
        "build_snapshot":false,"lucene_version":"9.12.1",
        "minimum_wire_compatibility_version":"7.10.0",
        "minimum_index_compatibility_version":"7.0.0"},
       "tagline":"The OpenSearch Project: https://opensearch.org/"}
      """;

  // Elasticsearch Serverless: a version kept for legacy clients, the flavor identifies it.
  private static final String ELASTICSEARCH_SERVERLESS_ROOT =
      """
      {"name":"serverless","cluster_name":"my-project","cluster_uuid":"pZ3pD5YxQnWmF8kq7T2bRg",
       "version":{"number":"8.11.0","build_flavor":"serverless","build_type":"docker",
        "build_hash":"00000000","build_date":"2023-10-31","build_snapshot":false,
        "lucene_version":"9.7.0","minimum_wire_compatibility_version":"8.11.0",
        "minimum_index_compatibility_version":"8.11.0"},
       "tagline":"You Know, for Search"}
      """;

  private HttpServer server;
  private final AtomicReference<String> rootBody = new AtomicReference<>(ELASTICSEARCH_9_ROOT);
  private final AtomicReference<Integer> rootStatus = new AtomicReference<>(200);
  private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
  private EngineConfig config;

  @BeforeEach
  void setUp() throws IOException {
    this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    this.server.createContext(
        "/",
        exchange -> {
          this.receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] body = this.rootBody.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(this.rootStatus.get(), body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    this.server.start();
    this.config = new EngineConfig();
    this.config.setUrl("http://127.0.0.1:" + this.server.getAddress().getPort());
    this.config.setConnectTimeoutMs(2_000);
    this.config.setSocketTimeoutMs(2_000);
  }

  @AfterEach
  void tearDown() {
    this.server.stop(0);
  }

  private EngineVersionProbe probe() throws Exception {
    TrustManagerFactory factory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init((KeyStore) null);
    X509TrustManager trustManager =
        Arrays.stream(factory.getTrustManagers())
            .filter(X509TrustManager.class::isInstance)
            .map(X509TrustManager.class::cast)
            .findFirst()
            .orElseThrow();
    return new EngineVersionProbe(this.config, new HttpClientFactory(trustManager));
  }

  @Nested
  @DisplayName("Root responses")
  class RootResponses {

    @Test
    @DisplayName(
        "given an Elasticsearch root response, should report its version and no distribution")
    void given_elasticsearchRoot_should_reportVersionWithoutDistribution() throws Exception {
      // Act
      EngineVersionProbe.ServerInfo info = probe().detect();

      // Assert
      assertNull(info.distribution());
      assertEquals("9.5.1", info.number());
      assertEquals("You Know, for Search", info.tagline());
      assertEquals("default", info.buildFlavor());
    }

    @Test
    @DisplayName("given an OpenSearch root response, should report the opensearch distribution")
    void given_opensearchRoot_should_reportOpensearchDistribution() throws Exception {
      // Arrange
      rootBody.set(OPENSEARCH_ROOT);

      // Act
      EngineVersionProbe.ServerInfo info = probe().detect();

      // Assert
      assertEquals("opensearch", info.distribution());
      assertEquals("3.9.0", info.number());
    }

    @Test
    @DisplayName(
        "given an OpenSearch in compatibility mode, should report its tagline and be detected as"
            + " OpenSearch")
    void given_opensearchCompatibilityRoot_should_beDetectedAsOpenSearch() throws Exception {
      // Arrange
      rootBody.set(OPENSEARCH_COMPATIBILITY_ROOT);

      // Act
      EngineVersionProbe.ServerInfo info = probe().detect();

      // Assert
      assertNull(info.distribution());
      assertEquals("7.10.2", info.number());
      assertEquals("The OpenSearch Project: https://opensearch.org/", info.tagline());
      assertEquals(EngineType.OPENSEARCH, EngineType.fromServerInfo(info));
    }

    @Test
    @DisplayName(
        "given an Elasticsearch Serverless root response, should report its build flavor and get"
            + " the latest client")
    void given_serverlessRoot_should_getLatestClient() throws Exception {
      // Arrange
      rootBody.set(ELASTICSEARCH_SERVERLESS_ROOT);

      // Act
      EngineVersionProbe.ServerInfo info = probe().detect();

      // Assert
      assertEquals("8.11.0", info.number());
      assertEquals("serverless", info.buildFlavor());
      assertEquals(EngineType.ELASTICSEARCH_9, EngineType.fromServerInfo(info));
    }

    @Test
    @DisplayName("given a JSON answer without version, should refuse to start")
    void given_jsonWithoutVersion_should_refuseToStart() {
      // Arrange
      rootBody.set("{\"status\":\"ok\"}");

      // Act & Assert
      StartupException exception = assertThrows(StartupException.class, () -> probe().detect());
      assertTrue(exception.getMessage().contains("does not look like Elasticsearch or OpenSearch"));
    }

    @Test
    @DisplayName("given a non-JSON answer, should refuse to start")
    void given_nonJsonAnswer_should_refuseToStart() {
      // Arrange
      rootBody.set("<html>proxy</html>");

      // Act & Assert
      StartupException exception = assertThrows(StartupException.class, () -> probe().detect());
      assertTrue(exception.getMessage().contains("non-JSON body"));
    }
  }

  @Nested
  @DisplayName("Authentication")
  class Authentication {

    @Test
    @DisplayName("given credentials, should send them as preemptive basic auth")
    void given_credentials_should_sendPreemptiveBasicAuth() throws Exception {
      // Arrange
      config.setUsername("elastic");
      config.setPassword("changeme");

      // Act
      probe().detect();

      // Assert
      assertEquals("Basic ZWxhc3RpYzpjaGFuZ2VtZQ==", receivedAuthorization.get());
    }

    @Test
    @DisplayName("given no credentials, should send no authorization header")
    void given_noCredentials_should_sendNoAuthorizationHeader() throws Exception {
      // Act
      probe().detect();

      // Assert
      assertNull(receivedAuthorization.get());
    }

    @Test
    @DisplayName("given a 401 answer, should refuse to start and point at the credentials")
    void given_unauthorized_should_refuseToStartAndPointAtCredentials() {
      // Arrange
      rootStatus.set(401);
      rootBody.set("{\"error\":\"unauthorized\"}");

      // Act & Assert
      StartupException exception = assertThrows(StartupException.class, () -> probe().detect());
      assertTrue(exception.getMessage().contains("HTTP 401"));
      assertTrue(exception.getMessage().contains("engine.username"));
    }
  }

  @Nested
  @DisplayName("Connectivity")
  class Connectivity {

    @Test
    @DisplayName("given an unreachable engine, should refuse to start with the engine url")
    void given_unreachableEngine_should_refuseToStartWithUrl() throws Exception {
      // Arrange
      int closedPort;
      try (ServerSocket socket = new ServerSocket(0)) {
        closedPort = socket.getLocalPort();
      }
      config.setUrl("http://127.0.0.1:" + closedPort);

      // Act & Assert
      StartupException exception = assertThrows(StartupException.class, () -> probe().detect());
      assertEquals(
          "Unable to reach the search engine at http://127.0.0.1:"
              + closedPort
              + " to detect its version",
          exception.getMessage());
    }

    @Test
    @DisplayName("given a server error, should refuse to start with the HTTP status")
    void given_serverError_should_refuseToStartWithStatus() {
      // Arrange
      rootStatus.set(503);

      // Act & Assert
      StartupException exception = assertThrows(StartupException.class, () -> probe().detect());
      assertTrue(exception.getMessage().contains("HTTP 503"));
    }
  }
}
