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
    List<JsonNode> objects = StreamSupport.stream(bundle.get("objects").spliterator(), false).toList();
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
      throw new BundleValidationError("The IOC validation request has no iocs array