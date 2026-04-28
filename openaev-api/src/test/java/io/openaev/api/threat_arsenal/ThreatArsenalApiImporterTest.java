package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.constants.Constants.IMPORTED_OBJECT_NAME_SUFFIX;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.Relationship;
import io.openaev.jsonapi.ResourceIdentifier;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.service.ZipJsonService;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.ThreatArsenalInputFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class ThreatArsenalApiImporterTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DomainComposer domainComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private ZipJsonService<Payload> zipJsonService;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void beforeEach() throws Exception {
    new Manager(List.of(openaevInjectorIntegrationFactory)).monitorIntegrations();
    domainComposer.reset();
    injectorContractComposer.reset();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Import Threat Arsenal Action")
  class ImportThreatArsenalAction {

    @Test
    @DisplayName(
        "Importing an injector-contract export should create a new action with imported naming")
    void given_injectorContractExport_should_importThreatArsenalAction() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mockMvc
              .perform(
                  post(THREAT_ARSENAL_URL)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String originalActionId = JsonPath.read(createResponse, "$.injector_contract_id");
      String originalPayloadName = createInput.name();

      byte[] exportedZip =
          mockMvc
              .perform(get(THREAT_ARSENAL_URL + "/" + originalActionId + "/export"))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      MockMultipartFile zipFile =
          new MockMultipartFile("file", "threat-arsenal.zip", "application/zip", exportedZip);

      // Act
      String importResponse =
          mockMvc
              .perform(multipart(THREAT_ARSENAL_URL + "/import").file(zipFile))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String importedActionId = JsonPath.read(importResponse, "$.injector_contract_id");
      String importedPayloadId = JsonPath.read(importResponse, "$.action_payload.payload_id");
      Map<String, String> importedLabels = JsonPath.read(importResponse, "$.action_labels");
      Payload importedPayload = payloadRepository.findById(importedPayloadId).orElseThrow();

      assertThat(importedActionId).isNotEqualTo(originalActionId);
      assertThat(importedPayload.getName()).isEqualTo(originalPayloadName + " (Import)");
      assertThat(importedLabels).isNotEmpty();
      assertThat(importedLabels.values()).allMatch(label -> label.endsWith(" (Import)"));
      assertThat(payloadRepository.findById(importedPayloadId)).isPresent();
      assertThat(injectorContractRepository.findById(importedActionId)).isPresent();
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Importing  payload")
  class ImportingPayload {
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
          .perform(multipart(THREAT_ARSENAL_URL + "/import").file(zipFile))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    @Test
    @DisplayName("Importing a payload export should use the legacy payload import path")
    void given_payloadExport_should_importThreatArsenalActionFromPayload() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mockMvc
              .perform(
                  post(THREAT_ARSENAL_URL)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String originalActionId = JsonPath.read(createResponse, "$.injector_contract_id");
      String originalPayloadId = JsonPath.read(createResponse, "$.action_payload.payload_id");
      String originalPayloadName = createInput.name();

      byte[] exportedZip =
          mockMvc
              .perform(get(PAYLOAD_URI + "/" + originalPayloadId + "/export"))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      MockMultipartFile zipFile =
          new MockMultipartFile("file", "payload.zip", "application/zip", exportedZip);

      // Act
      String importResponse =
          mockMvc
              .perform(multipart(THREAT_ARSENAL_URL + "/import").file(zipFile))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String importedActionId = JsonPath.read(importResponse, "$.injector_contract_id");
      String importedPayloadId = JsonPath.read(importResponse, "$.action_payload.payload_id");

      assertThat(importedActionId).isNotEqualTo(originalActionId);
      assertThat(importedPayloadId).isNotEqualTo(originalPayloadId);

      Payload importedPayload = payloadRepository.findById(importedPayloadId).orElseThrow();
      assertThat(importedPayload.getName()).isEqualTo(originalPayloadName + " (Import)");
      assertThat(injectorContractRepository.findInjectorContractByPayload(importedPayload))
          .map(InjectorContract::getId)
          .contains(importedActionId);
    }

    @Test
    @DisplayName("Import a payload returns complete entity with all array fields")
    void importPayloadReturnsPayloadWithAllArrayFields() throws Exception {
      // -- PREPARE --
      // payload_arguments and payload_prerequisites must be arrays of objects,
      // matching the PayloadArgument / PayloadPrerequisite model schema.
      Map<String, Object> argument1 =
          Map.of("type", "text", "key", "target_host", "default_value", "localhost");
      Map<String, Object> argument2 =
          Map.of("type", "text", "key", "port", "default_value", "8080");
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
                      new Relationship(
                          List.of(new ResourceIdentifier(parserId, "output_parsers"))))),
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
      String importedPayloadId = JsonPath.read(response, "$.action_payload.payload_id");
      Payload importedPayload = payloadRepository.findById(importedPayloadId).orElseThrow();
      assertThat(importedPayload.getName()).isEqualTo("Payload With Multiple Elements (Import)");

      Set<ContractOutputElement> outputElements =
          importedPayload.getOutputParsers().stream().findFirst().get().getContractOutputElements();

      assertEquals(2, outputElements.size(), "Expected 2 contract output elements");

      ContractOutputElement portsScanElement =
          outputElements.stream()
              .filter(e -> "PortsScan Name".equals(e.getName()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Missing 'PortsScan Name' element"));
      ContractOutputElement usernameElement =
          outputElements.stream()
              .filter(e -> "Username Name".equals(e.getName()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Missing 'Username Name' element"));

      assertEquals(
          2, portsScanElement.getRegexGroups().size(), "PortsScan should have 2 regex groups");
      assertEquals(
          1, usernameElement.getRegexGroups().size(), "Username should have 1 regex group");
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
  }
}
