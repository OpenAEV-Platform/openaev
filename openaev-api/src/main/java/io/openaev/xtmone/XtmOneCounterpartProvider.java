package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.xtm.counterpart.Counterpart;
import io.openaev.xtm.counterpart.CounterpartKind;
import io.openaev.xtm.counterpart.CounterpartProvider;
import io.openaev.xtm.counterpart.CounterpartUnavailableException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/**
 * Finds our assets in the inventory of the XTM One this platform is connected to
 * (openaev.xtm.one.*), through XTM One's asset lookup ({@code POST
 * /api/v1/organization-context/assets/lookup}, XTM One #1349): one call per batch, read with the
 * calling user's own per-user JWT, so what it answers is what that user may read in XTM One. XTM
 * One's inventory holds assets only; every other kind is not supported.
 */
@Component
@RequiredArgsConstructor
public class XtmOneCounterpartProvider implements CounterpartProvider {

  public static final String PRODUCT = "xtmone";

  static final String LOOKUP_PATH = "/api/v1/organization-context/assets/lookup";

  /** A counterpart lookup sits on a page view: an unreachable XTM One is given up fast. */
  static final Timeout TIMEOUT = Timeout.of(5, TimeUnit.SECONDS);

  private final XtmOneConfig config;
  private final XtmOneIdentity identity;
  private final XtmOneClient client;
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper mapper;

  @Override
  public String product() {
    return PRODUCT;
  }

  @Override
  public Optional<String> connectedUrl(String tenantId) {
    // Where a browser opens XTM One (its published identity), never the internal address.
    return config.isConfigured() ? Optional.ofNullable(identity.browserUrl()) : Optional.empty();
  }

  @Override
  public boolean supports(CounterpartKind kind) {
    return kind == CounterpartKind.ASSET;
  }

  @Override
  public boolean readsAsCaller() {
    return true;
  }

  @Override
  public Map<String, Counterpart> resolve(
      String tenantId, CounterpartKind kind, Collection<String> keys)
      throws CounterpartUnavailableException {
    if (!config.isConfigured()) {
      throw new CounterpartUnavailableException("XTM One is not connected");
    }
    if (keys.isEmpty() || !supports(kind)) {
      return Map.of();
    }
    ObjectNode input = mapper.createObjectNode();
    ArrayNode array = input.putArray("keys");
    keys.forEach(array::add);
    HttpPost request = new HttpPost(config.getUrl() + LOOKUP_PATH);
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + client.issueJwtForCurrentUser());
    request.addHeader(HttpHeaders.ACCEPT, "application/json");
    request.setEntity(new StringEntity(input.toString(), ContentType.APPLICATION_JSON));
    String body;
    int status;
    try (CloseableHttpClient http = httpClientFactory.httpClientNoRetry(TIMEOUT)) {
      String[] read = new String[1];
      status =
          http.execute(
              request,
              response -> {
                read[0] =
                    response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
                return response.getCode();
              });
      body = read[0];
    } catch (IOException e) {
      // Never log the request: its headers carry the user's token.
      throw new CounterpartUnavailableException("XTM One cannot be reached", e);
    }
    if (status != 200 || body == null) {
      throw new CounterpartUnavailableException(
          "XTM One refused the asset lookup (status %d)".formatted(status));
    }
    try {
      return match(keys, mapper.readTree(body));
    } catch (IOException e) {
      throw new CounterpartUnavailableException("XTM One answered an unreadable lookup", e);
    }
  }

  /**
   * Keeps the inventory assets XTM One answered for a requested key, first match wins per key. A
   * key is matched case-insensitively and returned as it was asked. The asset's criticality and
   * category, when XTM One holds them, are its facts.
   */
  Map<String, Counterpart> match(Collection<String> keys, JsonNode body) {
    Map<String, String> asked = new HashMap<>();
    keys.forEach(k -> asked.putIfAbsent(k.toLowerCase(Locale.ROOT), k));
    Map<String, Counterpart> found = new HashMap<>();
    for (JsonNode node : body.path("items")) {
      String answered = node.path("key").asText(null);
      String id = node.path("id").asText(null);
      String url = node.path("url").asText(null);
      if (answered == null || id == null || url == null) {
        continue;
      }
      String key = asked.get(answered.toLowerCase(Locale.ROOT));
      if (key == null || found.containsKey(key)) {
        continue;
      }
      List<Counterpart.Fact> facts = new ArrayList<>();
      fact(node, "criticality").ifPresent(v -> facts.add(new Counterpart.Fact("criticality", v)));
      fact(node, "category").ifPresent(v -> facts.add(new Counterpart.Fact("category", v)));
      found.put(
          key,
          new Counterpart(
              key, PRODUCT, id, "asset", node.path("name").asText(answered), url, facts));
    }
    return found;
  }

  private static Optional<String> fact(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isTextual() && !value.asText().isBlank()) {
      return Optional.of(value.asText());
    }
    return Optional.empty();
  }
}
