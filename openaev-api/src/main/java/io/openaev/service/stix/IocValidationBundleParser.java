package io.openaev.service.stix;

import static io.openaev.database.model.IocValidation.MAX_INDICATORS;
import static io.openaev.database.model.IocValidation.MAX_PLATFORMS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.service.stix.error.BundleValidationError;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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

  /**
   * Checked before the bundle is parsed, so an oversized payload is never materialized. OpenCTI
   * sends at most 2,211 objects (see {@link #MAX_OBJECTS}); 16 Mi characters leave room for long
   * indicator descriptions.
   */
  static final int MAX_BUNDLE_LENGTH = 16 * 1024 * 1024;

  /** OpenCTI sends one pair per (indicator, security platform) couple. */
  static final int MAX_PAIRS = MAX_INDICATORS * MAX_PLATFORMS;

  /** The request, its indicators, its security platforms and one deployment per pair. */
  static final int MAX_OBJECTS = 1 + MAX_INDICATORS + MAX_PLATFORMS + MAX_PAIRS;

  static final String INDICATOR_TYPE = "indicator";
  static final String IDENTITY_TYPE = "identity";
  static final String RELATIONSHIP_TYPE = "relationship";
  // OpenCTI generates every id it sends as a lower-case RFC 4122 UUID of version 4 (random) or 5
  // (name-based); the result bundle writes the refs back as STIX identifiers OpenCTI resolves
  private static final Pattern OPENCTI_UUID =
      Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[45][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

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
    JsonNode objectsNode = bundle.get("objects");
    if (objectsNode.size() > MAX_OBJECTS) {
      throw new BundleValidationError(
          "An IOC validation bundle is limited to %d objects, found %d"
              .formatted(MAX_OBJECTS, objectsNode.size()));
    }
    List<JsonNode> objects = StreamSupport.stream(objectsNode.spliterator(), false).toList();
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
    checkLimits(iocs, pairs, platformNames);

    return new IocValidationRequest(
        requestId,
        name,
        blankToNull(text(request, "description")),
        truncate(blankToNull(text(request, "requested_by")), MAX_NAME_LENGTH),
        parseTestKinds(request.path("test_kinds")),
        iocs,
        pairs,
        platformNames,
        createdByIdOfType(objects, RELATIONSHIP_TYPE));
  }

  /**
   * The STIX {@code created} timestamp of each object of the type: the result bundle sends it back
   * unchanged with the deployments it updates. An object without a readable one is left out.
   */
  private static Map<String, Instant> createdByIdOfType(List<JsonNode> objects, String type) {
    Map<String, Instant> created = new LinkedHashMap<>();
    objects.stream()
        .filter(object -> type.equals(text(object, "type")))
        .forEach(
            object -> {
              String id = text(object, "id");
              String timestamp = text(object, "created");
              if (id != null && timestamp != null) {
                try {
                  created.put(id, Instant.parse(timestamp));
                } catch (DateTimeParseException e) {
                  // Not a STIX timestamp: the result bundle cannot send it back
                }
              }
            });
    return created;
  }

  private JsonNode readBundle(String stixJson) throws BundleValidationError {
    if (stixJson != null && stixJson.length() > MAX_BUNDLE_LENGTH) {
      throw new BundleValidationError(
          "An IOC validation bundle is limited to %d characters, found %d"
              .formatted(MAX_BUNDLE_LENGTH, stixJson.length()));
    }
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

  /**
   * The OpenCTI id of the request, from the event or the OpenCTI extension of the request object.
   * When both are present they must be equal: the lifecycle reported back to OpenCTI must target
   * the request whose IOCs are executed. An id that is not an OpenCTI internal id (a version 4 or 5
   * UUID in lower case) makes the bundle malformed: it is stored, put in the link to the request in
   * OpenCTI and sent back with every status.
   */
  static String resolveRequestId(JsonNode request, String entityId) throws BundleValidationError {
    String eventId = requestIdOrNull(entityId);
    String extensionId =
        requestIdOrNull(text(request.path("extensions").path(OPENCTI_EXTENSION), "id"));
    if (eventId != null && extensionId != null && !eventId.equals(extensionId)) {
      throw new BundleValidationError(
          "The event targets request %s but the bundle carries request %s"
              .formatted(eventId, extensionId));
    }
    if (eventId != null) {
      return eventId;
    }
    if (extensionId != null) {
      return extensionId;
    }
    throw new BundleValidationError(
        "The IOC validation request carries no OpenCTI id (event.entity_id or extension id)");
  }

  private static String requestIdOrNull(String id) throws BundleValidationError {
    if (id == null || id.isBlank()) {
      return null;
    }
    String trimmed = id.trim();
    if (!OPENCTI_UUID.matcher(trimmed).matches()) {
      throw new BundleValidationError(
          ("The OpenCTI id of the IOC validation request must be a lower-case version 4 or 5"
                  + " UUID, found '%s'")
              .formatted(IocValidationPlanner.display(trimmed)));
    }
    return trimmed;
  }

  private static List<IocValidationRequest.Ioc> parseIocs(
      JsonNode iocsNode, Map<String, String> indicatorNames) throws BundleValidationError {
    if (!iocsNode.isArray()) {
      throw new BundleValidationError("The IOC validation request has no iocs array");
    }
    if (iocsNode.size() > MAX_INDICATORS) {
      throw new BundleValidationError(
          "An IOC validation request is limited to %d IOCs, found %d"
              .formatted(MAX_INDICATORS, iocsNode.size()));
    }
    List<IocValidationRequest.Ioc> iocs = new ArrayList<>();
    // One test per indicator, the first IOC given for it, as OpenCTI sends them: further entries of
    // an indicator are ignored, so a request never runs more tests than it has indicators
    Set<String> seenIndicators = new LinkedHashSet<>();
    for (JsonNode node : iocsNode) {
      String indicatorRef = requiredStixId(node, "indicator_ref", INDICATOR_TYPE);
      if (!seenIndicators.add(indicatorRef)) {
        continue;
      }
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
    Iterator<Map.Entry<String, JsonNode>> fields = hashesNode.fields();
    while (hashes.size() < MAX_HASHES && fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      if (entry.getValue().isTextual() && !entry.getValue().asText().isBlank()) {
        hashes.put(
            truncate(entry.getKey(), MAX_NAME_LENGTH),
            truncate(entry.getValue().asText().trim(), MAX_NAME_LENGTH));
      }
    }
    return hashes;
  }

  private static List<IocValidationRequest.Pair> parsePairs(JsonNode pairsNode)
      throws BundleValidationError {
    if (!pairsNode.isArray()) {
      throw new BundleValidationError("The IOC validation request has no pairs array");
    }
    if (pairsNode.size() > MAX_PAIRS) {
      throw new BundleValidationError(
          "An IOC validation request is limited to %d (indicator, security platform) pairs, found %d"
              .formatted(MAX_PAIRS, pairsNode.size()));
    }
    List<IocValidationRequest.Pair> pairs = new ArrayList<>();
    Set<String> seenDeployments = new LinkedHashSet<>();
    // One deployed-on per (indicator, platform): this keeps the pairs within the indicator and
    // platform limits, whatever the number of deployed_on_ref values sent
    Set<String> seenCouples = new LinkedHashSet<>();
    for (JsonNode node : pairsNode) {
      IocValidationRequest.Pair pair =
          new IocValidationRequest.Pair(
              requiredStixId(node, "indicator_ref", INDICATOR_TYPE),
              requiredStixId(node, "platform_ref", IDENTITY_TYPE),
              requiredStixId(node, "deployed_on_ref", RELATIONSHIP_TYPE));
      String couple = pair.indicatorRef() + "|" + pair.platformRef();
      if (!seenDeployments.contains(pair.deployedOnRef()) && seenCouples.add(couple)) {
        seenDeployments.add(pair.deployedOnRef());
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
      List<IocValidationRequest.Ioc> iocs,
      List<IocValidationRequest.Pair> pairs,
      Map<String, String> platformNames)
      throws BundleValidationError {
    // OpenCTI builds every pair from an IOC of the request and sends the named identity of every
    // paired security platform, the only way a platform is matched here: a pair without either
    // could never be tested and is a malformed request
    Set<String> iocIndicators = new LinkedHashSet<>();
    iocs.forEach(ioc -> iocIndicators.add(ioc.indicatorRef()));
    for (IocValidationRequest.Pair pair : pairs) {
      if (!iocIndicators.contains(pair.indicatorRef())) {
        throw new BundleValidationError(
            "The IOC validation request pairs indicator %s, which has no IOC"
                .formatted(pair.indicatorRef()));
      }
      if (!platformNames.containsKey(pair.platformRef())) {
        throw new BundleValidationError(
            "The IOC validation request pairs security platform %s, which has no named identity"
                .formatted(pair.platformRef()));
      }
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

  /**
   * A required STIX identifier of the given object type, as OpenCTI generates it: {@code
   * <type>--<UUID>} with a lower-case version 4 or 5 UUID. Anything else is a malformed request:
   * the refs are stored and written back in the result bundle, which OpenCTI would refuse on every
   * retry.
   */
  private static String requiredStixId(JsonNode node, String field, String type)
      throws BundleValidationError {
    String id = required(node, field);
    if (!isStixId(id, type)) {
      throw new BundleValidationError(
          ("The IOC validation request field '%s' must be a STIX identifier %s--<UUID>, with a"
                  + " lower-case version 4 or 5 UUID, found '%s'")
              .formatted(field, type, IocValidationPlanner.display(id)));
    }
    return id;
  }

  static boolean isStixId(String id, String type) {
    String prefix = type + "--";
    return id != null
        && id.startsWith(prefix)
        && OPENCTI_UUID.matcher(id.substring(prefix.length())).matches();
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
