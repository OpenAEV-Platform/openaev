package io.openaev.service.stix;

import static io.openaev.database.model.IocValidation.MAX_INDICATORS;
import static io.openaev.database.model.IocValidation.MAX_PLATFORMS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.service.stix.error.BundleValidationError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Parses the bundle OpenCTI sends for an IOC validation request (contract section 4) into an {@link
 * IocValidationRequest}. Every shape violation is a {@link BundleValidationError}: OpenCTI cannot
 * fix a malformed request by retrying, so the work is acknowledged in error and not replayed.
 */
@Component
@RequiredArgsConstructor
public class IocValidationBundleParser {

  public static final String REQUEST_TYPE = "x-opencti-ioc-validation-request";
  public static final String OPENCTI_EXTENSION =
      "extension-definition--ea279b3e-5c71-4632-ac08-831c66a786ba";
  static final int MAX_NAME_LENGTH = 255;
  static final int MAX_VALUE_LENGTH = 8192;
  static final int MAX_HASHES = 10;

  private final ObjectMapper mapper;

  /**
   * Parses and validates the bundle.
   *
   * @param stixJson the {@code stix_objects} JSON string of the CTI event
   * @param entityId the {@code event.entity_id} of the CTI event (the request internal id), may be
   *     null for older senders, in which case the request object's OpenCTI extension id is used
   * @throws BundleValidationError when the bundle does not follow the contract
   */
  public IocValidationRequest parse(String stixJson, String entityId) throws BundleValidationError {
    JsonNode bundle = readBundle(stixJson);
    List<JsonNode> objects =
        StreamSupport.stream(bundle.get("objects").spliterator(), false).toList();
    List<JsonNode> requests =
        objects.stream().filter(object -> REQUEST_TYPE.equals(text(object, "type"))).toList();
    if (requests.size() != 1) {
      throw new BundleValidationError(
          "An IOC validation bundle must contain exactly one %s object, found %d"
              .formatted(REQUEST_TYPE, requests.size()));
    }
    JsonNode request = requests.getFirst();

    String requestId = resolveRequestId(request, entityId);
    String name = truncate(required(request, "name"), MAX_NAME_LENGTH);
    Map<String, String> indicatorNames = namesByIdOfType(objects, "indicator");
    Map<String, String> platformNames = namesByIdOfType(objects, "identity");

    List<IocValidationRequest.Ioc> iocs = parseIocs(request.path("iocs"), indicatorNames);
    List<IocValidationRequest.Pair> pairs = parsePairs(request.path("pairs"));
    checkLimits(iocs, pairs);

    return new IocValidationRequest(
        requestId,
        name,
        blankToNull(text(request, "description")),
        truncate(blankToNull(text(request, "requested_by")), MAX_NAME_LENGTH),
        parseTestKinds(request.path("test_kinds")),
        iocs,
        pairs,
        platformNames);
  }

  private JsonNode readBundle(String stixJson) throws BundleValidationError {
    JsonNode bundle;
    try {
      bundle = stixJson == null ? null : mapper.readTree(stixJson);
    } catch (JsonProcessingException e) {
      throw new BundleValidationError("Invalid STIX bundle: the payload is not valid JSON");
    }
    if (bundle == null
        || !"bundle".equals(text(bundle, "type"))
        || !bundle.path("objects").isArray()) {
      throw new BundleValidationError("Invalid STIX bundle: a bundle with objects is required");
    }
    return bundle;
  }

  private static String resolveRequestId(JsonNode request, String entityId)
      throws BundleValidationError {
    if (entityId != null && !entityId.isBlank()) {
      return entityId.trim();
    }
    String extensionId = text(request.path("extensions").path(OPENCTI_EXTENSION), "id");
    if (extensionId != null && !extensionId.isBlank()) {
      return extensionId.trim();
    }
    throw new BundleValidationError(
        "The IOC validation request carries no OpenCTI id (event.entity_id or extension id)");
  }

