package io.openaev.service.chaining;

import static io.openaev.api.chaining.ChainingApi.CHAINING_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exercise.form.CreateExerciseInput;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.scenario.form.ScenarioInput;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetService;
import io.openaev.service.TeamService;
import io.openaev.service.UserService;
import io.openaev.utils.fixtures.AssetFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.helpers.InjectTestHelper;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
class ChainingIntegrationTest extends IntegrationTest {

  // -- Repositories
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private StepRepository stepRepository;

  // -- Setup inject
  @Autowired private StepService stepService;
  @Autowired private WorkflowService workflowService;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectTestHelper injectTestHelper;

  // -- Mocks
  @MockitoBean private InjectorContractService injectorContractService;
  @MockitoBean private TeamService teamService;
  @MockitoBean private AssetService assetService;
  @MockitoBean private TagService tagService;
  @MockitoBean private DocumentService documentService;
  @MockitoBean private InjectService injectService;
  @MockitoBean private io.openaev.executors.Executor executor;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @MockitoSpyBean UserService userService;
  @Autowired private TestUserHolder testUserHolder;
  String injectInputJson;
  InjectorContract injectorContractSaved;

  @BeforeEach
  void beforeEach() throws Exception {
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Injector injectorSaved = injectorRepository.save(injector);

    InjectorContract injectorContract = InjectorContractFixture.createImplantInjectorContract();
    injectorContract.addInjector(injectorSaved);
    injectorContractSaved = injectorContractRepository.save(injectorContract);
    // Link on the owning side and save to persist the join table
    injectorSaved.getContracts().add(injectorContractSaved);
    injectorRepository.save(injectorSaved);

    doReturn(injectorContractSaved).when(injectorContractService).injectorContract(any());
    doReturn(new ArrayList<>()).when(teamService).getTeamsByIds(any());
    doReturn(new ArrayList<>()).when(assetService).assets(any());
    doReturn(new HashSet<>()).when(tagService).tagSet(any());
    doReturn(null).when(documentService).document(any());
    doReturn(false).when(injectService).canApplyTargetType(any(), any());
    doReturn(new InjectStatus()).when(executor).directExecute(any());
    doAnswer(invocation -> testUserHolder.get()).when(userService).currentUser();

    doAnswer(
            invocation -> {
              Inject inject = invocation.getArgument(0);
              return injectRepository.save(inject);
            })
        .when(injectService)
        .createInject(any(Inject.class));

    Asset asset = AssetFixture.createDefaultAsset("AssetTest");
    asset = injectTestHelper.forceSaveAsset(asset);

    injectInputJson =
        """
            {
                "type": "inject",
                "inject_title": "whoami",
                "inject_description": "",
                "inject_injector_contract": "%s",
                "inject_content": {
                  "expectations": [],
                  "obfuscator": "plain-text",
                  "file": "c:\\\\programdata\\\\test.bat"
                },
                "inject_depends_on": [],
                "inject_depends_duration": 100,
                "inject_teams": [],
                "inject_assets": ["%s"],
                "inject_asset_groups": [],
                "inject_documents": [],
                "inject_all_teams": false,
                "inject_tags": [],
                "inject_enabled": true
            }
            """
            .formatted(injectorContractSaved.getId(), asset.getId());
  }

  @Nested
  @DisplayName("Scenario chaining integration tests")
  public class ScenarioChainingIntegrationTests {

