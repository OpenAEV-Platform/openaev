package io.openaev.rest.payload;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY;
import static io.openaev.database.specification.InjectorContractSpecification.byPayloadId;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.collector.form.CollectorCreateInput;
import io.openaev.rest.payload.form.*;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.fixtures.composers.CollectorComposer;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
class PayloadApiTest extends IntegrationTest {

  private static final String PAYLOAD_URI = "/api/payloads";
  private static Document EXECUTABLE_FILE;

  @Autowired private MockMvc mvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  @Autowired private CollectorComposer collectorComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  @Resource private ObjectMapper objectMapper;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void beforeEach() throws Exception {
    new Manager(List.of(openaevInjectorIntegrationFactory)).monitorIntegrations();
  }

  @BeforeAll
  void beforeAll() {
    collectorComposer.reset();
    collectorComposer.forCollector(CollectorFixture.createDefaultCollector("CS")).persist();
    collectorComposer.forCollector(CollectorFixture.createDefaultCollector("SENTINEL")).persist();
    collectorComposer.forCollector(CollectorFixture.createDefaultCollector("DEFENDER")).persist();
    EXECUTABLE_FILE = documentRepository.save(PayloadInputFixture.createDefaultExecutableFile());
  }

  @AfterAll
  void afterAll() {
    this.payloadRepository.deleteAll();
    if (EXECUTABLE_FILE != null) {
      this.documentRepository.deleteById(EXECUTABLE_FILE.getId());
    }
    this.collectorRepository.deleteAll();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Create Payload")
  class CreatePayload {

    @Test
    @DisplayName("Create Payload")
    void createExecutablePayload() throws Exception {

      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForExecutable(List.of(domain.getId()));
      input.setExecutableFile(EXECUTABLE_FILE.getId());

      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.payload_name").value("My Executable Payload"))
          .andExpect(jsonPath("$.payload_description").value("Executable description"))
          .andExpect(jsonPath("$.payload_source").value("MANUAL"))
          .andExpect(jsonPath("$.payload_status").value("VERIFIED"))
          .andExpect(jsonPath("$.payload_platforms.[0]").value("Linux"))
          .andExpect(
              jsonPath("$.payload_execution_arch")
                  .value(Payload.PAYLOAD_EXECUTION_ARCH.x86_64.name()));
    }

    @Test
    @DisplayName("Creating a Payload with a null as arch should fail")
    void createPayloadWithNullArch() throws Exception {

      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(
              List.of(domain.getId()));
      input.setExecutionArch(null);
      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "Creating an executable Payload with an arch different from x86_64 or arm64 should fail")
    void createExecutablePayloadWithoutArch() throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();

      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForExecutable(List.of(domain.getId()));
      input.setExecutableFile(EXECUTABLE_FILE.getId());
      input.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);

      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isBadRequest())
          .andExpect(
              result -> {
                String errorMessage = result.getResolvedException().getMessage();
                assertTrue(
                    errorMessage.contains("Executable architecture must be x86_64 or arm64"));
              });
    }

    @Test
    @DisplayName("Create Payload with output parser")
    void given_payload_create_input_with_output_parsers_should_return_payload_with_output_parsers()
        throws Exception {

      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputWithOutputParser(
              List.of(domain.getId()));

      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.payload_name").value("Command line payload"))
          .andExpect(
              jsonPath("$.payload_output_parsers[0].output_parser_mode")
                  .value(ParserMode.STDOUT.name()))
          .andExpect(
              jsonPath("$.payload_output_parsers[0].output_parser_type")
                  .value(ParserType.REGEX.name()))
          .andExpect(
              jsonPath(
                      "$.payload_output_parsers[0].output_parser_contract_output_elements[0].contract_output_element_rule")
                  .value("rule"))
          .andExpect(
              jsonPath(
                      "$.payload_output_parsers[0].output_parser_contract_output_elements[0].contract_output_element_key")
                  .value("IPV6"));
    }

    @Test
    @DisplayName("Create Payload with detection remediations")
    void
        given_payload_create_input_with_detection_remediation_should_return_payload_with_detection_remediation()
            throws Exception {
      when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);

      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputWithDetectionRemediation(
              List.of(domain.getId()));

      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.payload_name").value("Command line payload"))
          .andExpect(jsonPath("$.payload_detection_remediations.length()").value(3));
    }

    @Test
    @DisplayName("Create Payload with detection remediations and then update remediation")
    void
        given_payload_update_input_with_detection_remediation_should_return_payload_with_detection_remediation_updated()
            throws Exception {
      when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
      /******* Create *******/
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputWithDetectionRemediation(
              List.of(domain.getId()));

      String response =
          mvc.perform(
                  post(PAYLOAD_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String payloadId = JsonPath.read(response, "$.payload_id");

      /******* Update *******/
      PayloadUpdateInput updateInput =
          PayloadInputFixture.getDefaultCommandPayloadUpdateInput(List.of(domain.getId()));
      String updatedValues = "test values";
      List<DetectionRemediationInput> detectionRemediation =
          PayloadInputFixture.buildDetectionRemediations();
      detectionRemediation.stream().forEach(dr -> dr.setValues(updatedValues));
      updateInput.setDetectionRemediations(detectionRemediation);
      mvc.perform(
              put(PAYLOAD_URI + "/" + payloadId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.payload_detection_remediations.length()").value(3))
          .andExpect(
              jsonPath("$.payload_detection_remediations[0].detection_remediation_values")
                  .value(updatedValues));
    }

    @Test
    @DisplayName("Create Payload with targeted asset")
    void given_targetedAssetArgument_should_create_payload_with_targeted_asset() throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(
              List.of(domain.getId()));

      PayloadArgument targetedAssetArgument = new PayloadArgument();
      targetedAssetArgument.setKey("URL");
      targetedAssetArgument.setType(ArgumentType.TargetedAsset);
      targetedAssetArgument.setDefaultValue("hostname");
      targetedAssetArgument.setSeparator("-u");
      input.setArguments(List.of(targetedAssetArgument));

      String response =
          mvc.perform(
                  post(PAYLOAD_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andExpect(jsonPath("$.payload_name").value("Command line payload"))
              //              .andExpect(jsonPath("$.payload_arguments]").value("targeted-asset"))
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertEquals("1", JsonPath.read(response, "$.payload_arguments.length()").toString());
      assertEquals("targeted-asset", JsonPath.read(response, "$.payload_arguments[0].type"));
      InjectorContract injectorContract =
          injectorContractRepository
              .findOne(byPayloadId(JsonPath.read(response, "$.payload_id")))
              .orElse(null);

      assertNotNull(injectorContract);

      ArrayNode fields = (ArrayNode) injectorContract.getConvertedContent().get("fields");
      List<JsonNode> fieldsForTargetedAsset = new ArrayList<>();
      fields.forEach(
          f -> {
            String key = f.get("key").asText();
            String type = f.get("type").asText();

            if ("URL".equals(key)) {
              assertEquals("targeted-asset", type);
              fieldsForTargetedAsset.add(f);
            } else if ((CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY + "-URL").equals(key)) {
              assertEquals("select", type);
              assertEquals("[\"hostname\"]", f.get("defaultValue").toString());
              fieldsForTargetedAsset.add(f);
            } else if ((CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR + "-URL")
                .equals(key)) {
              assertEquals("text", type);
              assertEquals("-u", f.get("defaultValue").asText());
              fieldsForTargetedAsset.add(f);
            }
          });
      assertEquals(3, fieldsForTargetedAsset.size(), "Fields size should be 3");
    }
  }

  // ---- Payload Arguments ----

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Payload Arguments")
  class PayloadArguments {

    /**
     * All non-targeted-asset argument types (text, number, port, portscan, ipv4, ipv6, credentials,
     * cve) must generate a {@code text} contract field so they are visible and editable in the
     * inject form.
     */
    @Test
    @DisplayName(
        "Given all string-type arguments, should produce a text field in the injector contract for each")
    void given_allStringTypeArguments_should_each_produce_text_field_in_contract()
        throws Exception {
      // Arrange
      new Manager(List.of(openaevInjectorIntegrationFactory)).monitorIntegrations();

      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(
              List.of(domain.getId()));
      input.setArguments(
          List.of(
              PayloadFixture.createPayloadArgument("arg_text", ArgumentType.Text, "hello", null),
              PayloadFixture.createPayloadArgument("arg_number", ArgumentType.Number, "42", null),
              PayloadFixture.createPayloadArgument("arg_port", ArgumentType.Port, "8080", null),
              PayloadFixture.createPayloadArgument(
                  "arg_portscan", ArgumentType.PortsScan, "192.168.1.0/24", null),
              PayloadFixture.createPayloadArgument("arg_ipv4", ArgumentType.IPv4, "10.0.0.1", null),
              PayloadFixture.createPayloadArgument("arg_ipv6", ArgumentType.IPv6, "::1", null),
              PayloadFixture.createPayloadArgument(
                  "arg_credentials", ArgumentType.Credentials, "admin:pass", null),
              PayloadFixture.createPayloadArgument(
                  "arg_cve", ArgumentType.CVE, "CVE-2024-1234", null)));

      // Act
      String response =
          mvc.perform(
                  post(PAYLOAD_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().is2xxSuccessful())
              .andExpect(jsonPath("$.payload_arguments.length()").value(8))
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — every argument is round-tripped with the correct type label
      assertEquals("text", JsonPath.read(response, "$.payload_arguments[0].type"));
      assertEquals("number", JsonPath.read(response, "$.payload_arguments[1].type"));
      assertEquals("port", JsonPath.read(response, "$.payload_arguments[2].type"));
      assertEquals("portscan", JsonPath.read(response, "$.payload_arguments[3].type"));
      assertEquals("ipv4", JsonPath.read(response, "$.payload_arguments[4].type"));
      assertEquals("ipv6", JsonPath.read(response, "$.payload_arguments[5].type"));
      assertEquals("credentials", JsonPath.read(response, "$.payload_arguments[6].type"));
      assertEquals("cve", JsonPath.read(response, "$.payload_arguments[7].type"));

      // Assert — every argument key produces a "text" field in the injector contract
      InjectorContract injectorContract =
          injectorContractRepository
              .findOne(byPayloadId(JsonPath.read(response, "$.payload_id")))
              .orElse(null);
      assertNotNull(injectorContract);

      Set<String> argumentKeys =
          Set.of(
              "arg_text",
              "arg_number",
              "arg_port",
              "arg_portscan",
              "arg_ipv4",
              "arg_ipv6",
              "arg_credentials",
              "arg_cve");
      Map<String, String> keyToContractType = new HashMap<>();
      ArrayNode contractFields = (ArrayNode) injectorContract.getConvertedContent().get("fields");
      contractFields.forEach(
          f -> {
            String key = f.get("key").asText();
            if (argumentKeys.contains(key)) {
              keyToContractType.put(key, f.get("type").asText());
            }
          });

      assertEquals(
          8, keyToContractType.size(), "All 8 argument keys must appear in the injector contract");
      keyToContractType.forEach(
          (key, type) ->
              assertEquals(
                  "text", type, "Argument '" + key + "' must produce a text contract field"));
    }

    /**
     * Subtypes are persisted at the payload-argument level and returned verbatim in the response.
     * Three structured types are covered: portscan/host, credentials/username, cve/severity.
     */
    @Test
    @DisplayName(
        "Given arguments with subtypes, should persist and return the correct subtype for each")
    void given_argumentsWithSubtypes_should_persist_and_return_subtypes() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(
              List.of(domain.getId()));
      input.setArguments(
          List.of(
              PayloadFixture.createPayloadArgument(
                  "scan_host",
                  ArgumentType.PortsScan,
                  "192.168.1.0/24",
                  null,
                  ArgumentSubType.Host),
              PayloadFixture.createPayloadArgument(
                  "cred_user", ArgumentType.Credentials, "admin", null, ArgumentSubType.Username),
              PayloadFixture.createPayloadArgument(
                  "cve_severity",
                  ArgumentType.CVE,
                  "CVE-2024-1234",
                  null,
                  ArgumentSubType.Severity)));

      // Act
      String response =
          mvc.perform(
                  post(PAYLOAD_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().is2xxSuccessful())
              .andExpect(jsonPath("$.payload_arguments.length()").value(3))
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — type and subtype are round-tripped correctly for each argument
      assertEquals("portscan", JsonPath.read(response, "$.payload_arguments[0].type"));
      assertEquals("host", JsonPath.read(response, "$.payload_arguments[0].subtype"));
      assertEquals("credentials", JsonPath.read(response, "$.payload_arguments[1].type"));
      assertEquals("username", JsonPath.read(response, "$.payload_arguments[1].subtype"));
      assertEquals("cve", JsonPath.read(response, "$.payload_arguments[2].type"));
      assertEquals("severity", JsonPath.read(response, "$.payload_arguments[2].subtype"));
    }

    /**
     * An argument with a subtype still produces a plain {@code text} contract field — the subtype
     * is a payload-level annotation, not a different UI component type.
     */
    @Test
    @DisplayName(
        "Given an argument with a subtype, should still produce a text field in the injector contract")
    void given_argumentWithSubtype_should_produce_text_field_in_contract() throws Exception {
      // Arrange
      new Manager(List.of(openaevInjectorIntegrationFactory)).monitorIntegrations();

      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(
              List.of(domain.getId()));
      input.setArguments(
          List.of(
              PayloadFixture.createPayloadArgument(
                  "scan_result",
                  ArgumentType.PortsScan,
                  "10.0.0.0/24",
                  null,
                  ArgumentSubType.Host)));

      // Act
      String response =
          mvc.perform(
                  post(PAYLOAD_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().is2xxSuccessful())
              .andExpect(jsonPath("$.payload_arguments[0].type").value("portscan"))
              .andExpect(jsonPath("$.payload_arguments[0].subtype").value("host"))
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — contract field type is still "text"; default value is preserved
      InjectorContract injectorContract =
          injectorContractRepository
              .findOne(byPayloadId(JsonPath.read(response, "$.payload_id")))
              .orElse(null);
      assertNotNull(injectorContract);

      JsonNode scanField = null;
      ArrayNode contractFields = (ArrayNode) injectorContract.getConvertedContent().get("fields");
      for (JsonNode f : contractFields) {
        if ("scan_result".equals(f.get("key").asText())) {
          scanField = f;
          break;
        }
      }
      assertNotNull(scanField, "Contract must contain a field for 'scan_result'");
      assertEquals("text", scanField.get("type").asText());
      assertEquals("10.0.0.0/24", scanField.get("defaultValue").asText());
    }
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Update Executable Payload")
  void updateExecutablePayload() throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForExecutable(List.of(domain.getId()));
    createInput.setExecutableFile(EXECUTABLE_FILE.getId());

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.payload_name").value("My Executable Payload"))
            .andExpect(jsonPath("$.payload_platforms.[0]").value("Linux"))
            .andExpect(
                jsonPath("$.payload_execution_arch")
                    .value(Payload.PAYLOAD_EXECUTION_ARCH.x86_64.name()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput =
        PayloadInputFixture.getDefaultExecutablePayloadUpdateInput(List.of(domain.getId()));
    updateInput.setExecutableFile(EXECUTABLE_FILE.getId());

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_name").value("My Updated Executable Payload"))
        .andExpect(jsonPath("$.payload_platforms.[0]").value("MacOS"))
        .andExpect(
            jsonPath("$.payload_execution_arch")
                .value(Payload.PAYLOAD_EXECUTION_ARCH.arm64.name()));
  }

  @Test
  @DisplayName("Updating an Executed Payload with null as arch should fail")
  @WithMockUser(isAdmin = true)
  void updateExecutablePayloadWithoutArch() throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForExecutable(List.of(domain.getId()));
    createInput.setExecutableFile(EXECUTABLE_FILE.getId());

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput =
        PayloadInputFixture.getDefaultExecutablePayloadUpdateInput(List.of(domain.getId()));
    updateInput.setExecutableFile(EXECUTABLE_FILE.getId());
    updateInput.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(
            result -> {
              String errorMessage = result.getResolvedException().getMessage();
              assertTrue(errorMessage.contains("Executable architecture must be x86_64 or arm64"));
            });
  }

  @Test
  @DisplayName("Updating a Payload no Executable without arch should set ALL_ARCHITECTURES")
  @WithMockUser(isAdmin = true)
  void updatePayloadNoExecutableWithoutArch() throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();

    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput =
        PayloadInputFixture.getDefaultCommandPayloadUpdateInput(List.of(domain.getId()));
    updateInput.setExecutableFile(EXECUTABLE_FILE.getId());

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_name").value("Updated Command line payload"))
        .andExpect(jsonPath("$.payload_platforms.[0]").value("MacOS"))
        .andExpect(
            jsonPath("$.payload_execution_arch")
                .value(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES.name()));
  }

  @Test
  @DisplayName("Update Payload with output parser")
  @WithMockUser(isAdmin = true)
  void
      given_payload_update_input_with_output_parsers_should_return_updated_payloadd_with_output_parsers()
          throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput =
        PayloadInputFixture.getDefaultCommandPayloadUpdateInputWithOutputParser(
            List.of(domain.getId()));

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_name").value("Updated Command line payload"))
        .andExpect(
            jsonPath("$.payload_output_parsers[0].output_parser_mode")
                .value(ParserMode.STDOUT.name()))
        .andExpect(
            jsonPath("$.payload_output_parsers[0].output_parser_type")
                .value(ParserType.REGEX.name()))
        .andExpect(
            jsonPath(
                    "$.payload_output_parsers[0].output_parser_contract_output_elements[0].contract_output_element_rule")
                .value("rule"))
        .andExpect(
            jsonPath(
                    "$.payload_output_parsers[0].output_parser_contract_output_elements[0].contract_output_element_key")
                .value("IPV6"));
  }

  @Test
  @DisplayName("Update Payload with detection remediations")
  @WithMockUser(isAdmin = true)
  void
      given_payload_update_input_with_detection_remediations_should_return_updated_payload_with_detection_remediations()
          throws Exception {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.payload_detection_remediations.length()").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput =
        PayloadInputFixture.getDefaultPayloadUpdateInputWithDetectionRemediation(
            List.of(domain.getId()));

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_detection_remediations.length()").value(3));
    ;
  }

  @Test
  @DisplayName("Upsert architecture of a Payload")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PAYLOADS})
  void upsertCommandPayloadToValidateArchitecture() throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    Payload payload = payloadRepository.save(PayloadFixture.createDefaultCommand());
    payload.setExternalId("external-id");

    // -- Without property architecture
    PayloadUpsertInput upsertInput =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInput(Set.of(domain));
    upsertInput.setExternalId(payload.getExternalId());
    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(upsertInput))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.payload_execution_arch")
                .value(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES.name()));

    // -- With property architecture and null value
    upsertInput.setExecutionArch(null);
    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(upsertInput))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(
            result -> {
              String errorMessage = result.getResolvedException().getMessage();
              assertTrue(errorMessage.contains("Payload architecture cannot be null"));
            });
  }

  @Test
  @DisplayName("Upsert Payload with output parser")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PAYLOADS})
  void
      given_payload_upsert_input_with_output_parsers_should_return_updated_payload_with_output_parsers()
          throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputWithOutputParser(
            List.of(domain.getId()));

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    PayloadUpsertInput upsertInput =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInputWithOutputParser(Set.of(domain));
    upsertInput.setExternalId("external-id");

    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(upsertInput))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.payload_output_parsers[0].output_parser_mode")
                .value(ParserMode.STDOUT.name()))
        .andExpect(
            jsonPath("$.payload_output_parsers[0].output_parser_type")
                .value(ParserType.REGEX.name()))
        .andExpect(
            jsonPath(
                    "$.payload_output_parsers[0].output_parser_contract_output_elements[0].contract_output_element_rule")
                .value("regex xPath"))
        .andExpect(
            jsonPath(
                    "$.payload_output_parsers[0].output_parser_contract_output_elements[0].contract_output_element_key")
                .value("username"))
        .andExpect(
            jsonPath(
                    "$.payload_output_parsers[0].output_parser_contract_output_elements[0].contract_output_element_regex_groups[0].regex_group_field")
                .value("username"))
        .andExpect(
            jsonPath(
                    "$.payload_output_parsers[0].output_parser_contract_output_elements[0].contract_output_element_regex_groups[0].regex_group_index_values")
                .value("$1"));
  }

  @Test
  @DisplayName("Upsert Payload with detection remediations")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PAYLOADS})
  void
      given_payload_upsert_input_with_detection_remediation_should_return_updated_payload_with_detection_remediations()
          throws Exception {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_detection_remediations.length()").value(0));

    PayloadUpsertInput upsertInput =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInputWithDetectionRemediations(
            Set.of(domain));
    upsertInput.setExternalId("external-id");

    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(upsertInput))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload_detection_remediations.length()").value(3));
  }

  // -- CHECK CLEANUP AND EXECUTOR --

  @Test
  @DisplayName("Creating Command Line payload with both set executor and content should succeed")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithBothSetExecutorAndContent() throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    createInput.setExecutor("sh");
    createInput.setExecutor("echo hello world");

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Creating Command Line payload with both null cleanup executor and command should succeed")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithBothNullCleanupExecutorAndCommand() throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    createInput.setCleanupExecutor(null);
    createInput.setCleanupCommand(null);

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Creating Command Line payload with both set cleanup executor and command should succeed")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithBothSetCleanupExecutorAndCommand() throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    createInput.setCleanupExecutor("sh");
    createInput.setCleanupCommand("cleanup this mess");

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Creating Command Line payload with only set cleanup executor and null command should fail")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithOnlySetCleanupExecutorAndNullCommand() throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    createInput.setCleanupExecutor("sh");
    createInput.setCleanupCommand(null);

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput))
                .with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "Creating Command Line payload with only set cleanup command and null executor should fail")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithOnlySetCommandAndNullExecutor() throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    createInput.setCleanupExecutor(null);
    createInput.setCleanupCommand("cleanup this mess");

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput))
                .with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "Updating Command Line payload with only set cleanup command and null executor should fail")
  @WithMockUser(isAdmin = true)
  void updateCommandLinePayloadWithOnlySetCommandAndNullExecutor() throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of(domain.getId()));

    createInput.setCleanupExecutor(null);
    createInput.setCleanupCommand(null);

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput = new PayloadUpdateInput();
    updateInput.setName("updated command line payload");
    updateInput.setContent("echo world again");
    updateInput.setExecutor("sh");
    updateInput.setDomainIds(List.of(domain.getId()));
    updateInput.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux});

    updateInput.setCleanupCommand("cleanup this mess");

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput))
                .with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "Duplicating a Community and Verified Payload should result in a Manual and Unverified Payload")
  @WithMockUser(isAdmin = true)
  void duplicateExecutablePayload() throws Exception {

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForExecutable(List.of(domain.getId()));
    createInput.setExecutableFile(EXECUTABLE_FILE.getId());
    createInput.setSource(Payload.PAYLOAD_SOURCE.COMMUNITY);
    createInput.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);

    String createdPayload =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.payload_name").value("My Executable Payload"))
            .andExpect(jsonPath("$.payload_platforms.[0]").value("Linux"))
            .andExpect(
                jsonPath("$.payload_execution_arch")
                    .value(Payload.PAYLOAD_EXECUTION_ARCH.x86_64.name()))
            .andExpect(jsonPath("$.payload_source").value("COMMUNITY"))
            .andExpect(jsonPath("$.payload_status").value("VERIFIED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(createdPayload, "$.payload_id");

    mvc.perform(post(PAYLOAD_URI + "/" + payloadId + "/duplicate").with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_name").value("My Executable Payload (duplicate)"))
        .andExpect(jsonPath("$.payload_platforms.[0]").value("Linux"))
        .andExpect(
            jsonPath("$.payload_execution_arch")
                .value(Payload.PAYLOAD_EXECUTION_ARCH.x86_64.name()))
        .andExpect(jsonPath("$.payload_source").value("MANUAL"))
        .andExpect(jsonPath("$.payload_status").value("UNVERIFIED"));
  }

  @Test
  @DisplayName("Process Deprecated Payloads")
  @WithMockUser(isAdmin = true)
  void processDeprecatedPayloads() throws Exception {
    String collectorId = "039eee9b-b95d-4b11-95bb-a9ac233f1738";
    CollectorCreateInput collectorCreateInput = new CollectorCreateInput();
    collectorCreateInput.setId(collectorId);
    collectorCreateInput.setName("My Collector");
    collectorCreateInput.setType("openaev_atomic_red_team");

    MockMultipartFile inputMultipart =
        new MockMultipartFile(
            "input",
            null,
            "application/json",
            objectMapper.writeValueAsString(collectorCreateInput).getBytes());

    mvc.perform(multipart("/api/collectors").file(inputMultipart).with(csrf()))
        .andExpect(status().is2xxSuccessful());

    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();

    PayloadUpsertInput payloadUpsertInput1 =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInput(Set.of(domain));
    payloadUpsertInput1.setCollector(collectorId);
    payloadUpsertInput1.setExternalId("54e03fc3-e906-4b8e-865a-972e3e339d60");

    PayloadUpsertInput payloadUpsertInput2 =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInput(Set.of(domain));
    payloadUpsertInput2.setName("Command Payload 2");
    payloadUpsertInput2.setCollector(collectorId);
    payloadUpsertInput2.setExternalId("7a1ecc3c-3201-45cb-9a93-58405c0a680d");

    String upsertedPayload1 =
        mvc.perform(
                post(PAYLOAD_URI + "/upsert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(payloadUpsertInput1))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String payloadId1 = JsonPath.read(upsertedPayload1, "$.payload_id");

    String upsertedPayload2 =
        mvc.perform(
                post(PAYLOAD_URI + "/upsert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(payloadUpsertInput2))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String payloadId2 = JsonPath.read(upsertedPayload2, "$.payload_id");

    PayloadsDeprecateInput payloadsDeprecateInput =
        new PayloadsDeprecateInput(collectorId, List.of(payloadUpsertInput2.getExternalId()));

    mvc.perform(
            post(PAYLOAD_URI + "/deprecate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(payloadsDeprecateInput))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    mvc.perform(get(PAYLOAD_URI + "/" + payloadId1).with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_name").value("My Command Payload"))
        .andExpect(jsonPath("$.payload_collector_type").value(collectorCreateInput.getType()))
        .andExpect(jsonPath("$.payload_status").value("DEPRECATED"));

    mvc.perform(get(PAYLOAD_URI + "/" + payloadId2).with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_name").value("Command Payload 2"))
        .andExpect(jsonPath("$.payload_collector_type").value(collectorCreateInput.getType()))
        .andExpect(jsonPath("$.payload_status").value("UNVERIFIED"));
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(
      withCapabilities = {
        Capability.MANAGE_PAYLOADS,
        Capability.ACCESS_PAYLOADS,
        Capability.DELETE_PAYLOADS
      })
  @org.springframework.transaction.annotation.Transactional
  class TenantIsolation {

    /**
     * Creates a payload directly in the given tenant via native SQL, bypassing the creation service
     * which requires injector integration to be registered for the tenant.
     */
    private String createPayloadInTenant(Tenant tenant, String name) {
      String payloadId = UUID.randomUUID().toString();
      entityManager
          .createNativeQuery("SELECT set_config('app.current_tenant', :tenantId, false)")
          .setParameter("tenantId", tenant.getId())
          .getSingleResult();
      entityManager
          .createNativeQuery(
              "INSERT INTO payloads (payload_id, payload_type, payload_name, payload_status, payload_source, tenant_id, payload_created_at, payload_updated_at) "
                  + "VALUES (:id, 'Command', :name, 'VERIFIED', 'MANUAL', :tenantId, now(), now())")
          .setParameter("id", payloadId)
          .setParameter("name", name)
          .setParameter("tenantId", tenant.getId())
          .executeUpdate();
      // Reset RLS context to default tenant so subsequent API calls don't inherit tenantX
      entityManager
          .createNativeQuery("SELECT set_config('app.current_tenant', '', false)")
          .getSingleResult();
      entityManager.flush();
      entityManager.clear();
      return payloadId;
    }

    @Test
    @DisplayName("Payload created in tenant X should NOT be readable from tenant Y")
    // @WithoutRls // uncomment to fail the test: native query caught by RLS
    void given_payloadInTenantX_should_notBeReadableFromTenantY() throws Exception {
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_PAYLOADS, Capability.ACCESS_PAYLOADS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_PAYLOADS));

      String payloadId = createPayloadInTenant(tenantX, "IsolationReadTest");

      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/payloads/" + payloadId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Payload created in tenant X should be readable from tenant X")
    void given_payloadInTenantX_should_beReadableFromTenantX() throws Exception {
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_PAYLOADS, Capability.ACCESS_PAYLOADS));

      String payloadId = createPayloadInTenant(tenantX, "IsolationSameTenantTest");

      mvc.perform(
              get("/api/tenants/" + tenantX.getId() + "/payloads/" + payloadId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Payload search in tenant Y should NOT return payloads from tenant X")
    void given_payloadInTenantX_should_notAppearInTenantYSearch() throws Exception {
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_PAYLOADS, Capability.ACCESS_PAYLOADS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_PAYLOADS));

      createPayloadInTenant(tenantX, "CrossTenantPayloadSearch");

      SearchPaginationInput searchInput =
          PaginationFixture.simpleTextSearch("CrossTenantPayloadSearch");

      String searchResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/payloads/search")
                      .content(asJsonString(searchInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertEquals(Integer.valueOf(0), JsonPath.read(searchResponse, "$.totalElements"));
    }

    @Test
    @DisplayName("Payload created in tenant X should NOT be deletable from tenant Y")
    void given_payloadInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_PAYLOADS, Capability.ACCESS_PAYLOADS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_PAYLOADS, Capability.ACCESS_PAYLOADS));

      String payloadId = createPayloadInTenant(tenantX, "IsolationDeleteTest");

      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/payloads/" + payloadId).with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