  private static List<IocValidationRequest.Ioc> parseIocs(
      JsonNode iocsNode, Map<String, String> indicatorNames) throws BundleValidationError {
    if (!iocsNode.isArray()) {
      throw new BundleValidationError("The IOC validation request has no iocs array");
    }
    List<IocValidationRequest.Ioc> iocs = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (JsonNode node : iocsNode) {
      String indicatorRef = required(node, "indicator_ref");
      String observableType = required(node, "observable_type");
      String value = required(node, "value");
      if (value.length() > MAX_VALUE_LENGTH) {
        throw new BundleValidationError(
            "The IOC value of indicator %s exceeds %d characters"
                .formatted(indicatorRef, MAX_VALUE_LENGTH));
      }
      String testKindValue = required(node, "test_kind");
      IocValidationTestKind testKind =
          IocValidationTestKind.fromStix(testKindValue)
              .orElseThrow(
                  () ->
                      new BundleValidationError(
                          "Unknown IOC validation test kind '%s' for indicator %s"
                              .formatted(testKindValue, indicatorRef)));
      if (!seen.add(indicatorRef + "|" + testKind)) {
        continue;
      }
      iocs.add(
          new IocValidationRequest.Ioc(
              indicatorRef,
              indicatorNames.get(indicatorRef),
              observableType,
              value,
              testKind,
              truncate(blankToNull(text(node, "file_name")), MAX_NAME_LENGTH),
              parseHashes(node.path("hashes"))));
    }
    if (iocs.isEmpty()) {
      throw new BundleValidationError("The IOC validation request contains no IOC");
    }
    return iocs;
  }

  private static Map<String, String> parseHashes(JsonNode hashesNode) {
    Map<String, String> hashes = new LinkedHashMap<>();
    if (!hashesNode.isObject()) {
      return hashes;
    }
    hashesNode
        .fields()
        .forEachRemaining(
            entry -> {
              if (hashes.size() < MAX_HASHES
                  && entry.getValue().isTextual()
                  && !entry.getValue().asText().isBlank()) {
                hashes.put(
                    truncate(entry.getKey(), MAX_NAME_LENGTH),
                    truncate(entry.getValue().asText().trim(), MAX_NAME_LENGTH));
              }
            });
    return hashes;
  }

  private static List<IocValidationRequest.Pair> parsePairs(JsonNode pairsNode)
      throws BundleValidationError {
    if (!pairsNode.isArray()) {
      throw new BundleValidationError("The IOC validation request has no pairs array");
    }
    List<IocValidationRequest.Pair> pairs = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (JsonNode node : pairsNode) {
      IocValidationRequest.Pair pair =
          new IocValidationRequest.Pair(
              required(node, "indicator_ref"),
              required(node, "platform_ref"),
              required(node, "deployed_on_ref"));
      if (seen.add(pair.deployedOnRef())) {
        pairs.add(pair);
      }
    }
    if (pairs.isEmpty()) {
      throw new BundleValidationError(
          "The IOC validation request contains no (indicator, security platform) pair");
    }
    return pairs;
  }

  private static void checkLimits(
      List<IocValidationRequest.Ioc> iocs, List<IocValidationRequest.Pair> pairs)
      throws BundleValidationError {
    Set<String> indicators = new LinkedHashSet<>();
    iocs.forEach(ioc -> indicators.add(ioc.indicatorRef()));
    pairs.forEach(pair -> indicators.add(pair.indicatorRef()));
    if (indicators.size() > MAX_INDICATORS) {
      throw new BundleValidationError(
          "An IOC validation request is limited to %d indicators, found %d"
              .formatted(MAX_INDICATORS, indicators.size()));
    }
    long platforms = pairs.stream().map(IocValidationRequest.Pair::platformRef).distinct().count();
    if (platforms > MAX_PLATFORMS) {
      throw new BundleValidationError(
          "An IOC validation request is limited to %d security platforms, found %d"
              .formatted(MAX_PLATFORMS, platforms));
    }
  }

  /** Requested test kinds; values this version does not know are ignored, not rejected. */
  private static List<IocValidationTestKind> parseTestKinds(JsonNode testKindsNode) {
    Set<IocValidationTestKind> testKinds = new LinkedHashSet<>();
    if (testKindsNode.isArray()) {
      testKindsNode.forEach(
          node -> IocValidationTestKind.fromStix(node.asText()).ifPresent(testKinds::add));
    }
    return new ArrayList<>(testKinds);
  }

  private static Map<String, String> namesByIdOfType(List<JsonNode> objects, String type) {
    Map<String, String> names = new LinkedHashMap<>();
    objects.stream()
        .filter(object -> type.equals(text(object, "type")))
        .forEach(
            object -> {
              String id = text(object, "id");
              String name = blankToNull(text(object, "name"));
              if (id != null && name != null) {
                names.put(id, truncate(name, MAX_NAME_LENGTH));
              }
            });
    return names;
  }

  private static String required(JsonNode node, String field) throws BundleValidationError {
    String value = blankToNull(text(node, field));
    if (value == null) {
      throw new BundleValidationError(
          "The IOC validation request is missing the required field '%s'".formatted(field));
    }
    return value.trim();
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String truncate(String value, int maxLength) {
    return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