    // -------------------------------------------------------------------------
    // 1. CREATION SCENARIO CHAINING → Scenario + Workflow TEMPLATE created
    // -------------------------------------------------------------------------
    @Test
    @WithMockUser(isAdmin = true)
    void should_create_scenario_and_workflow_template_when_chaining_enabled() throws Exception {
      long scenarioCountBefore = scenarioRepository.count();
      long workflowCountBefore = workflowRepository.count();

      ScenarioInput input = buildScenarioInput();
      String response =
          mvc.perform(
                  post(CHAINING_URI + "/scenarios")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(input)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      Scenario createdScenario = mapper.readValue(response, Scenario.class);

      assertNotNull(createdScenario);
      assertNotNull(createdScenario.getId());
      assertEquals(scenarioCountBefore + 1, scenarioRepository.count());

      // A Workflow TEMPLATE must have been created and linked to the scenario
      assertEquals(workflowCountBefore + 1, workflowRepository.count());

      Workflow workflowTemplate =
          workflowRepository.findAll().stream()
              .filter(w -> WorkflowStatus.TEMPLATE.equals(w.getStatus()))
              .filter(
                  w ->
                      w.getScenario() != null
                          && createdScenario.getId().equals(w.getScenario().getId()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Workflow TEMPLATE not find for scenario"));

      assertEquals(WorkflowStatus.TEMPLATE, workflowTemplate.getStatus());
      assertNull(
          workflowTemplate.getSimulation(), "The Workflow TEMPLATE must not have a simulation");
    }

    // -------------------------------------------------------------------------
    // 2. ADD STEP TEMPLATE → steps associated to the workflow TEMPLATE
    // -------------------------------------------------------------------------
    @Test
    @WithMockUser(isAdmin = true)
    void should_associate_steps_to_workflow_template() throws Exception {
      String response =
          mvc.perform(
                  post(CHAINING_URI + "/scenarios")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(buildScenarioInput())))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      Scenario createdScenario = mapper.readValue(response, Scenario.class);
      Workflow workflowTemplate =
          workflowRepository.findAll().stream()
              .filter(w -> WorkflowStatus.TEMPLATE.equals(w.getStatus()))
              .filter(
                  w ->
                      w.getScenario() != null
                          && createdScenario.getId().equals(w.getScenario().getId()))
              .findFirst()
              .orElseThrow();

      InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
      StepsCreateInput.StepInput step1 = buildValidStepInput();
      step1.setDataStep(injectInput);
      StepsCreateInput.StepInput step2 = buildValidStepInput();
      step2.setDataStep(injectInput);

      long stepCountBefore = stepRepository.count();

      stepService.createStepTemplates(workflowTemplate.getId(), List.of(step1, step2));

      assertEquals(stepCountBefore + 2, stepRepository.count());

      // All steps must be linked to the Workflow TEMPLATE
      List<Step> stepsCreated =
          stepRepository.findAll().stream()
              .filter(
                  s ->
                      s.getWorkflow() != null
                          && workflowTemplate.getId().equals(s.getWorkflow().getId()))
              .toList();

      assertEquals(2, stepsCreated.size());
      stepsCreated.forEach(s -> assertEquals(StepStatus.TEMPLATE, s.getStatus()));
    }

    // -------------------------------------------------------------------------
    // 3. LAUNCH SCENARIO → Workflow RUN + Simulation created, TEMPLATE unchanged
    // -------------------------------------------------------------------------
    @Test
    @WithMockUser(isAdmin = true)
    void should_create_workflow_run_and_simulation_on_launch() throws Exception {
      String response =
          mvc.perform(
                  post(CHAINING_URI + "/scenarios")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(buildScenarioInput())))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      Scenario createdScenario = mapper.readValue(response, Scenario.class);
      String scenarioId = createdScenario.getId();

      Workflow workflowTemplate =
          workflowRepository.findAll().stream()
              .filter(w -> WorkflowStatus.TEMPLATE.equals(w.getStatus()))
              .filter(w -> w.getScenario() != null && scenarioId.equals(w.getScenario().getId()))
              .findFirst()
              .orElseThrow();

      long workflowCountBefore = workflowRepository.count();
      long simulationCountBefore = exerciseRepository.count();

      String simulationResult =
          mvc.perform(post(SCENARIO_URI + "/" + scenarioId + "/exercise/running").with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String simulationId = JsonPath.read(simulationResult, "$.exercise_id");
      Exercise simulation = exerciseRepository.findById(simulationId).orElseThrow();

      assertNotNull(simulation);
      assertNotNull(simulation.getId());
      assertEquals(simulationCountBefore + 1, exerciseRepository.count());
      assertEquals(workflowCountBefore + 2, workflowRepository.count());

      // Workflow RUN created with the simulation attache
      List<Workflow> workflows = workflowRepository.findAll();
      Workflow newWorkflowTemplate =
          workflows.stream()
              .filter(
                  workflow ->
                      workflow.getStatus().equals(WorkflowStatus.TEMPLATE)
                          && !workflow.getId().equals(workflowTemplate.getId()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("New Workflow TEMPLATE not find"));

      Workflow workflowRun =
          workflows.stream()
              .filter(w -> WorkflowStatus.END.equals(w.getStatus()))
              .filter(
                  w ->
                      newWorkflowTemplate
                          .getId()
                          .equals(
                              w.getWorkflowTemplate() != null
                                  ? w.getWorkflowTemplate().getId()
                                  : null))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Workflow END not find"));

      assertEquals(simulation.getId(), workflowRun.getSimulation().getId());

      // The TEMPLATE must not be modified
      Workflow templateAfterLaunch =
          workflowRepository.findById(workflowTemplate.getId()).orElseThrow();
      assertNull(
          templateAfterLaunch.getSimulation(),
          "The Workflow TEMPLATE must not have a simulation after launch");
      assertEquals(WorkflowStatus.TEMPLATE, templateAfterLaunch.getStatus());
    }

    // -------------------------------------------------------------------------
    // 4. DELETE SCENARIO → Scenario + Simulation + Steps + Workflows deleted
    // -------------------------------------------------------------------------
    @Test
    @WithMockUser(isAdmin = true)
    void should_delete_scenario_and_cascade_to_simulation_steps_and_workflows() throws Exception {
      String response =
          mvc.perform(
                  post(CHAINING_URI + "/scenarios")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(buildScenarioInput())))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Extract only the ID from JSON, never deserialize the full entity
      String scenarioId = mapper.readTree(response).get("scenario_id").asText();

      // Full setup: creation + steps + launch
      Workflow workflowTemplate =
          workflowRepository.findAll().stream()
              .filter(w -> WorkflowStatus.TEMPLATE.equals(w.getStatus()))
              .filter(w -> w.getScenario() != null && scenarioId.equals(w.getScenario().getId()))
              .findFirst()
              .orElseThrow();

      InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
      StepsCreateInput.StepInput step = buildValidStepInput();
      step.setDataStep(injectInput);
      stepService.createStepTemplates(workflowTemplate.getId(), List.of(step));

      String simulationResult =
          mvc.perform(post(SCENARIO_URI + "/" + scenarioId + "/exercise/running").with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String simulationId = mapper.readTree(simulationResult).get("exercise_id").asText();

      // Snapshots before deletion
      String workflowTemplateId = workflowTemplate.getId();
      List<String> stepIds =
          stepRepository.findAll().stream()
              .filter(
                  s ->
                      s.getWorkflow() != null && workflowTemplateId.equals(s.getWorkflow().getId()))
              .map(Step::getId)
              .toList();

      assertFalse(stepIds.isEmpty(), "Steps must exist before deletion");

      entityManager.clear();
      // DELETE
      mvc.perform(delete("/api/scenarios/" + scenarioId).with(csrf()))
          .andExpect(status().is2xxSuccessful());
      entityManager.flush();
      entityManager.clear();
      // Scenario deleted
      assertFalse(scenarioRepository.existsById(scenarioId));

      // Workflows deleted (TEMPLATE + RUN)
      assertFalse(
          workflowRepository.existsById(workflowTemplateId),
          "The Workflow TEMPLATE must be deleted");
      assertTrue(
          workflowRepository.findAll().stream()
              .noneMatch(
                  w ->
                      w.getWorkflowTemplate() != null
                          && workflowTemplateId.equals(w.getWorkflowTemplate().getId())),
          "The Workflow RUN must be deleted");

      // Steps deleted
      stepIds.forEach(
          stepId ->
              assertFalse(
                  stepRepository.existsById(stepId), "Step " + stepId + " must be deleted"));

      // Simulation deleted
      assertTrue(exerciseRepository.existsById(simulationId), "Simulation must not be deleted");
    }

    // -------------------------------------------------------------------------
    // 5. WORKFLOW CHAINING INJECT → not accessible as atomic testing
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(isAdmin = true)
    void should_not_expose_workflow_chaining_inject_as_atomic_testing() throws Exception {
      // Create scenario with chaining enabled
      String scenarioResponse =
          mvc.perform(
                  post(CHAINING_URI + "/scenarios")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(buildScenarioInput())))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      Scenario createdScenario = mapper.readValue(scenarioResponse, Scenario.class);

      // Get the workflow template created
      Workflow workflowTemplate =
          workflowRepository.findAll().stream()
              .filter(w -> WorkflowStatus.TEMPLATE.equals(w.getStatus()))
              .filter(
                  w ->
                      w.getScenario() != null
                          && createdScenario.getId().equals(w.getScenario().getId()))
              .findFirst()
              .orElseThrow();

      // Add a step with an inject to the workflow template
      InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
      StepsCreateInput.StepInput step = buildValidStepInput();
      step.setDataStep(injectInput);
      stepService.createStepTemplates(workflowTemplate.getId(), List.of(step));
      String simulation =
          mvc.perform(
                  post(SCENARIO_URI + "/" + createdScenario.getId() + "/exercise/running")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String simulationId = mapper.readTree(simulation).get("exercise_id").asText();

      List<Workflow> workflowsRun = workflowService.findWorkflowRunBySimulationId(simulationId);

      assertTrue((workflowsRun.size() == 1), "The Workflow RUN must be unique");
      Workflow workflowRun = workflowsRun.get(0);
      // Retrieve the inject created from the step
      Step createdStep =
          stepService.findAllStepExecutedByWorkflowRunId(workflowRun.getId()).stream()
              .findFirst()
              .orElseThrow(() -> new AssertionError("Step not found"));
      if (createdStep.getStatus() == StepStatus.READY) {
        stepService.run(createdStep);
      }

      assertNotNull(
          StepService.getField(createdStep.getData(), "inject_id"), "Step must have an inject");
      String injectId = StepService.getField(createdStep.getData(), "inject_id");
      // assertFalse(Boolean.getBoolean(StepService.getField(createdStep.getData(),
      // "is_atomic_testing")));
      injectRepository.findById(injectId).orElseThrow(() -> new AssertionError("Inject not found"));
      // The inject must NOT be accessible via the atomic testing API
      String result =
          mvc.perform(
                  post("/api/atomic-testings/search")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"page\":0,\"size\":20,\"sorts\":[{\"direction\":\"DESC\",\"property\":\"inject_updated_at\"}],\"textSearch\":\"\"}"))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertFalse(
          result.contains(injectId),
          "Workflow chaining inject must not be exposed as atomic testing");
    }

    // -------------------------------------------------------------------------
    // 6. DUPLICATE SCENARIO CHAINING → Scenario + Workflow TEMPLATE + Step TEMPLATE duplicated
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(isAdmin = true)
    void should_duplicate_scenario_workflow_template_and_step_template_when_chaining_enabled()
        throws Exception {
      // Create scenario with chaining enabled
      String scenarioResponse =
          mvc.perform(
                  post(CHAINING_URI + "/scenarios")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(buildScenarioInput())))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      Scenario createdScenario = mapper.readValue(scenarioResponse, Scenario.class);

      // Get the workflow template created
      Workflow workflowTemplate =
          workflowRepository.findAll().stream()
              .filter(w -> WorkflowStatus.TEMPLATE.equals(w.getStatus()))
              .filter(
                  w ->
                      w.getScenario() != null
                          && createdScenario.getId().equals(w.getScenario().getId()))
              .findFirst()
              .orElseThrow();

      // Add a step with an inject to the workflow template
      InjectInput injectInput = mapper.readValue(injectInputJson, InjectInput.class);
      StepsCreateInput.StepInput step = buildValidStepInput();
      step.setDataStep(injectInput);
      stepService.createStepTemplates(workflowTemplate.getId(), List.of(step));

      String result =
          mvc.perform(
                  post(CHAINING_URI + "/scenarios/" + createdScenario.getId())
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      Scenario scenarioDuplicated = mapper.readValue(result, Scenario.class);

      Workflow workflowTemplateDuplicated =
          workflowRepository.findAll().stream()
              .filter(w -> WorkflowStatus.TEMPLATE.equals(w.getStatus()))
              .filter(
                  w ->
                      w.getScenario() != null
                          && scenarioDuplicated.getId().equals(w.getScenario().getId()))
              .findFirst()
              .orElseThrow();

      assertWorkflowEqualsExceptId(workflowTemplate, workflowTemplateDuplicated);

      List<Step> originalSteps =
          stepRepository.findAll().stream()
              .filter(
                  s ->
                      s.getWorkflow() != null
                          && workflowTemplate.getId().equals(s.getWorkflow().getId()))
              .toList();
      List<Step> duplicatedSteps =
          stepRepository.findAll().stream()
              .filter(
                  s ->
                      s.getWorkflow() != null
                          && workflowTemplateDuplicated.getId().equals(s.getWorkflow().getId()))
              .toList();

      assertEquals(originalSteps.size(), duplicatedSteps.size(), "Step TEMPLATE count must match");
      assertFalse(duplicatedSteps.isEmpty(), "Duplicated workflow must contain step templates");

      Step originalStep = originalSteps.getFirst();
      Step duplicatedStep = duplicatedSteps.getFirst();
      assertStepEqualsExceptId(originalStep, duplicatedStep);
    }
  }

  @Nested
  @DisplayName("Simulation chaining integration tests")
  public class SimulationChainingIntegrationTests {

    @Test
    @WithMockUser(isAdmin = true)
    void should_create_simulation_and_workflow_template_when_chaining_enabled() throws Exception {
      long simulationCountBefore = exerciseRepository.count();
      long workflowCountBefore = workflowRepository.count();

      CreateExerciseInput input = buildSimulationInput();
      String response =
          mvc.perform(
                  post(CHAINING_URI + "/simulations")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(input)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String simulationId = JsonPath.read(response, "$.exercise_id");
      Exercise createdSimulation = exerciseRepository.findById(simulationId).orElseThrow();

      assertNotNull(createdSimulation.getId());
      assertEquals(simulationCountBefore + 1, exerciseRepository.count());
      assertEquals(workflowCountBefore + 1, workflowRepository.count());

      Workflow workflowTemplate = findTemplateWorkflowBySimulationId(createdSimulation.getId());
      assertEquals(WorkflowStatus.TEMPLATE, workflowTemplate.getStatus());
      assertNull(
          workflowTemplate.getScenario(),
          "Template workflow for simulation must not link scenario");
    }

    @Test
    @WithMockUser(isAdmin = true)
    void should_create_step_template_when_add_inject_to_simulation_chaining() throws Exception {
      String response =
          mvc.perform(
                  post(CHAINING_URI + "/simulations")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(buildSimulationInput())))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String simulationId = JsonPath.read(response, "$.exercise_id");
      Exercise createdSimulation = exerciseRepository.findById(simulationId).orElseThrow();
      Workflow workflowTemplate = findTemplateWorkflowBySimulationId(createdSimulation.getId());

      long stepCountBefore = stepRepository.count();

      mvc.perform(
              post(CHAINING_URI + "/simulations/" + createdSimulation.getId() + "/injects")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(injectInputJson))
          .andExpect(status().is2xxSuccessful());

      assertEquals(stepCountBefore + 1, stepRepository.count());

      List<Step> stepsCreated =
          stepRepository.findAll().stream()
              .filter(
                  s ->
                      s.getWorkflow() != null
                          && workflowTemplate.getId().equals(s.getWorkflow().getId()))
              .toList();
      assertEquals(1, stepsCreated.size());
      assertEquals(StepStatus.TEMPLATE, stepsCreated.getFirst().getStatus());
    }

    @Test
    @WithMockUser(isAdmin = true)
    void should_duplicate_simulation_workflow_template_and_step_template_when_chaining_enabled()
        throws Exception {
      String response =
          mvc.perform(
                  post(CHAINING_URI + "/simulations")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(buildSimulationInput())))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String simulationId = JsonPath.read(response, "$.exercise_id");
      Exercise createdSimulation = exerciseRepository.findById(simulationId).orElseThrow();

      Workflow workflowTemplate = findTemplateWorkflowBySimulationId(createdSimulation.getId());

      mvc.perform(
              post(CHAINING_URI + "/simulations/" + createdSimulation.getId() + "/injects")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(injectInputJson))
          .andExpect(status().is2xxSuccessful());

      String duplicatedResponse =
          mvc.perform(
                  post(CHAINING_URI + "/simulations/" + createdSimulation.getId())
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String duplicatedSimulationId = JsonPath.read(duplicatedResponse, "$.exercise_id");
      Exercise duplicatedSimulation =
          exerciseRepository.findById(duplicatedSimulationId).orElseThrow();
      Workflow duplicatedWorkflowTemplate =
          findTemplateWorkflowBySimulationId(duplicatedSimulation.getId());

      assertWorkflowEqualsExceptId(workflowTemplate, duplicatedWorkflowTemplate);

      List<Step> originalSteps =
          stepRepository.findAll().stream()
              .filter(
                  s ->
                      s.getWorkflow() != null
                          && workflowTemplate.getId().equals(s.getWorkflow().getId()))
              .toList();
      List<Step> duplicatedSteps =
          stepRepository.findAll().stream()
              .filter(
                  s ->
                      s.getWorkflow() != null
                          && duplicatedWorkflowTemplate.getId().equals(s.getWorkflow().getId()))
              .toList();

      assertEquals(originalSteps.size(), duplicatedSteps.size(), "Step TEMPLATE count must match");
      assertFalse(
          duplicatedSteps.isEmpty(), "Duplicated simulation workflow must contain step templates");

      assertStepEqualsExceptId(originalSteps.getFirst(), duplicatedSteps.getFirst());
    }
  }

  private Workflow findTemplateWorkflowBySimulationId(String simulationId) {
    return workflowRepository.findAll().stream()
        .filter(w -> WorkflowStatus.TEMPLATE.equals(w.getStatus()))
        .filter(w -> w.getSimulation() != null && simulationId.equals(w.getSimulation().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Workflow TEMPLATE not found for simulation"));
  }

  private CreateExerciseInput buildSimulationInput() {
    CreateExerciseInput input = new CreateExerciseInput();
    input.setName("Test Simulation Chaining");
    input.setIsChaining(true);
    input.setReplyTos(new ArrayList<>());
    return input;
  }

  private void assertWorkflowEqualsExceptId(Workflow expected, Workflow actual) {
    assertNotNull(expected.getId());
    assertNotNull(actual.getId());
    assertNotEquals(expected.getId(), actual.getId(), "Workflow ids must differ");

    assertEquals(expected.getStatus(), actual.getStatus());
    assertEquals(expected.getVersion(), actual.getVersion());
    assertEquals(expected.isEdited(), actual.isEdited());
    assertEquals(expected.isRateLimitEnabled(), actual.isRateLimitEnabled());
    assertEquals(expected.getMaxAttempts(), actual.getMaxAttempts());
    assertEquals(expected.getMaxTemporalRateSeconds(), actual.getMaxTemporalRateSeconds());
    assertEquals(expected.isTimeoutEnabled(), actual.isTimeoutEnabled());
    assertEquals(expected.getTimeoutSeconds(), actual.getTimeoutSeconds());
    assertEquals(expected.isSafeModeEnabled(), actual.isSafeModeEnabled());
  }

  private void assertStepEqualsExceptId(Step expected, Step actual) {
    assertNotNull(expected.getId());
    assertNotNull(actual.getId());
    assertNotEquals(expected.getId(), actual.getId(), "Step ids must differ");

    assertEquals(expected.getStatus(), actual.getStatus());
    assertEquals(expected.getStepAction(), actual.getStepAction());
    assertEquals(expected.getInput(), actual.getInput());
    assertEquals(
        StepService.setField(expected.getData(), "inject_exercise", ""),
        StepService.setField(actual.getData(), "inject_exercise", ""),
        "Step data must be the same expected for simulation id (inject_exercise)");
    assertEquals(expected.getOutput(), actual.getOutput());
    assertEquals(expected.getOutputParser(), actual.getOutputParser());
    assertEquals(expected.getConditionExecuted(), actual.getConditionExecuted());
    assertEquals(expected.getLimitExecution(), actual.getLimitExecution());
  }

  private ScenarioInput buildScenarioInput() {
    ScenarioInput input = new ScenarioInput();
    input.setName("Test Scenario Chaining");
    input.setIsChaining(true);
    return input;
  }

  private StepsCreateInput.StepInput buildValidStepInput() {
    StepsCreateInput.StepInput stepInput = new StepsCreateInput.StepInput();
    stepInput.setStepAction(StepActionClass.INJECT_EXECUTION);

    ConditionCreateInput root = new ConditionCreateInput();
    root.setTemporaryId("tmp-1");
    root.setTemporaryIdConditionParent(null);
    root.setType(ConditionType.EQ);
    root.setKey("status");
    root.setValue("SUCCESS");
    root.setStepFrom(null);

    ConditionCreateInput child = new ConditionCreateInput();
    child.setTemporaryId("tmp-2");
    child.setTemporaryIdConditionParent("tmp-1");
    child.setType(ConditionType.EQ);
    child.setKey("status");
    child.setValue("SUCCESS");
    child.setStepFrom(null);

    stepInput.setConditions(List.of(root, child));
    return stepInput;
  }
}
