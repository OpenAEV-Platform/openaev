package io.openaev.service.stix;

import static io.openaev.database.model.IocValidation.MAX_INDICATORS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.service.stix.error.BundleValidationError;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("IOC validation request bundle parser")
class IocValidationBundleParserTest {

  private static final String REQUEST_ID = "5c7f0a2e-1111-4a2b-9c3d-123456789abc";
  private static final String INDICATOR = "indicator--6d2f6bb1-31b1-4b8a-9d36-3b3b3a1f0e11";
  private static final String PLATFORM = "identity--1e2f6bb1-31b1-4b8a-9d36-3b3b3a1f0e22";
  private static final String DEPLOYED_ON = "relationship--9b1d3f5a-3333-4c4d-9e5f-fedcbafedcba";

  private final ObjectMapper mapper = new ObjectMapper();
  private IocValidationBundleParser parser;

  @BeforeEach
  void setUp() {
    parser = new IocValidationBundleParser(mapper);
  }

  private ObjectNode request() {
    ObjectNode request = mapper.createObjectNode();
    request.put("type", IocValidationBundleParser.REQUEST_TYPE);
    request.put("id", IocValidationBundleParser.REQUEST_TYPE + "--" + REQUEST_ID);
    request.put("name", "Validation of evil.example.com");
    request.put("requested_by", "Analyst");
    request.putArray("test_kinds").add("dns_resolution").add("unknown_future_kind");
    ObjectNode ioc = request.putArray("iocs").addObject();
    ioc.put("indicator_ref", INDICATOR);
    ioc.put("observable_type", "Domain-Name");
    ioc.put("value", "evil.example.com");
    ioc.put("test_kind", "dns_resolution");
    ObjectNode pair = request.putArray("pairs").addObject();
    pair.put("indicator_ref", INDICATOR);
    pair.put("platform_ref", PLATFORM);
    pair.put("deployed_on_ref", DEPLOYED_ON);
    request
        .putObject("extensions")
        .putObject(IocValidationBundleParser.OPENCTI_EXTENSION)
        .put("id", REQUEST_ID);
    return request;
  }

  private String bundle(ObjectNode... objects) {
    ObjectNode bundle = mapper.createObjectNode();
    bundle.put("type", "bundle");
    bundle.put("id", "bundle--1b1d3f5a-3333-4c4d-9e5f-fedcbafedcba");
    ArrayNode array = bundle.putArray("objects");
    for (ObjectNode object : objects) {
      array.add(object);
    }
    ObjectNode platform = mapper.createObjectNode();
    platform.put("type", "identity");
    platform.put("id", PLATFORM);
    platform.put("name", "Microsoft Defender");
    array.add(platform);
    return bundle.toString();
  }

  @Test
  @DisplayName("parses the request, its IOCs, pairs and platform names")
  void given_validBundle_should_parseRequest() throws BundleValidationError {
    IocValidationRequest parsed = parser.parse(bundle(request()), REQUEST_ID);

    assertThat(parsed.requestId()).isEqualTo(REQUEST_ID);
    assertThat(parsed.name()).isEqualTo("Validation of evil.example.com");
    assertThat(parsed.requestedBy()).isEqualTo("Analyst");
    assertThat(parsed.testKinds()).containsExactly(IocValidationTestKind.DNS_RESOLUTION);
    assertThat(parsed.iocs())
        .singleElement()
        .satisfies(
            ioc -> {
              assertThat(ioc.indicatorRef()).isEqualTo(INDICATOR);
              assertThat(ioc.testKind()).isEqualTo(IocValidationTestKind.DNS_RESOLUTION);
              assertThat(ioc.value()).isEqualTo("evil.example.com");
            });
    assertThat(parsed.pairs())
        .containsExactly(new IocValidationRequest.Pair(INDICATOR, PLATFORM, DEPLOYED_ON));
    assertThat(parsed.platformNamesByRef()).containsEntry(PLATFORM, "Microsoft Defender");
  }

  @Test
  @DisplayName("falls back to the OpenCTI extension id when the event carries no entity id")
  void given_noEntityId_should_useExtensionId() throws BundleValidationError {
    assertThat(parser.parse(bundle(request()), null).requestId()).isEqualTo(REQUEST_ID);
  }

  @Test
  @DisplayName("rejects an event targeting another request than the one the bundle carries")
  void given_mismatchedRequestIds_should_throw() {
    assertThatThrownBy(
            () -> parser.parse(bundle(request()), "6d2f6bb1-31b1-4b8a-9d36-000000000000"))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("bundle carries request " + REQUEST_ID);
  }

