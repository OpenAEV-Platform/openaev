package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.export.Mixins;
import io.openaev.utils.ZipUtils;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
public class ScenarioExportTest extends IntegrationTest {
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private EntityManager manager;

  @BeforeEach
  public void before() {
    scenarioComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    tagComposer.reset();
    documentComposer.reset();
  }

  private String getJsonExportFromZip(byte[] zipBytes, String entryName) throws IOException {
    return ZipUtils.getZipEntry(zipBytes, "%s.json".formatted(entryName), ZipUtils::streamToString);
  }

  @Nested
  @DisplayName("Scenario document export")
  class ScenarioDocumentExport {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_injectWithDirectDocument_should_includeDocumentInScenarioDocuments")
    public void given_injectWithDirectDocument_should_includeDocumentInScenarioDocuments()
        throws Exception {
      // Arrange
      DocumentComposer.Composer docComposer =
          documentComposer.forDocument(DocumentFixture.getDocumentJpeg());

      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .withInject(
                  injectComposer
                      .forInject(InjectFixture.getDefaultInject())
                      .withDocument(docComposer))
              .persist()
              .get();

      manager.flush();
      manager.clear();

      // Act
      byte[] response =
          mvc.perform(
                  get(SCENARIO_URI + "/" + scenario.getId() + "/export")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      String actualJson = getJsonExportFromZip(response, scenario.getName());

      // Assert — inject-attached document must appear in scenario_documents regardless of order
      String docId = docComposer.get().getId();
      assertThatJson(actualJson)
          .when(
              Option.IGNORING_ARRAY_ORDER,
              Option.IGNORING_EXTRA_FIELDS,
              Option.IGNORING_EXTRA_ARRAY_ITEMS)
          .node("scenario_documents")
          .isEqualTo("[{\"document_id\": \"%s\"}]".formatted(docId));
    }
  }

  @Nested
  @DisplayName("Scenario tag export")
  class ScenarioTagExport {

    private Scenario buildScenarioWithInjectorAndContractOutputTags(boolean withWorkflowStepLink) {
      String injectorContractTagName = "injector-contract-tag-" + java.util.UUID.randomUUID();
      String contractOutputTagName = "contract-output-tag-" + java.util.UUID.randomUUID();

      Tag injectorContractTag =
          tagComposer.forTag(TagFixture.getTagWithText(injectorContractTagName)).persist().get();
      Tag contractOutputTag =
          tagComposer.forTag(TagFixture.getTagWithText(contractOutputTagName)).persist().get();

      ContractOutputElement outputElement = OutputParserFixture.getContractOutputElementTypeIPv6();
      outputElement.setTags(Set.of(contractOutputTag));
      OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(outputElement));

      io.openaev.database.model.Payload payload = PayloadFixture.createDefaultCommand();
      payload.setOutputParsers(Set.of(outputParser));

      InjectorContractComposer.Composer injectorContractComposerRef =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
              .withTag(tagComposer.forTag(injectorContractTag))
              .withPayload(payloadComposer.forPayload(payload));

      ScenarioComposer.Composer scenarioComposerRef =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .withInject(
                  injectComposer
                      .forInject(InjectFixture.getDefaultInject())
                      .withInjectorContract(injectorContractComposerRef));

      if (withWorkflowStepLink) {
        Step step = StepFixture.getDefaultStepTemplate();
        step.setData(
            "{\"inject_injector_contract\":{\"injector_contract_id\":\"%s\"}}"
                .formatted(injectorContractComposerRef.get().getId()));
        Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
        workflowComposer
            .forWorkflow(workflowTemplate)
            .withScenario(scenarioComposerRef)
            .withStep(stepComposer.forStep(step))
            .persist();
      } else {
        scenarioComposerRef.persist();
      }

      return scenarioComposerRef.get();
    }

    private Set<String> extractScenarioTagNames(String actualJson) throws IOException {
      return mapper.readTree(actualJson).get("scenario_tags").findValuesAsText("tag_name").stream()
          .collect(Collectors.toSet());
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("When payloads have tags, scenario export has these tags")
    public void WhenPayloadsHaveTags_ScenarioExportHasTheseTags() throws Exception {

      ObjectMapper objectMapper = mapper.copy();
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("scenario tag")))
              .withInject(
                  injectComposer
                      .forInject(InjectFixture.getDefaultInject())
                      .withTag(tagComposer.forTag(TagFixture.getTagWithText("inject tag")))
                      .withInjectorContract(
                          injectorContractComposer
                              .forInjectorContract(
                                  InjectorContractFixture.createDefaultInjectorContract())
                              .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
                              .withTag(
                                  tagComposer.forTag(
                                      TagFixture.getTagWithText("this is a payload tag")))
                              .withPayload(
                                  payloadComposer.forPayload(
                                      PayloadFixture.createDefaultCommand()))))
              .persist()
              .get();

