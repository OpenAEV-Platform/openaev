package io.openbas.rest.payload;

import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR;
import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY;
import static io.openbas.database.specification.InjectorContractSpecification.byPayloadId;
import static io.openbas.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.repository.CollectorRepository;
import io.openbas.database.repository.DocumentRepository;
import io.openbas.database.repository.InjectorContractRepository;
import io.openbas.database.repository.PayloadRepository;
import io.openbas.ee.Ee;
import io.openbas.rest.collector.form.CollectorCreateInput;
import io.openbas.rest.payload.form.*;
import io.openbas.utils.fixtures.CollectorFixture;
import io.openbas.utils.fixtures.PayloadFixture;
import io.openbas.utils.fixtures.PayloadInputFixture;
import io.openbas.utils.fixtures.composers.CollectorComposer;
import io.openbas.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

  @Autowired private CollectorComposer collectorComposer;

  @Resource private ObjectMapper objectMapper;

  @MockBean private Ee eeService;

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
    this.documentRepository.deleteAll(List.of(EXECUTABLE_FILE));
    this.payloadRepository.deleteAll();
    this.collectorRepository.deleteAll();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Create Payload")
  class CreatePayload {

    @Test
    @DisplayName("Create Payload")
    void createExecutablePayload() throws Exception {
      PayloadCreateInput input = PayloadInputFixture.createDefaultPayloadCreateInputForExecutable();
      input.setExecutableFile(EXECUTABLE_FILE.getId());

      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
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
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();
      input.setExecutionArch(null);
      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "Creating an executable Payload with an arch different from x86_64 or arm64 should fail")
    void createExecutablePayloadWithoutArch() throws Exception {
      PayloadCreateInput input = PayloadInputFixture.createDefaultPayloadCreateInputForExecutable();
      input.setExecutableFile(EXECUTABLE_FILE.getId());
      input.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);

      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
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
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputWithOutputParser();

      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
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
      when(eeService.isEnterpriseLicenseInactive(any())).thenReturn(false);

      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputWithDetectionRemediation();

      mvc.perform(
              post(PAYLOAD_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.payload_name").value("Command line payload"))
          .andExpect(jsonPath("$.payload_detection_remediations.length()").value(3));
    }

    @Test
    @DisplayName("Create Payload with detection remediations and then update remediation")
    void
        given_payload_update_input_with_detection_remediation_should_return_payload_with_detection_remediation_updated()
            throws Exception {
      when(eeService.isEnterpriseLicenseInactive(any())).thenReturn(false);
      /******* Create *******/
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputWithDetectionRemediation();

      String response =
          mvc.perform(
                  post(PAYLOAD_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String payloadId = JsonPath.read(response, "$.payload_id");

      /******* Update *******/
      PayloadUpdateInput updateInput = PayloadInputFixture.getDefaultCommandPayloadUpdateInput();
      String updatedValues = "test values";
      List<DetectionRemediationInput> detectionRemediation =
          PayloadInputFixture.buildDetectionRemediations();
      detectionRemediation.stream().forEach(dr -> dr.setValues(updatedValues));
      updateInput.setDetectionRemediations(detectionRemediation);
      mvc.perform(
              put(PAYLOAD_URI + "/" + payloadId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.payload_detection_remediations.length()").value(3))
          .andExpect(
              jsonPath("$.payload_detection_remediations[0].detection_remediation_values")
                  .value(updatedValues));
    }

    @Test
    @DisplayName("Create Payload with targeted asset")
    void given_targetedAssetArgument_should_create_payload_with_targeted_asset() throws Exception {
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

      PayloadArgument targetedAssetArgument = new PayloadArgument();
      targetedAssetArgument.setKey("URL");
      targetedAssetArgument.setType("targeted-asset");
      targetedAssetArgument.setDefaultValue("hostname");
      targetedAssetArgument.setSeparator("-u");
      input.setArguments(List.of(targetedAssetArgument));

      String response =
          mvc.perform(
                  post(PAYLOAD_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
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

  @Test
  @DisplayName("Update Executable Payload")
  @WithMockUser(isAdmin = true)
  void updateExecutablePayload() throws Exception {
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForExecutable();
    createInput.setExecutableFile(EXECUTABLE_FILE.getId());

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput)))
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

    PayloadUpdateInput updateInput = PayloadInputFixture.getDefaultExecutablePayloadUpdateInput();
    updateInput.setExecutableFile(EXECUTABLE_FILE.getId());

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput)))
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
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForExecutable();
    createInput.setExecutableFile(EXECUTABLE_FILE.getId());

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput = PayloadInputFixture.getDefaultExecutablePayloadUpdateInput();
    updateInput.setExecutableFile(EXECUTABLE_FILE.getId());
    updateInput.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput)))
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
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput = PayloadInputFixture.getDefaultCommandPayloadUpdateInput();
    updateInput.setExecutableFile(EXECUTABLE_FILE.getId());

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput)))
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
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput =
        PayloadInputFixture.getDefaultCommandPayloadUpdateInputWithOutputParser();

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput)))
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
    when(eeService.isEnterpriseLicenseInactive(any())).thenReturn(false);

    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.payload_detection_remediations.length()").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput =
        PayloadInputFixture.getDefaultPayloadUpdateInputWithDetectionRemediation();

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_detection_remediations.length()").value(3));
    ;
  }

  @Test
  @DisplayName("Upsert architecture of a Payload")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PAYLOADS})
  void upsertCommandPayloadToValidateArchitecture() throws Exception {
    Payload payload = payloadRepository.save(PayloadFixture.createDefaultCommand());
    payload.setExternalId("external-id");

    // -- Without property architecture
    PayloadUpsertInput upsertInput = PayloadInputFixture.getDefaultCommandPayloadUpsertInput();
    upsertInput.setExternalId(payload.getExternalId());
    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(upsertInput)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.payload_execution_arch")
                .value(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES.name()));

    // -- With property architecture and null value
    upsertInput.setExecutionArch(null);
    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(upsertInput)))
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
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputWithOutputParser();

    mvc.perform(
            post(PAYLOAD_URI).contentType(MediaType.APPLICATION_JSON).content(asJsonString(input)))
        .andExpect(status().is2xxSuccessful());

    PayloadUpsertInput upsertInput =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInputWithOutputParser();
    upsertInput.setExternalId("external-id");

    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(upsertInput)))
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
    when(eeService.isEnterpriseLicenseInactive(any())).thenReturn(false);

    PayloadCreateInput input = PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    mvc.perform(
            post(PAYLOAD_URI).contentType(MediaType.APPLICATION_JSON).content(asJsonString(input)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_detection_remediations.length()").value(0));

    PayloadUpsertInput upsertInput =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInputWithDetectionRemediations();
    upsertInput.setExternalId("external-id");

    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(upsertInput)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload_detection_remediations.length()").value(3));
  }

  // -- CHECK CLEANUP AND EXECUTOR --

  @Test
  @DisplayName("Creating Command Line payload with both set executor and content should succeed")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithBothSetExecutorAndContent() throws Exception {
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    createInput.setExecutor("sh");
    createInput.setExecutor("echo hello world");

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Creating Command Line payload with both null cleanup executor and command should succeed")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithBothNullCleanupExecutorAndCommand() throws Exception {
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    createInput.setCleanupExecutor(null);
    createInput.setCleanupCommand(null);

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Creating Command Line payload with both set cleanup executor and command should succeed")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithBothSetCleanupExecutorAndCommand() throws Exception {
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    createInput.setCleanupExecutor("sh");
    createInput.setCleanupCommand("cleanup this mess");

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Creating Command Line payload with only set cleanup executor and null command should fail")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithOnlySetCleanupExecutorAndNullCommand() throws Exception {
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    createInput.setCleanupExecutor("sh");
    createInput.setCleanupCommand(null);

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "Creating Command Line payload with only set cleanup command and null executor should fail")
  @WithMockUser(isAdmin = true)
  void createCommandLinePayloadWithOnlySetCommandAndNullExecutor() throws Exception {
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    createInput.setCleanupExecutor(null);
    createInput.setCleanupCommand("cleanup this mess");

    mvc.perform(
            post(PAYLOAD_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "Updating Command Line payload with only set cleanup command and null executor should fail")
  @WithMockUser(isAdmin = true)
  void updateCommandLinePayloadWithOnlySetCommandAndNullExecutor() throws Exception {
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine();

    createInput.setCleanupExecutor(null);
    createInput.setCleanupCommand(null);

    String response =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var payloadId = JsonPath.read(response, "$.payload_id");

    PayloadUpdateInput updateInput = new PayloadUpdateInput();
    updateInput.setName("updated command line payload");
    updateInput.setContent("echo world again");
    updateInput.setExecutor("sh");
    updateInput.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux});

    updateInput.setCleanupCommand("cleanup this mess");

    mvc.perform(
            put(PAYLOAD_URI + "/" + payloadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "Duplicating a Community and Verified Payload should result in a Manual and Unverified Payload")
  @WithMockUser(isAdmin = true)
  void duplicateExecutablePayload() throws Exception {
    PayloadCreateInput createInput =
        PayloadInputFixture.createDefaultPayloadCreateInputForExecutable();
    createInput.setExecutableFile(EXECUTABLE_FILE.getId());
    createInput.setSource(Payload.PAYLOAD_SOURCE.COMMUNITY);
    createInput.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);

    String createdPayload =
        mvc.perform(
                post(PAYLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput)))
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

    mvc.perform(post(PAYLOAD_URI + "/" + payloadId + "/duplicate"))
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
    collectorCreateInput.setType("openbas_atomic_red_team");

    MockMultipartFile inputMultipart =
        new MockMultipartFile(
            "input",
            null,
            "application/json",
            objectMapper.writeValueAsString(collectorCreateInput).getBytes());

    mvc.perform(multipart("/api/collectors").file(inputMultipart))
        .andExpect(status().is2xxSuccessful());

    PayloadUpsertInput payloadUpsertInput1 =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInput();
    payloadUpsertInput1.setCollector(collectorId);
    payloadUpsertInput1.setExternalId("54e03fc3-e906-4b8e-865a-972e3e339d60");

    PayloadUpsertInput payloadUpsertInput2 =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInput();
    payloadUpsertInput2.setName("Command Payload 2");
    payloadUpsertInput2.setCollector(collectorId);
    payloadUpsertInput2.setExternalId("7a1ecc3c-3201-45cb-9a93-58405c0a680d");

    String upsertedPayload1 =
        mvc.perform(
                post(PAYLOAD_URI + "/upsert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(payloadUpsertInput1)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String payloadId1 = JsonPath.read(upsertedPayload1, "$.payload_id");

    String upsertedPayload2 =
        mvc.perform(
                post(PAYLOAD_URI + "/upsert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(payloadUpsertInput2)))
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
                .content(asJsonString(payloadsDeprecateInput)))
        .andExpect(status().is2xxSuccessful());

    mvc.perform(get(PAYLOAD_URI + "/" + payloadId1))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_name").value("My Command Payload"))
        .andExpect(jsonPath("$.payload_collector").value(collectorId))
        .andExpect(jsonPath("$.payload_status").value("DEPRECATED"));

    mvc.perform(get(PAYLOAD_URI + "/" + payloadId2))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.payload_name").value("Command Payload 2"))
        .andExpect(jsonPath("$.payload_collector").value(collectorId))
        .andExpect(jsonPath("$.payload_status").value("UNVERIFIED"));
  }
}
