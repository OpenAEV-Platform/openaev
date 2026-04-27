package io.openaev.api.payload;

import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.utils.constants.Constants.IMPORTED_OBJECT_NAME_SUFFIX;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.Relationship;
import io.openaev.jsonapi.ResourceIdentifier;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.service.ZipJsonService;
import io.openaev.utils.fixtures.DetectionRemediationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Payload api importer tests")
class PayloadApiImporterTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ZipJsonService<Payload> zipJsonService;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private CollectorRepository collectorRepository;

  // -- HELPERS --

  private Map<String, Object> buildDefaultPayloadAttributes() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("payload_type", "Command");
    attributes.put("command_executor", "psh");
    attributes.put("command_content", "echo \"toto\"");
    attributes.put("payload_name", "Echo");
    attributes.put("payload_description", "");
    attributes.put("payload_platforms", new String[] {"Windows"});
    attributes.put("payload_source", "MANUAL");
    attributes.put("payload_expectations", new String[] {"VULNERABILITY"});
    attributes.put("payload_status", "VERIFIED");
    attributes.put("payload_execution_arch", "ALL_ARCHITECTURES");
    return attributes;
  }

  private MockMultipartFile buildZipFile(JsonApiDocument<ResourceObject> document)
      throws Exception {
    byte[] zip = zipJsonService.writeZip(document, emptyMap());
    return new MockMultipartFile("file", "payload.zip", "application/zip", zip);
  }

  private String performImport(MockMultipartFile zipFile) throws Exception {
    return mockMvc
        .perform(multipart(PAYLOAD_URI + "/import").file(zipFile).with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private ResourceObject buildCollectorResource(String collectorResourceId, String collectorType) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("collector_type", collectorType);
    return new ResourceObject(collectorResourceId, "collectors", attributes, emptyMap());
  }

  // -- TESTS --

  @Test
  @DisplayName("Import payload should create injector contract")
  void importPayloadShouldCreateInjectorContract() throws Exception {
    new Manager(List.of(openaevInjectorIntegrationFactory)).monitorIntegrations();

    // -- PREPARE --
    String domainId = "02e33774-33ae-4d65-91fd-a9c0e1a37c7b";
    Map<String, Object> domainAttributes = new HashMap<>();
    domainAttributes.put("domain_id", domainId);
    domainAttributes.put("domain_name", "Data Exfiltration");
    domainAttributes.put("domain_color", "#9933CC");
    domainAttributes.put("domain_created_at", "2026-02-02T14:55:27.442379Z");
    domainAttributes.put("domain_updated_at", "2026-02-02T14:55:27.442379Z");
    ResourceObject domainElement =
        new ResourceObject(domainId, "domains", domainAttributes, emptyMap());

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(
                null,
                "command",
                buildDefaultPayloadAttributes(),
                Map.of(
                    "payload_domains",
                    new Relationship(List.of(new ResourceIdentifier(domainId, "domains"))))),
            List.of(domainElement));

    MockMultipartFile zipFile = buildZipFile(document);

    // -- EXECUTE --
    String response = performImport(zipFile);

    // -- ASSERT --
    assertNotNull(response);

    JsonNode json = objectMapper.readTree(response);
    String payloadId = json.at("/data/attributes/payload_id").asText();
    assertNotNull(payloadId);

    Optional<Payload> payloadPersisted = payloadRepository.findById(payloadId);
    assertFalse(payloadPersisted.isEmpty(), "Payload should have been persisted in the database");

    List<InjectorContract> injectorContracts =
        injectorContractRepository.findInjectorContractsByPayload(payloadPersisted.get());
    assertNotNull(injectorContracts);
    assertEquals(1, injectorContracts.size());
    assertEquals(payloadId, injectorContracts.getFirst().getPayload().getId());
  }

  @Test
  @DisplayName("Import a payload returns complete entity")
  void importPayloadReturnsPayloadWithRelationship() throws Exception {
    // -- PREPARE --
    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(null, "command", buildDefaultPayloadAttributes(), emptyMap()),
            emptyList());

    MockMultipartFile zipFile = buildZipFile(document);

    // -- EXECUTE --
    String response = performImport(zipFile);

    // -- ASSERT --
    assertNotNull(response);

    JsonNode json = objectMapper.readTree(response);

    // Payload
    assertEquals("command", json.at("/data/type").asText());
    assertEquals(
        "Echo" + IMPORTED_OBJECT_NAME_SUFFIX, json.at("/data/attributes/payload_name").asText());
    assertEquals("psh", json.at("/data/attributes/command_executor").asText());
    assertEquals("echo \"toto\"", json.at("/data/attributes/command_content").asText());
  }

  @Test
  @DisplayName("Import a payload returns complete entity with all array fields")
  void importPayloadReturnsPayloadWithAllArrayFields() throws Exception {
    // -- PREPARE --
    // payload_arguments and payload_prerequisites must be arrays of objects,
    // matching the PayloadArgument / PayloadPrerequisite model schema.
    Map<String, Object> argument1 =
        Map.of("type", "text", "key", "target_host", "default_value", "localhost");
    Map<String, Object> argument2 = Map.of("type", "text", "key", "port", "default_value", "8080");
    Map<String, Object> prerequisite1 =
        Map.of("executor", "sh", "get_command", "which curl", "check_command", "curl --version");

    Map<String, Object> attributes = buildDefaultPayloadAttributes();
    attributes.put("payload_arguments", List.of(argument1, argument2));
    attributes.put("payload_prerequisites", List.of(prerequisite1));

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(null, "command", attributes, emptyMap()), emptyList());

    MockMultipartFile zipFile = buildZipFile(document);

    // -- EXECUTE --
    String response = performImport(zipFile);

    // -- ASSERT --
    assertNotNull(response);
    JsonNode json = objectMapper.readTree(response);
    assertEquals("command", json.at("/data/type").asText());
    assertEquals(
        "Echo" + IMPORTED_OBJECT_NAME_SUFFIX, json.at("/data/attributes/payload_name").asText());
    assertEquals("psh", json.at("/data/attributes/command_executor").asText());
    assertEquals("echo \"toto\"", json.at("/data/attributes/command_content").asText());
    assertEquals(1, json.at("/data/attributes/payload_platforms").size());

    // Assert argument field values, not just array size
    JsonNode args = json.at("/data/attributes/payload_arguments");
    assertEquals(2, args.size());
    assertEquals("text", args.get(0).get("type").asText());
    assertEquals("target_host", args.get(0).get("key").asText());
    assertEquals("localhost", args.get(0).get("default_value").asText());
    assertEquals("port", args.get(1).get("key").asText());
    assertEquals("8080", args.get(1).get("default_value").asText());

    // Assert prerequisite field values, not just array size
    JsonNode prereqs = json.at("/data/attributes/payload_prerequisites");
    assertEquals(1, prereqs.size());
    assertEquals("sh", prereqs.get(0).get("executor").asText());
    assertEquals("which curl", prereqs.get(0).get("get_command").asText());
    assertEquals("curl --version", prereqs.get(0).get("check_command").asText());
  }

  @Test
  @DisplayName("Import payload with empty array fields")
  void importPayloadWithEmptyArrayFields() throws Exception {
    // -- PREPARE --
    Map<String, Object> attributes = buildDefaultPayloadAttributes();
    attributes.put("payload_platforms", new String[] {}); // empty array
    attributes.put("payload_arguments", new String[] {}); // empty array
    attributes.put("payload_prerequisites", new String[] {}); // empty array

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(null, "command", attributes, emptyMap()), emptyList());

    MockMultipartFile zipFile = buildZipFile(document);

    // -- EXECUTE --
    String response = performImport(zipFile);

    // -- ASSERT --
    assertNotNull(response);
    JsonNode json = objectMapper.readTree(response);
    assertEquals(0, json.at("/data/attributes/payload_platforms").size());
    assertEquals(0, json.at("/data/attributes/payload_arguments").size());
    assertEquals(0, json.at("/data/attributes/payload_prerequisites").size());
  }

  @Test
  @DisplayName("Import payload with output parser, contract output element and regex group")
  void importPayloadWithOutputParserSucceeds() throws Exception {
    // -- PREPARE --
    String parserId = UUID.randomUUID().toString();
    String elementId = UUID.randomUUID().toString();
    String regexId = UUID.randomUUID().toString();

    ResourceObject regexGroupResource =
        new ResourceObject(
            regexId,
            "regex_groups",
            Map.of("regex_group_field", "Any text", "regex_group_index_values", "$1"),
            null);

    // ContractOutputElement + RegexGroup
    Map<String, Object> elementAttrs = new HashMap<>();
    elementAttrs.put("contract_output_element_rule", "\\d+");
    elementAttrs.put("contract_output_element_key", "IPv6-key");
    elementAttrs.put("contract_output_element_name", "IPv6 Name");
    elementAttrs.put("contract_output_element_type", "ipv6");
    elementAttrs.put("contract_output_element_is_finding", false);
    ResourceObject contractOutputElementResource =
        new ResourceObject(
            elementId,
            "contract_output_elements",
            elementAttrs,
            Map.of(
                "contract_output_element_regex_groups",
                new Relationship(List.of(new ResourceIdentifier(regexId, "regex_groups")))));

    // OutputParser + ContractOutputElement
    ResourceObject outputParserResource =
        new ResourceObject(
            parserId,
            "output_parsers",
            Map.of("output_parser_mode", "STDOUT", "output_parser_type", "REGEX"),
            Map.of(
                "output_parser_contract_output_elements",
                new Relationship(
                    List.of(new ResourceIdentifier(elementId, "contract_output_elements")))));

    // Payload + OutputParser
    Map<String, Object> payloadAttrs = buildDefaultPayloadAttributes();
    payloadAttrs.put("payload_name", "Payload With Output Parser");
    payloadAttrs.put("command_content", "ipconfig");

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(
                null,
                "command",
                payloadAttrs,
                Map.of(
                    "payload_output_parsers",
                    new Relationship(List.of(new ResourceIdentifier(parserId, "output_parsers"))))),
            List.of(outputParserResource, contractOutputElementResource, regexGroupResource));

    MockMultipartFile zipFile = buildZipFile(document);

    // -- EXECUTE --
    String response = performImport(zipFile);

    // -- ASSERT --
    assertNotNull(response);
    JsonNode json = objectMapper.readTree(response);

    assertEquals("command", json.at("/data/type").asText());
    assertEquals(
        "Payload With Output Parser" + IMPORTED_OBJECT_NAME_SUFFIX,
        json.at("/data/attributes/payload_name").asText());

    // Output parser must be referenced in the payload's relationships
    JsonNode outputParserRel = json.at("/data/relationships/payload_output_parsers/data");
    assertEquals(1, outputParserRel.size());
    assertEquals("output_parsers", outputParserRel.get(0).get("type").asText());

    // All cascade chain entities must appear in the included array
    JsonNode included = json.at("/included");
    assertTrue(included.isArray());
    long outputParserCount = 0;
    long contractOutputElementCount = 0;
    long regexGroupCount = 0;
    for (JsonNode node : included) {
      switch (node.get("type").asText()) {
        case "output_parsers" -> {
          outputParserCount++;
          assertEquals("STDOUT", node.at("/attributes/output_parser_mode").asText());
          assertEquals("REGEX", node.at("/attributes/output_parser_type").asText());
        }
        case "contract_output_elements" -> {
          contractOutputElementCount++;
          assertEquals("IPv6-key", node.at("/attributes/contract_output_element_key").asText());
        }
        case "regex_groups" -> {
          regexGroupCount++;
          assertEquals("Any text", node.at("/attributes/regex_group_field").asText());
          assertEquals("$1", node.at("/attributes/regex_group_index_values").asText());
        }
        default -> {
          // other relationship types (tags, attack patterns, etc.) are ignored
        }
      }
    }
    assertEquals(1, outputParserCount, "Expected 1 output parser in included");
    assertEquals(1, contractOutputElementCount, "Expected 1 contract output element in included");
    assertEquals(1, regexGroupCount, "Expected 1 regex group in included");
  }

  @Test
  @DisplayName("Import payload with multiple contract output elements and regex groups")
  void importPayloadWithMultipleContractOutputElementsSucceeds() throws Exception {
    // -- PREPARE --
    // Tests 1 output parser, 2 contract output elements, 3 regex groups total.
    String parserId = UUID.randomUUID().toString();
    String element1Id = UUID.randomUUID().toString();
    String element2Id = UUID.randomUUID().toString();
    String regex1Id = UUID.randomUUID().toString();
    String regex2Id = UUID.randomUUID().toString();
    String regex3Id = UUID.randomUUID().toString();

    // RegexGroups for element 1 (2 groups: host + port)
    ResourceObject regexGroup1 =
        new ResourceObject(
            regex1Id,
            "regex_groups",
            Map.of("regex_group_field", "host", "regex_group_index_values", "$1"),
            null);
    ResourceObject regexGroup2 =
        new ResourceObject(
            regex2Id,
            "regex_groups",
            Map.of("regex_group_field", "port", "regex_group_index_values", "$2"),
            null);
    // RegexGroup for element 2 (1 group: username)
    ResourceObject regexGroup3 =
        new ResourceObject(
            regex3Id,
            "regex_groups",
            Map.of("regex_group_field", "username", "regex_group_index_values", "$1"),
            null);

    // ContractOutputElement 1: PortsScan with 2 regex groups
    Map<String, Object> element1Attrs = new HashMap<>();
    element1Attrs.put("contract_output_element_rule", "(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d+)");
    element1Attrs.put("contract_output_element_key", "portscan-key");
    element1Attrs.put("contract_output_element_name", "PortsScan Name");
    element1Attrs.put("contract_output_element_type", "portscan");
    element1Attrs.put("contract_output_element_is_finding", true);
    ResourceObject contractOutputElement1 =
        new ResourceObject(
            element1Id,
            "contract_output_elements",
            element1Attrs,
            Map.of(
                "contract_output_element_regex_groups",
                new Relationship(
                    List.of(
                        new ResourceIdentifier(regex1Id, "regex_groups"),
                        new ResourceIdentifier(regex2Id, "regex_groups")))));

    // ContractOutputElement 2: Username with 1 regex group
    Map<String, Object> element2Attrs = new HashMap<>();
    element2Attrs.put("contract_output_element_rule", "(\\w+)");
    element2Attrs.put("contract_output_element_key", "username-key");
    element2Attrs.put("contract_output_element_name", "Username Name");
    element2Attrs.put("contract_output_element_type", "text");
    element2Attrs.put("contract_output_element_is_finding", true);
    ResourceObject contractOutputElement2 =
        new ResourceObject(
            element2Id,
            "contract_output_elements",
            element2Attrs,
            Map.of(
                "contract_output_element_regex_groups",
                new Relationship(List.of(new ResourceIdentifier(regex3Id, "regex_groups")))));

    // OutputParser with 2 contract output elements
    ResourceObject outputParserResource =
        new ResourceObject(
            parserId,
            "output_parsers",
            Map.of("output_parser_mode", "STDOUT", "output_parser_type", "REGEX"),
            Map.of(
                "output_parser_contract_output_elements",
                new Relationship(
                    List.of(
                        new ResourceIdentifier(element1Id, "contract_output_elements"),
                        new ResourceIdentifier(element2Id, "contract_output_elements")))));

    // Payload + OutputParser
    Map<String, Object> payloadAttrs = buildDefaultPayloadAttributes();
    payloadAttrs.put("payload_name", "Payload With Multiple Elements");
    payloadAttrs.put("command_executor", "bash");
    payloadAttrs.put("command_content", "netstat -an");
    payloadAttrs.put("payload_platforms", new String[] {"Linux"});

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(
                null,
                "command",
                payloadAttrs,
                Map.of(
                    "payload_output_parsers",
                    new Relationship(List.of(new ResourceIdentifier(parserId, "output_parsers"))))),
            List.of(
                outputParserResource,
                contractOutputElement1,
                contractOutputElement2,
                regexGroup1,
                regexGroup2,
                regexGroup3));

    MockMultipartFile zipFile = buildZipFile(document);

    // -- EXECUTE --
    String response = performImport(zipFile);

    // -- ASSERT --
    assertNotNull(response);
    JsonNode json = objectMapper.readTree(response);
    assertEquals("command", json.at("/data/type").asText());
    assertEquals(
        "Payload With Multiple Elements" + IMPORTED_OBJECT_NAME_SUFFIX,
        json.at("/data/attributes/payload_name").asText());

    // All 2 contract output elements and 3 regex groups must be present in included
    JsonNode included = json.at("/included");
    assertTrue(included.isArray());
    long contractOutputElementCount = 0;
    long regexGroupCount = 0;
    for (JsonNode node : included) {
      switch (node.get("type").asText()) {
        case "contract_output_elements" -> contractOutputElementCount++;
        case "regex_groups" -> regexGroupCount++;
        default -> {
          // other types (output_parsers, etc.) are not counted here
        }
      }
    }
    assertEquals(2, contractOutputElementCount, "Expected 2 contract output elements in included");
    assertEquals(3, regexGroupCount, "Expected 3 regex groups in included");
  }

  @Test
  @DisplayName("Import payload with null (missing) array fields")
  void importPayloadWithNullArrayFields() throws Exception {
    // -- PREPARE --
    Map<String, Object> attributes = buildDefaultPayloadAttributes();
    // Remove array fields to simulate missing/null values
    attributes.remove("payload_platforms");

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(null, "command", attributes, emptyMap()), emptyList());

    MockMultipartFile zipFile = buildZipFile(document);

    // -- EXECUTE --
    String response = performImport(zipFile);

    // -- ASSERT --
    assertNotNull(response);
    JsonNode json = objectMapper.readTree(response);
    assertEquals(0, json.at("/data/attributes/payload_platforms").size());
    assertEquals(0, json.at("/data/attributes/payload_arguments").size());
    assertEquals(0, json.at("/data/attributes/payload_prerequisites").size());
  }

  @Test
  @DisplayName("Given unknown collector should skip detection remediations during import")
  void given_unknownCollector_should_skipDetectionRemediations() throws Exception {
    // -- PREPARE --
    String unknownCollectorResourceId = UUID.randomUUID().toString();
    String remediationId = UUID.randomUUID().toString();

    ResourceObject unknownCollectorResource =
        buildCollectorResource(unknownCollectorResourceId, "non_existing_collector_type");

    ResourceObject remediation =
        DetectionRemediationFixture.buildDetectionRemediationResource(
            remediationId, "{\"rule\":\"test\"}", "collectors", unknownCollectorResourceId);

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(
                null,
                "command",
                buildDefaultPayloadAttributes(),
                Map.of(
                    "payload_detection_remediations",
                    new Relationship(
                        List.of(new ResourceIdentifier(remediationId, "detection_remediations"))))),
            List.of(remediation, unknownCollectorResource));

    MockMultipartFile zipFile = buildZipFile(document);

    // -- EXECUTE --
    String response = performImport(zipFile);

    // -- ASSERT --
    JsonNode json = objectMapper.readTree(response);
    String payloadId = json.at("/data/attributes/payload_id").asText();
    assertNotNull(payloadId);

    Payload payloadPersisted = payloadRepository.findById(payloadId).orElseThrow();
    assertTrue(
        payloadPersisted.getDetectionRemediations().isEmpty(),
        "Detection remediations with unknown collector should be skipped");
  }

  @Test
  @DisplayName(
      "Given mixed known and unknown collectors should keep only known detection remediations")
  void given_mixedCollectors_should_keepOnlyKnownDetectionRemediations() throws Exception {
    // -- PREPARE --
    var existingCollector = collectorRepository.findAll().iterator().next();

    String knownCollectorResourceId = UUID.randomUUID().toString();
    String unknownCollectorResourceId = UUID.randomUUID().toString();
    String knownRemediationId = UUID.randomUUID().toString();
    String unknownRemediationId = UUID.randomUUID().toString();

    ResourceObject knownCollectorResource =
        buildCollectorResource(knownCollectorResourceId, existingCollector.getType());
    ResourceObject unknownCollectorResource =
        buildCollectorResource(unknownCollectorResourceId, "non_existing_collector_type");

    ResourceObject knownRemediation =
        DetectionRemediationFixture.buildDetectionRemediationResource(
            knownRemediationId, "{\"rule\":\"valid\"}", "collectors", knownCollectorResourceId);
    ResourceObject unknownRemediation =
        DetectionRemediationFixture.buildDetectionRemediationResource(
            unknownRemediationId,
            "{\"rule\":\"invalid\"}",
            "collectors",
            unknownCollectorResourceId);

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(
                null,
                "command",
                buildDefaultPayloadAttributes(),
                Map.of(
                    "payload_detection_remediations",
                    new Relationship(
                        List.of(
                            new ResourceIdentifier(knownRemediationId, "detection_remediations"),
                            new ResourceIdentifier(
                                unknownRemediationId, "detection_remediations"))))),
            List.of(
                knownRemediation,
                unknownRemediation,
                knownCollectorResource,
                unknownCollectorResource));

    MockMultipartFile zipFile = buildZipFile(document);

    // -- EXECUTE --
    String response = performImport(zipFile);

    // -- ASSERT --
    JsonNode json = objectMapper.readTree(response);
    String payloadId = json.at("/data/attributes/payload_id").asText();
    assertNotNull(payloadId);

    Payload payloadPersisted = payloadRepository.findById(payloadId).orElseThrow();
    assertEquals(
        1,
        payloadPersisted.getDetectionRemediations().size(),
        "Only detection remediations with known collector should be kept");
    assertEquals(
        existingCollector.getType(),
        payloadPersisted.getDetectionRemediations().getFirst().getCollector().getType());
  }

  @Test
  @DisplayName("Given known collector references should keep all detection remediations")
  void given_knownCollectorReferences_should_keepAllDetectionRemediations() throws Exception {
    // Arrange
    var existingCollector = collectorRepository.findAll().iterator().next();
    String collectorResourceId = UUID.randomUUID().toString();
    String firstRemediationId = UUID.randomUUID().toString();
    String secondRemediationId = UUID.randomUUID().toString();

    ResourceObject collectorResource =
        buildCollectorResource(collectorResourceId, existingCollector.getType());

    ResourceObject firstRemediation =
        DetectionRemediationFixture.buildDetectionRemediationResource(
            firstRemediationId, "{\"rule\":\"first\"}", "collectors", collectorResourceId);
    ResourceObject secondRemediation =
        DetectionRemediationFixture.buildDetectionRemediationResource(
            secondRemediationId, "{\"rule\":\"second\"}", "collectors", collectorResourceId);

    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(
                null,
                "command",
                buildDefaultPayloadAttributes(),
                Map.of(
                    "payload_detection_remediations",
                    new Relationship(
                        List.of(
                            new ResourceIdentifier(firstRemediationId, "detection_remediations"),
                            new ResourceIdentifier(
                                secondRemediationId, "detection_remediations"))))),
            List.of(firstRemediation, secondRemediation, collectorResource));

    MockMultipartFile zipFile = buildZipFile(document);

    // Act
    String response = performImport(zipFile);

    // Assert
    JsonNode json = objectMapper.readTree(response);
    String payloadId = json.at("/data/attributes/payload_id").asText();
    assertNotNull(payloadId);

    Payload payloadPersisted = payloadRepository.findById(payloadId).orElseThrow();
    assertEquals(2, payloadPersisted.getDetectionRemediations().size());
  }
}