      manager.flush();
      manager.clear();

      byte[] response =
          mvc.perform(
                  get(SCENARIO_URI + "/" + scenario.getId() + "/export")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      String actualJson = getJsonExportFromZip(response, scenario.getName());

      objectMapper.addMixIn(Base.class, Mixins.Base.class);
      objectMapper.addMixIn(Tag.class, Mixins.Tag.class);
      String tagsJson =
          objectMapper.writeValueAsString(tagComposer.generatedItems.stream().toList());

      assertThatJson(actualJson)
          .when(Option.IGNORING_ARRAY_ORDER)
          .node("scenario_tags")
          .isArray()
          .isEqualTo(tagsJson);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "given_nonChainingScenario_when_exporting_should_includeInjectorContractAndContractOutputElementTags")
    void
        given_nonChainingScenario_when_exporting_should_includeInjectorContractAndContractOutputElementTags()
            throws Exception {
      Scenario scenario = buildScenarioWithInjectorAndContractOutputTags(false);
      manager.flush();
      manager.clear();

      byte[] response =
          mvc.perform(
                  get(SCENARIO_URI + "/" + scenario.getId() + "/export")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      Set<String> tagNames =
          extractScenarioTagNames(getJsonExportFromZip(response, scenario.getName()));
      assertTrue(
          tagNames.stream().anyMatch(name -> name.startsWith("injector-contract-tag-")),
          "scenario_tags must include injector_contract_tags");
      assertTrue(
          tagNames.stream().anyMatch(name -> name.startsWith("contract-output-tag-")),
          "scenario_tags must include contract_output_element_tags");
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "given_chainingScenario_when_exporting_should_includeInjectorContractAndContractOutputElementTags")
    void
        given_chainingScenario_when_exporting_should_includeInjectorContractAndContractOutputElementTags()
            throws Exception {
      Scenario scenario = buildScenarioWithInjectorAndContractOutputTags(true);
      manager.flush();
      manager.clear();

      byte[] response =
          mvc.perform(
                  get(SCENARIO_URI + "/" + scenario.getId() + "/export")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      String actualJson = getJsonExportFromZip(response, scenario.getName());
      Set<String> tagNames = extractScenarioTagNames(actualJson);
      assertTrue(
          tagNames.stream().anyMatch(name -> name.startsWith("injector-contract-tag-")),
          "scenario_tags must include injector_contract_tags when chaining");
      assertTrue(
          tagNames.stream().anyMatch(name -> name.startsWith("contract-output-tag-")),
          "scenario_tags must include contract_output_element_tags when chaining");
      assertThatJson(actualJson).node("scenario_injects").isArray().isEqualTo("[]");
    }
  }

  @Nested
  @DisplayName("Scenario chaining export")
  class ScenarioChainingExport {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_chainingScenario_should_not_export_scenarioInjects")
    void given_chainingScenario_should_not_export_scenarioInjects() throws Exception {
      // Arrange
      ScenarioComposer.Composer scenarioComposerRef =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()));
      Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
      workflowComposer.forWorkflow(workflowTemplate).withScenario(scenarioComposerRef).persist();
      Scenario scenario = scenarioComposerRef.get();
      manager.flush();
      manager.clear();

      // Act
      byte[] response =
          mvc.perform(
                  get(SCENARIO_URI + "/" + scenario.getId() + "/export")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      String actualJson = getJsonExportFromZip(response, scenario.getName());

      // Assert
      assertThatJson(actualJson).node("scenario_injects").isArray().isEqualTo("[]");
    }
  }

  @Nested
  @DisplayName("Scenario payload nullable export")
  class ScenarioPayloadNullableExport {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_payloadWithNullJsonLists_should_exportScenarioWithoutError")
    void given_payloadWithNullJsonLists_should_exportScenarioWithoutError() throws Exception {
      // Arrange
      Payload payload = PayloadFixture.createDefaultCommand();
      payload.setArguments(null);
      payload.setPrerequisites(null);
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .withInject(
                  injectComposer
                      .forInject(InjectFixture.getDefaultInject())
                      .withInjectorContract(
                          injectorContractComposer
                              .forInjectorContract(
                                  InjectorContractFixture.createDefaultInjectorContract())
                              .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                              .withPayload(payloadComposer.forPayload(payload))))
              .persist()
              .get();

      manager.flush();
      manager.clear();

      // Act
      byte[] response =
          mvc.perform(
                  get(SCENARIO_URI + "/" + scenario.getId() + "/export")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      String actualJson = getJsonExportFromZip(response, scenario.getName());

      // Assert
      assertThatJson(actualJson)
          .node("scenario_information.scenario_id")
          .isEqualTo(scenario.getId());
      assertThatJson(actualJson).node("scenario_injects").isArray().isNotEmpty();
    }
  }
}
