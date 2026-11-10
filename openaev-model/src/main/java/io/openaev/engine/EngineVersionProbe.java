package io.openaev.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.EngineConfig;
import io.openaev.exception.StartupException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/**
 * Asks the search engine what it is, before any engine client is built.
 *
 * <p>The answer of {@code GET /} carries {@code version.number} on every engine, and {@code
 * version.distribution = "opensearch"} on OpenSearch outside compatibility mode, where the root
 * {@code tagline} still identifies it. The probe uses a short-lived client from {@link
 * HttpClientFactory}, which trusts the platform chain like every other outgoing connection, since
 * the right engine client cannot be chosen until the answer is known.
 */
@Component
@RequiredArgsConstructor
public class EngineVersionProbe {

  /** What the engine reported about itself. */
  public record ServerInfo(
      String distribution, String number, String tagline, String buildFlavor) {}

  private record RawResponse(int status, String body) {}

  private final EngineConfig config;
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Calls {@code GET /} on {@code engine.url}.
   *
   * @return the distribution, version, tagline and build flavor reported by the engine
   * @throws StartupException if the engine is unreachable, refuses the call or answers something
   *     that is not an Elasticsearch or OpenSearch root response
   */
  public ServerInfo detect() {
    String url = config.getUrl();
    HttpGet request;
    try {
      request = new HttpGet(url);
    } catch (IllegalArgumentException e) {
      throw new StartupException("Invalid search engine url '" + url + "'", e);
    }
    request.setHeader(HttpHeaders.ACCEPT, "application/json");
    request.setConfig(
        RequestConfig.custom()
            .setResponseTimeout(Timeout.ofMilliseconds(config.getSocketTimeoutMs()))
            .build());
    if (config.getUsername() != null) {
      String password = config.getPassword() == null ? "" : config.getPassword();
      String credentials =
          Base64.getEncoder()
              .encodeToString(
                  (config.getUsername() + ":" + password).getBytes(StandardCharsets.UTF_8));
      request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials);
    }

    RawResponse response;
    try (CloseableHttpClient client = httpClient()) {
      response =
          client.execute(
              request,
              r ->
                  new RawResponse(
                      r.getCode(),
                      r.getEntity() == null
                          ? ""
                          : EntityUtils.toString(r.getEntity(), StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new StartupException(
          "Unable to reach the search engine at " + url + " to detect its version", e);
    }

    if (response.status() == 401 || response.status() == 403) {
      throw new StartupException(
          "The search engine at "
              + url
              + " refused the version detection (HTTP "
              + response.status()
              + "): check engine.username and engine.password");
    }
    if (response.status() != 200) {
      throw new StartupException(
          "The search engine at "
              + url
              + " answered HTTP "
              + response.status()
              + " to the version detection");
    }
    return parse(url, response.body());
  }

  private ServerInfo parse(String url, String body) {
    JsonNode root;
    try {
      root = mapper.readTree(body);
    } catch (IOException e) {
      throw new StartupException(
          "The search engine at " + url + " answered a non-JSON body to the version detection", e);
    }
    JsonNode version = root.path("version");
    if (!version.hasNonNull("number")) {
      throw new StartupException(
          "The server at " + url + " does not look like Elasticsearch or OpenSearch: no version");
    }
    String distribution =
        version.hasNonNull("distribution") ? version.get("distribution").asText() : null;
    String tagline = root.hasNonNull("tagline") ? root.get("tagline").asText() : null;
    String buildFlavor =
        version.hasNonNull("build_flavor") ? version.get("build_flavor").asText() : null;
    return new ServerInfo(distribution, version.get("number").asText(), tagline, buildFlavor);
  }

  private CloseableHttpClient httpClient() {
    Timeout connectTimeout = Timeout.ofMilliseconds(config.getConnectTimeoutMs());
    if (config.isRejectUnauthorized()) {
      return httpClientFactory.httpClientNoRetry(connectTimeout);
    }
    // engine.reject-unauthorized=false: no certificate nor hostname check, like the engine
    // clients. The platform factory deliberately offers no such client.
    try {
      return HttpClients.custom()
          .disableAutomaticRetries()
          .setConnectionManager(
              PoolingHttpClientConnectionManagerBuilder.create()
                  .setTlsSocketStrategy(
                      ClientTlsStrategyBuilder.create()
                          .setSslContext(
                              SSLContextBuilder.create()
                                  .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                                  .build())
                          .setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                          .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                          .buildClassic())
                  .setDefaultConnectionConfig(
                      ConnectionConfig.custom().setConnectTimeout(connectTimeout).build())
                  .build())
          .build();
    } catch (GeneralSecurityException e) {
      throw new StartupException("Unable to build the engine TLS context", e);
    }
  }
}
