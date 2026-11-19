package io.openaev.opencrq.counterpart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.opencrq.config.OpenCRQConfig;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.xtm.counterpart.Counterpart;
import io.openaev.xtm.counterpart.CounterpartKind;
import io.openaev.xtm.counterpart.CounterpartProvider;
import io.openaev.xtm.counterpart.CounterpartUnavailableException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/**
 * Finds our objects in the OpenCRQ the tenant is connected to (openaev.xtm.opencrq.{tenant}.*),
 * with the OpenCRQ service-account token of that connection, through OpenCRQ's xtm.lookup
 * procedure: one call per kind and batch. OpenCRQ answers the risk it quantifies for the object
 * (risk scenarios driven, annual loss, fix value), carried as the counterpart's facts.
 */
@Component
@RequiredArgsConstructor
public class OpenCRQCounterpartProvider implements CounterpartProvider {

  public static final String PRODUCT = "opencrq";

  static final String LOOKUP_PROCEDURE = "xtm.lookup";

  /** The kinds OpenCRQ keys its objects by: threats, CVEs and assets (by hostname). */
  static final Set<CounterpartKind> SUPPORTED =
      Set.of(CounterpartKind.THREAT, CounterpartKind.VULNERABILITY, CounterpartKind.ASSET);

  /** A counterpart lookup sits on a page view: an unreachable OpenCRQ is given up fast. */
  static final Timeout TIMEOUT = Timeout.of(5, TimeUnit.SECONDS);

  private final XtmConfig xtmConfig;
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper mapper;

  @Override
  public String product() {
    return PRODUCT;
  }

  @Override
  public Optional<String> connectedUrl(String tenantId) {
    return config(tenantId).map(OpenCRQConfig::getFormattedUrl);
  }

  @Override
  public boolean supports(CounterpartKind kind) {
    return SUPPORTED.contains(kind);
  }

  @Override
  public Map<String, Counterpart> resolve(
      String tenantId, CounterpartKind kind, Collection<String> keys)
      throws CounterpartUnavailableException {
    OpenCRQConfig config =
        config(tenantId)
            .orElseThrow(() -> new CounterpartUnavailableException("OpenCRQ is not connected"));
    if (keys.isEmpty() || !supports(kind)) {
      return Map.of();
    }
    HttpGet request = new HttpGet(lookupUrl(config.getApiUrl(), kind, keys));
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(config.getToken()));
    request.addHeader(HttpHeaders.ACCEPT, "application/json");
    String body;
    int status;
    try (CloseableHttpClient client = httpClientFactory.httpClientNoRetry(TIMEOUT)) {
      String[] read = new String[1];
      status =
          client.execute(
              request,
              response -> {
                read[0] = EntityUtils.toString(response.getEntity());
                return response.getCode();
              });
      body = read[0];
    } catch (IOException e) {
      throw new CounterpartUnavailableException("OpenCRQ cannot be reached", e);
    }
    if (status != 200 || body == null) {
      throw new CounterpartUnavailableException(
          "OpenCRQ refused the counterpart lookup (status %d)".formatted(status));
    }
    try {
      return match(kind, keys, mapper.readTree(body));
    } catch (IOException e) {
      throw new CounterpartUnavailableException("OpenCRQ answered an unreadable lookup", e);
    }
  }

  /** The tRPC query URL: GET {api}/xtm.lookup?input={"kind":...,"keys":[...]}. */
  String lookupUrl(String apiUrl, CounterpartKind kind, Collection<String> keys) {
    ObjectNode input = mapper.createObjectNode();
    input.put("kind", kind.name());
    ArrayNode array = input.putArray("keys");
    keys.forEach(array::add);
    return apiUrl
        + "/"
        + LOOKUP_PROCEDURE
        + "?input="
        + URLEncoder.encode(input.toString(), StandardCharsets.UTF_8);
  }

  /**
   * Keeps the objects OpenCRQ answered for a requested key, first match wins per key. OpenCRQ
   * answers keys in its own case (STIX ids and hostnames lower-cased, CVEs upper-cased), so a key
   * is matched case-insensitively and returned as it was asked.
   */
  Map<String, Counterpart> match(CounterpartKind kind, Collection<String> keys, JsonNode body) {
    Map<String, String> asked = new HashMap<>();
    keys.forEach(k -> asked.putIfAbsent(k.toLowerCase(Locale.ROOT), k));
    Map<String, Counterpart> found = new HashMap<>();
    for (JsonNode node : body.path("result").path("data")) {
      String answered = node.path("object_key").asText(null);
      String id = node.path("object_id").asText(null);
      String url = node.path("object_url").asText(null);
      if (answered == null || id == null || url == null) {
        continue;
      }
      String key = asked.get(answered.toLowerCase(Locale.ROOT));
      if (key == null || found.containsKey(key)) {
        continue;
      }
      List<Counterpart.Fact> facts = new ArrayList<>();
      for (JsonNode fact : node.path("object_facts")) {
        String factKey = fact.path("key").asText(null);
        String factValue = fact.path("value").asText(null);
        if (factKey != null && factValue != null) {
          facts.add(new Counterpart.Fact(factKey, factValue));
        }
      }
      found.put(
          key,
          new Counterpart(
              key,
              PRODUCT,
              id,
              node.path("object_type").asText(kind.name().toLowerCase(Locale.ROOT)),
              node.path("object_name").asText(answered),
              url,
              facts));
    }
    return found;
  }

  private Optional<OpenCRQConfig> config(String tenantId) {
    if (tenantId == null || xtmConfig.getOpencrq() == null) {
      return Optional.empty();
    }
    OpenCRQConfig config = xtmConfig.getOpencrq().get(tenantId);
    return config != null && config.isUsable() ? Optional.of(config) : Optional.empty();
  }
}