  @Test
  @DisplayName("uses the event entity id when the request carries no OpenCTI extension id")
  void given_noExtensionId_should_useEntityId() throws BundleValidationError {
    ObjectNode request = request();
    request.remove("extensions");

    assertThat(parser.parse(bundle(request), REQUEST_ID).requestId()).isEqualTo(REQUEST_ID);
  }

  /** Values that are not a lower-case version 4 or 5 UUID, the only ids OpenCTI generates. */
  private static Stream<String> malformedUuids() {
    return Stream.of(
        "not-a-uuid",
        "../../dashboard/settings",
        "5c7f0a2e-1111-4a2b-9c3d-123456789abc/../x",
        "5C7F0A2E-1111-4A2B-9C3D-123456789ABC",
        "5c7f0a2e-1111-1a2b-9c3d-123456789abc",
        "5c7f0a2e-1111-3a2b-9c3d-123456789abc",
        "5c7f0a2e-1111-4a2b-cc3d-123456789abc",
        "5c7f0a2e11114a2b9c3d123456789abc",
        "5c7f0a2e-1111-4a2b-9c3d-123456789abcd",
        "5c7f0a2e-1111-4a2b-9c3d-123456789ab\u0441",
        "5c7f0a2e-1111-4a2b-9c3d-123456789abc'",
        "r".repeat(256));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("malformedUuids")
  @DisplayName("rejects a request id that is not an OpenCTI id, from the event or the bundle")
  void given_malformedRequestId_should_throw(String malformed) {
    ObjectNode withoutExtension = request();
    withoutExtension.remove("extensions");
    ObjectNode malformedExtension = request();
    ((ObjectNode)
            malformedExtension.path("extensions").path(IocValidationBundleParser.OPENCTI_EXTENSION))
        .put("id", malformed);

    assertThatThrownBy(() -> parser.parse(bundle(withoutExtension), malformed))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("version 4 or 5 UUID");
    assertThatThrownBy(() -> parser.parse(bundle(malformedExtension), null))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("version 4 or 5 UUID");
  }

  @Test
  @DisplayName("accepts the name-based ids OpenCTI generates for its objects")
  void given_version5Ids_should_parseRequest() throws BundleValidationError {
    String indicator = "indicator--2ee671b4-b671-5c38-9e29-72e52d135ece";
    String platform = "identity--b2a8fbec-b4fb-563c-a052-7b5b4ab23070";
    String deployedOn = "relationship--6ed368e7-88c7-5b2e-a495-1702f14cbc2e";
    ObjectNode request = request();
    ((ObjectNode) request.get("iocs").get(0)).put("indicator_ref", indicator);
    ((ObjectNode) request.get("pairs").get(0))
        .put("indicator_ref", indicator)
        .put("platform_ref", platform)
        .put("deployed_on_ref", deployedOn);
    ObjectNode identity = mapper.createObjectNode();
    identity.put("type", "identity");
    identity.put("id", platform);
    identity.put("name", "CrowdStrike Falcon");

    IocValidationRequest parsed = parser.parse(bundle(request, identity), REQUEST_ID);

    assertThat(parsed.pairs())
        .containsExactly(new IocValidationRequest.Pair(indicator, platform, deployedOn));
  }

  /**
   * Values that are not a STIX identifier of the type: the id of another object type, a malformed
   * prefix or UUID, or a look-alike character.
   */
  private static Stream<String> notStixIdsOf(String type, String idOfAnotherType) {
    String uuid = "5c7f0a2e-1111-4a2b-9c3d-123456789abc";
    return Stream.concat(
        Stream.of(
            idOfAnotherType,
            type,
            type + "--",
            type + "-" + uuid,
            type.toUpperCase(Locale.ROOT) + "--" + uuid,
            "x-" + type + "--" + uuid,
            type.replace('i', '\u0456') + "--" + uuid,
            " " + type + "--" + uuid + " " + type + "--" + uuid),
        malformedUuids().map(malformed -> type + "--" + malformed));
  }

  private static Stream<Arguments> malformedPairRefs() {
    return Stream.of(
            Map.entry("indicator_ref", notStixIdsOf("indicator", PLATFORM)),
            Map.entry("platform_ref", notStixIdsOf("identity", INDICATOR)),
            Map.entry(
                "deployed_on_ref",
                notStixIdsOf("relationship", "sighting--9b1d3f5a-3333-4c4d-9e5f-fedcbafedcba")))
        .flatMap(entry -> entry.getValue().map(value -> Arguments.of(entry.getKey(), value)));
  }

  private static Stream<String> malformedIndicatorRefs() {
    return notStixIdsOf("indicator", DEPLOYED_ON);
  }

  @ParameterizedTest(name = "pair {0} = {1}")
  @MethodSource("malformedPairRefs")
  @DisplayName("rejects a pair ref that is not a STIX identifier of its object type")
  void given_malformedPairRef_should_throw(String field, String value) {
    ObjectNode request = request();
    ((ObjectNode) request.get("pairs").get(0)).put(field, value);

    assertThatThrownBy(() -> parser.parse(bundle(request), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("'" + field + "' must be a STIX identifier");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("malformedIndicatorRefs")
  @DisplayName("rejects an IOC whose indicator_ref is not a STIX indicator identifier")
  void given_malformedIocIndicatorRef_should_throw(String value) {
    ObjectNode request = request();
    ((ObjectNode) request.get("iocs").get(0)).put("indicator_ref", value);

    assertThatThrownBy(() -> parser.parse(bundle(request), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("'indicator_ref' must be a STIX identifier indicator--<UUID>");
  }

  @Test
  @DisplayName("shows a rejected ref with its invisible characters spelled out")
  void given_refWithLookAlikeCharacter_should_spellItOut() {
    ObjectNode request = request();
    ((ObjectNode) request.get("pairs").get(0))
        .put("platform_ref", "\u0456dentity--1e2f6bb1-31b1-4b8a-9d36-3b3b3a1f0e22");

    assertThatThrownBy(() -> parser.parse(bundle(request), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("<U+0456>dentity--")
        .hasMessageNotContaining("\u0456");
  }

  @Test
  @DisplayName("deduplicates repeated IOCs and pairs")
  void given_duplicates_should_keepOne() throws BundleValidationError {
    ObjectNode request = request();
    ((ArrayNode) request.get("iocs")).add(request.get("iocs").get(0).deepCopy());
    ((ArrayNode) request.get("pairs")).add(request.get("pairs").get(0).deepCopy());
    IocValidationRequest parsed = parser.parse(bundle(request), REQUEST_ID);
    assertThat(parsed.iocs()).hasSize(1);
    assertThat(parsed.pairs()).hasSize(1);
  }

  @Test
  @DisplayName("keeps one IOC per indicator, the first, whatever the other test kinds given for it")
  void given_manyTestKindsForOneIndicator_should_keepTheFirst() throws BundleValidationError {
    ObjectNode request = request();
    ArrayNode iocs = (ArrayNode) request.get("iocs");
    for (String kind : List.of("http_head", "network_traffic", "log_injection", "file_drop")) {
      ObjectNode copy = iocs.get(0).deepCopy();
      copy.put("test_kind", kind);
      iocs.add(copy);
    }
    IocValidationRequest parsed = parser.parse(bundle(request), REQUEST_ID);
    assertThat(parsed.iocs())
        .singleElement()
        .satisfies(
            ioc -> assertThat(ioc.testKind()).isEqualTo(IocValidationTestKind.DNS_RESOLUTION));
  }

  @Test
  @DisplayName("keeps one pair per indicator and platform, whatever its deployed_on_ref")
  void given_sameCoupleWithManyDeployments_should_keepTheFirst() throws BundleValidationError {
    ObjectNode request = request();
    ArrayNode pairs = (ArrayNode) request.get("pairs");
    for (int i = 0; i < 1000; i++) {
      ObjectNode copy = pairs.get(0).deepCopy();
      copy.put("deployed_on_ref", "relationship--9b1d3f5a-3333-4c4d-9e5f-%012d".formatted(i));
      pairs.add(copy);
    }
    IocValidationRequest parsed = parser.parse(bundle(request), REQUEST_ID);
    assertThat(parsed.pairs())
        .containsExactly(new IocValidationRequest.Pair(INDICATOR, PLATFORM, DEPLOYED_ON));
  }

  @Test
  @DisplayName("rejects a bundle without request object")
  void given_noRequest_should_throw() {
    assertThatThrownBy(() -> parser.parse(bundle(), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("exactly one");
  }

  @Test
  @DisplayName("rejects an unknown test kind on an IOC")
  void given_unknownIocKind_should_throw() {
    ObjectNode request = request();
    ((ObjectNode) request.get("iocs").get(0)).put("test_kind", "payload_download");
    assertThatThrownBy(() -> parser.parse(bundle(request), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("payload_download");
  }

  @Test
  @DisplayName("rejects a request without pair or with a missing field")
  void given_incompleteRequest_should_throw() {
    ObjectNode noPairs = request();
    noPairs.putArray("pairs");
    assertThatThrownBy(() -> parser.parse(bundle(noPairs), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class);

    ObjectNode noValue = request();
    ((ObjectNode) noValue.get("iocs").get(0)).remove("value");
    assertThatThrownBy(() -> parser.parse(bundle(noValue), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("value");
  }

  @Test
  @DisplayName("rejects a pair whose indicator has no IOC in the request")
  void given_pairWithoutIoc_should_throw() {
    ObjectNode request = request();
    ((ObjectNode) request.get("pairs").get(0))
        .put("indicator_ref", "indicator--00000000-0000-4000-8000-000000000000");
    assertThatThrownBy(() -> parser.parse(bundle(request), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("has no IOC");
  }

  @Test
  @DisplayName("rejects a pair whose security platform has no named identity in the bundle")
  void given_pairWithoutPlatformIdentity_should_throw() {
    ObjectNode request = request();
    ((ObjectNode) request.get("pairs").get(0))
        .put("platform_ref", "identity--00000000-0000-4000-8000-000000000000");
    assertThatThrownBy(() -> parser.parse(bundle(request), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining("has no named identity");
  }

  @Test
  @DisplayName("rejects an oversized bundle before parsing it")
  void given_oversizedBundle_should_throwBeforeParsing() {
    // Not even JSON: the length is checked first, so the payload is never materialized
    String oversized = "x".repeat(IocValidationBundleParser.MAX_BUNDLE_LENGTH + 1);
    assertThatThrownBy(() -> parser.parse(oversized, REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining(
            "limited to %d characters".formatted(IocValidationBundleParser.MAX_BUNDLE_LENGTH));
  }

  @Test
  @DisplayName("rejects a bundle with more objects than a request can carry")
  void given_tooManyObjects_should_throw() {
    ObjectNode[] objects = new ObjectNode[IocValidationBundleParser.MAX_OBJECTS + 1];
    objects[0] = request();
    for (int i = 1; i < objects.length; i++) {
      objects[i] = mapper.createObjectNode().put("type", "identity").put("id", PLATFORM);
    }
    assertThatThrownBy(() -> parser.parse(bundle(objects), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining(
            "limited to %d objects".formatted(IocValidationBundleParser.MAX_OBJECTS));
  }

  @Test
  @DisplayName("rejects more IOC entries than the indicator limit, duplicates included")
  void given_tooManyIocEntries_should_throw() {
    ObjectNode request = request();
    ArrayNode iocs = (ArrayNode) request.get("iocs");
    JsonNode ioc = iocs.get(0);
    while (iocs.size() <= MAX_INDICATORS) {
      iocs.add(ioc.deepCopy());
    }
    assertThatThrownBy(() -> parser.parse(bundle(request), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining(
            "limited to %d IOCs, found %d".formatted(MAX_INDICATORS, MAX_INDICATORS + 1));
  }

  @Test
  @DisplayName("rejects more pair entries than one per indicator and security platform")
  void given_tooManyPairEntries_should_throw() {
    ObjectNode request = request();
    ArrayNode pairs = (ArrayNode) request.get("pairs");
    JsonNode pair = pairs.get(0);
    while (pairs.size() <= IocValidationBundleParser.MAX_PAIRS) {
      pairs.add(pair.deepCopy());
    }
    assertThatThrownBy(() -> parser.parse(bundle(request), REQUEST_ID))
        .isInstanceOf(BundleValidationError.class)
        .hasMessageContaining(
            "limited to %d (indicator, security platform) pairs"
                .formatted(IocValidationBundleParser.MAX_PAIRS));
  }

  @Test
  @DisplayName("rejects payloads that are not STIX bundles")
  void given_invalidJson_should_throw() {
    assertThatThrownBy(() -> parser.parse("{not json", REQUEST_ID))
        .isInstanceOf(BundleValidationError.class);
    assertThatThrownBy(() -> parser.parse("{\"type\":\"report\"}", REQUEST_ID))
        .isInstanceOf(BundleValidationError.class);
  }
}
