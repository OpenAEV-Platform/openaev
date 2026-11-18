package io.openaev.opencti.counterpart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.opencti.client.OpenCTIClient;
import io.openaev.opencti.client.response.Response;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.xtm.counterpart.Counterpart;
import io.openaev.xtm.counterpart.CounterpartKind;
import io.openaev.xtm.counterpart.CounterpartProvider;
import io.openaev.xtm.counterpart.CounterpartUnavailableException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Finds our objects in the OpenCTI the tenant is connected to (openaev.xtm.opencti.{tenant}.*),
 * with the token of that connection, in one GraphQL call per kind and batch.
 */
@Component
@RequiredArgsConstructor
public class OpenCTICounterpartProvider implements CounterpartProvider {

  public static final String PRODUCT = "opencti";

  static final String QUERY =
      """
      query XtmCounterparts($first: Int, $filters: FilterGroup) {
        stixCoreObjects(first: $first, filters: $filters) {
          edges {
            node {
              id
              entity_type
              standard_id
              representative { main }
              ... on AttackPattern { x_mitre_id }
              ... on Vulnerability { name }
              ... on System { name }
              ... on StixCyberObservable { observable_value }
            }
          }
        }
      }
      """;

  static final List<String> ASSET_OBSERVABLE_TYPES =
      List.of("Hostname", "IPv4-Addr", "IPv6-Addr", "Mac-Addr");

  private final XtmConfig xtmConfig;
  private final OpenCTIClient openCTIClient;
  private final ObjectMapper mapper;

  @Override
  public String product() {
    return PRODUCT;
  }

  @Override
  public Optional<String> connectedUrl(String tenantId) {
    return config(tenantId).map(OpenCTIConfig::getFormattedUrl);
  }

  @Override
  public boolean supports(CounterpartKind kind) {
    return true;
  }

  @Override
  public Map<String, Counterpart> resolve(
      String tenantId, CounterpartKind kind, Collection<String> keys)
      throws CounterpartUnavailableException {
    OpenCTIConfig config =
        config(tenantId)
            .orElseThrow(() -> new CounterpartUnavailableException("OpenCTI is not connected"));
    if (keys.isEmpty()) {
      return Map.of();
    }
    Response response;
    try {
      response =
          openCTIClient.execute(
              config.getApiUrl(), config.getToken(), QUERY, variables(kind, keys));
    } catch (IOException e) {
      throw new CounterpartUnavailableException("OpenCTI cannot be reached", e);
    }
    if (response.isError() || response.getData() == null) {
      throw new CounterpartUnavailableException(
          "OpenCTI refused the counterpart lookup (status %d)".formatted(response.getStatus()));
    }
    return match(kind, keys, response.getData(), config.getFormattedUrl());
  }

  /** The filters of the lookup: one exact match on the identifier the kind is keyed by. */
  ObjectNode variables(CounterpartKind kind, Collection<String> keys) {
    ObjectNode vars = mapper.createObjectNode();
    // An asset key can match a System and an observable at once; keep room for both.
    vars.put("first", kind == CounterpartKind.ASSET ? keys.size() * 4 : keys.size() * 2);
    ObjectNode root = vars.putObject("filters");
    switch (kind) {
      case ATTACK_PATTERN -> group(root, "and", List.of("Attack-Pattern"), "x_mitre_id", keys);
      case VULNERABILITY -> group(root, "and", List.of("Vulnerability"), "name", keys);
      case THREAT -> {
        root.put("mode", "and");
        ArrayNode filters = root.putArray("filters");
        filter(filters, "ids", keys);
        root.putArray("filterGroups");
      }
      case ASSET -> {
        root.put("mode", "or");
        root.putArray("filters");
        ArrayNode groups = root.putArray("filterGroups");
        group(groups.addObject(), "and", List.of("System"), "name", keys);
        group(groups.addObject(), "and", ASSET_OBSERVABLE_TYPES, "value", keys);
      }
    }
    return vars;
  }

  /** Keeps the nodes whose identifier is one of the keys, first match wins per key. */
  Map<String, Counterpart> match(
      CounterpartKind kind, Collection<String> keys, JsonNode data, String baseUrl) {
    Map<String, Counterpart> found = new HashMap<>();
    JsonNode edges = data.path("stixCoreObjects").path("edges");
    for (JsonNode edge : edges) {
      JsonNode node = edge.path("node");
      String id = node.path("id").asText(null);
      if (id == null) {
        continue;
      }
      for (String candidate : identifiers(kind, node)) {
        String key = kind.normalise(candidate);
        if (key != null && keys.contains(key) && !found.containsKey(key)) {
          found.put(
              key,
              new Counterpart(
                  key,
                  PRODUCT,
                  id,
                  node.path("entity_type").asText(),
                  node.path("representative").path("main").asText(candidate),
                  baseUrl + "dashboard/id/" + id));
        }
      }
    }
    return found;
  }

  private static List<String> identifiers(CounterpartKind kind, JsonNode node) {
    return switch (kind) {
      case ATTACK_PATTERN -> List.of(node.path("x_mitre_id").asText(""));
      case VULNERABILITY -> List.of(node.path("name").asText(""));
      case ASSET ->
          List.of(node.path("name").asText(""), node.path("observable_value").asText(""));
      case THREAT -> List.of(node.path("id").asText(""), node.path("standard_id").asText(""));
    };
  }

  private Optional<OpenCTIConfig> config(String tenantId) {
    if (tenantId == null || xtmConfig.getOpencti() == null) {
      return Optional.empty();
    }
    OpenCTIConfig config = xtmConfig.getOpencti().get(tenantId);
    if (config == null
        || !Boolean.TRUE.equals(config.getEnable())
        || StringUtils.isAnyBlank(config.getUrl(), config.getToken())) {
      return Optional.empty();
    }
    return Optional.of(config);
  }

  private static void group(
      ObjectNode node, String mode, List<String> types, String key, Collection<String> values) {
    node.put("mode", mode);
    ArrayNode filters = node.putArray("filters");
    filter(filters, "entity_type", types);
    filter(filters, key, values);
    node.putArray("filterGroups");
  }

  private static void filter(ArrayNode filters, String key, Collection<String> values) {
    ObjectNode f = filters.addObject();
    f.putArray("key").add(key);
    ArrayNode v = f.putArray("values");
    values.forEach(v::add);
    f.put("operator", "eq");
    f.put("mode", "or");
  }
}
