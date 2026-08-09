package io.openaev.importer;

import static io.openaev.service.chaining.WorkflowService.DEFAULT_TIMEOUT_SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.ee.EnterpriseEditionException;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_METHOD)
class V1_DataImporterTest extends IntegrationTest {

  @Autowired private V1_DataImporter importer;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private AttackPatternRepository attackPatternRepository;
  @Autowired private KillChainPhaseRepository killChainPhaseRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private DomainRepository domainRepository;
  @Autowired private DomainService domainService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private ConditionRepository conditionRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private JsonNode importNode;

  public static final String EXERCISE_NAME =
      "Test Exercise%s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX);
  public static final String TEAM_NAME = "Animation team";
  public static final String USER_EMAIL = "Romuald.Lemesle@openaev.io";
  public static final String ORGANIZATION_NAME = "Filigran";
  public static final String TAG_NAME = "crisis exercise";
  public static final String ATTACK_PATTERN_EXTERNAL_ID = "ATTACK_PATTERN_EXTERNAL_ID";
  public static final String KILLCHAIN_EXTERNAL_ID = "KILLCHAIN_EXTERNAL_ID";
  public static final String PAYLOAD_EXTERNAL_ID = "PAYLOAD_EXTERNAL_ID";
  public static final String NMAP_DUMMY_INJECTOR_TYPE = "openaev_nmap_dummy";

  @BeforeEach
  void cleanBefore() throws IOException {
    killChainPhaseRepository.deleteAll();
    attackPatternRepository.deleteAll();
    exerciseRepository.deleteAll();
    scenarioRepository.deleteAll();
    injectRepository.deleteAll();
    injectorContractRepository.deleteAll();
    injectorRepository.deleteAll();
    MockitoAnnotations.openMocks(this);
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(Paths.get("src/test/resources/importer-v1/import-data.json")));
    this.importNode = mapper.readTree(jsonContent);
  }

  @Test
  @Transactional
  void testImportData() {
    // -- EXECUTE --
    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- ASSERT --
    Optional<Exercise> exercise = this.exerciseRepository.findOne(exerciseByName(EXERCISE_NAME));
    assertTrue(exercise.isPresent());

    Optional<Team> team = this.teamRepository.findByName(TEAM_NAME);
    assertTrue(team.isPresent());
    assertEquals(1, team.get().getUsersNumber());
    assertEquals(ORGANIZATION_NAME, team.get().getOrganization().getName());
    assertEquals(1, team.get().getTags().size());

    Optional<User> user = this.userRepository.findByEmailIgnoreCase(USER_EMAIL);
    assertTrue(user.isPresent());
    assertEquals(ORGANIZATION_NAME, user.get().getOrganization().getName());
    assertEquals(1, user.get().getTags().size());

    List<Organization> organization =
        this.organizationRepository.findByNameIgnoreCase(ORGANIZATION_NAME);
    assertFalse(organization.isEmpty());
    assertEquals(ORGANIZATION_NAME, organization.getFirst().getName());

    List<Tag> tag = this.tagRepository.findByNameIgnoreCase(TAG_NAME);
    assertFalse(tag.isEmpty());
    assertEquals(TAG_NAME, tag.getFirst().getName());
  }

  @Test
  @Transactional
  void testScenario_with_attackpattern() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    MockitoAnnotations.openMocks(this);
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/import-scenario-with-attack-pattern.json")));
    this.importNode = mapper.readTree(jsonContent);
    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    Payload payload = payloadRepository.findAll().iterator().next();
    InjectorContract injectorContract =
        injectorContractRepository.findInjectorContractByPayload(payload).orElseThrow();

    // the scenario should have one inject with one attack pattern with one killchain
    AttackPattern attackPattern = injectorContract.getAttackPatterns().getFirst();

    KillChainPhase killChainPhase = attackPattern.getKillChainPhases().getFirst();
    assertEquals(ATTACK_PATTERN_EXTERNAL_ID, attackPattern.getExternalId());
    assertEquals(KILLCHAIN_EXTERNAL_ID, killChainPhase.getExternalId());

    // delete scenario and payload before reimporting to verify that the killchainphase is not
    // recreated
    // Clear the persistence context to avoid TransientObjectException from stale references
    entityManager.flush();
    entityManager.clear();
    // Delete in FK order: injects → injector contracts → scenarios → payloads
    injectRepository.deleteAll();
    injectorContractRepository.deleteAll();
    scenarioRepository.deleteAll();
    payloadRepository.deleteAll();
    entityManager.flush();
    entityManager.clear();
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());

    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    payload = payloadRepository.findAll().iterator().next();
    InjectorContract injectorContract2 =
        injectorContractRepository.findInjectorContractByPayload(payload).orElseThrow();
    AttackPattern attackPattern2 = injectorContract2.getAttackPatterns().getFirst();
    KillChainPhase killChainPhase2 = attackPattern.getKillChainPhases().getFirst();

    // verify that the new payload use the same attack pattern / killchain phase
    assertEquals(attackPattern.getId(), attackPattern2.getId());
    assertEquals(killChainPhase.getId(), killChainPhase2.getId());
  }

  @Test
  @Transactional
  void
      testScenario_given_injects_nuclei_without_nuclei_injector_registered_when_starterpack_then_should_create_injectorless_contract()
          throws IOException {

    MockitoAnnotations.openMocks(this);
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/scenario_with_injects_from_injector.json")));
    this.importNode = mapper.readTree(jsonContent);
    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // the contract should be created without any injector link (no placeholder injector):
    // the real injector adopts it by id when it registers
    InjectorContract importedContract =
        this.injectorContractRepository
            .findById("93d27459-68d0-43b1-ad65-eacc3cfa5cf7")
            .orElseThrow();
    assertTrue(importedContract.getInjectors().isEmpty());
    assertTrue(
        this.injectorRepository
            .findByTypeAndTenantId(NMAP_DUMMY_INJECTOR_TYPE, TenantContext.getCurrentTenant())
            .isEmpty());
  }

  @Test
  @Transactional
  void
      testScenario_given_payloadInject_without_payloadInjector_registered_when_starterpack_then_should_attach_payload_to_contract()
          throws IOException {
    // -- PREPARE --
    // Fresh platform: no payload-supporting injector is registered. The starter-pack import
    // creates the payload and must carry it onto the injector-less contract (regression: the
    // contract was persisted without its payload, showing a question mark / "no payload
    // attached").
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/import-starterpack-scenario-with-payload.json")));
    this.importNode = mapper.readTree(jsonContent);

    // -- EXECUTE --
    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- ASSERT --
    InjectorContract importedContract =
        this.injectorContractRepository
            .findById("6356f1c3-4152-4cfe-a595-cddcd1d9d233")
            .orElseThrow();
    // The created payload is attached to the starter-pack contract, not orphaned
    assertNotNull(
        importedContract.getPayload(),
        "The starter-pack contract must carry the payload created during import");
    assertEquals("Cleanup artifacts", importedContract.getPayload().getName());
    // No injector link yet: the payload injector adopts the contract when it registers
    assertTrue(importedContract.getInjectors().isEmpty());
    // No payload is left orphaned by the import
    for (Payload payload : payloadRepository.findAll()) {
      assertTrue(
          injectorContractRepository.findInjectorContractByPayload(payload).isPresent(),
          "Payload '" + payload.getName() + "' must be referenced by an injector contract");
    }
  }

  @Test
  @Transactional
  void testImportXTMHubScenarios() throws IOException {
    MockitoAnnotations.openMocks(this);

    ObjectMapper mapper = new ObjectMapper();
    Path xtmHubScenariosDir = Paths.get("src/test/resources/xtmhub-scenarios");

    List<Path> xtmScenariosFilesPath =
        Files.list(xtmHubScenariosDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".json"))
            .toList();

    for (Path xtmScenariosFilePath : xtmScenariosFilesPath) {
      String jsonContent = Files.readString(xtmScenariosFilePath);
      JsonNode importNode = mapper.readTree(jsonContent);
      this.importer.importData(
          importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    }
  }

  @Test
  @Transactional
  void test_empty() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/payload-json-for-domain-tests/payload_with_no_domain.json")));
    this.importNode = mapper.readTree(jsonContent);

    Domain domainToClassify =
        domainRepository.findByName(PresetDomain.getToClassify().getName()).orElseThrow();

    Set<Domain> importDomain =
        this.importer.mergeDomains(new HashMap<>(), this.importNode, "payload_", null, null);

    assertEquals(1, importDomain.size());
    assertEquals(domainToClassify.getId(), importDomain.stream().findFirst().get().getId());
  }

  @Test
  @Transactional
  void testMergeDomains_givenTextualInjectorContractDomainIds_shouldResolveExistingDomains() {
    // -- Arrange --
    Domain existingDomain =
        domainRepository.findByName(PresetDomain.getToClassify().getName()).orElseThrow();
    ObjectNode injectorContractNode = new ObjectMapper().createObjectNode();
    injectorContractNode.set(
        "injector_contract_domains",
        new ObjectMapper().createArrayNode().add(existingDomain.getId()));

    // -- Act --
    Set<Domain> mergedDomains =
        importer.mergeDomains(
            new HashMap<>(), injectorContractNode, "injector_contract_", null, null);

    // -- Assert --
    assertEquals(1, mergedDomains.size());
    assertEquals(existingDomain.getId(), mergedDomains.stream().findFirst().orElseThrow().getId());
  }

  @Test
  @Transactional
  void testImportScenario_givenPayloadWithMissingArrayFields_shouldImportWithoutError()
      throws IOException {
    // -- PREPARE --
    // Fixture has no payload_arguments or payload_prerequisites keys at all
    // buildPayload must fall back to safe empty iterables via safeArray() without NPE.
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/import-scenario-payload-missing-arrays.json")));
    this.importNode = mapper.readTree(jsonContent);

    // -- EXECUTE --
    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- ASSERT --
    List<Payload> payloads = new ArrayList<>();
    payloadRepository.findAll().forEach(payloads::add);
    assertFalse(payloads.isEmpty(), "Payload should have been created");
    Payload payload = payloads.getFirst();
    assertEquals("echo missing arrays", payload.getName());
    // No NPE: missing array fields should result in empty/null collections, not an exception
    List<PayloadArgument> arguments = payload.getArguments();
    List<PayloadPrerequisite> prerequisites = payload.getPrerequisites();
    assertTrue(
        arguments == null || arguments.isEmpty(),
        "Arguments should be empty when field is absent from JSON");
    assertTrue(
        prerequisites == null || prerequisites.isEmpty(),
        "Prerequisites should be empty when field is absent from JSON");
  }

  @Test
  @Transactional
  void testImportScenario_givenPayloadWithExplicitNullArrayFields_shouldImportWithoutError()
      throws IOException {
    // -- PREPARE --
    // Fixture has payload_arguments and payload_prerequisites set to JSON null
    // (payload_platforms is provided because it is @NotEmpty on the entity).
    // buildPayload must handle null nodes via safeArray() without NPE or ClassCastException.
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/import-scenario-payload-null-arrays.json")));
    this.importNode = mapper.readTree(jsonContent);

    // -- EXECUTE --
    this.importer.importData(
        this.importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- ASSERT --
    List<Payload> payloads = new ArrayList<>();
    payloadRepository.findAll().forEach(payloads::add);
    assertFalse(payloads.isEmpty(), "Payload should have been created");
    Payload payload = payloads.getFirst();
    assertEquals("echo null arrays", payload.getName());
    // No NPE/ClassCastException: explicit null arrays should result in empty/null collections
    List<PayloadArgument> arguments = payload.getArguments();
    List<PayloadPrerequisite> prerequisites = payload.getPrerequisites();
    assertTrue(
        arguments == null || arguments.isEmpty(),
        "Arguments should be empty when field is explicit null in JSON");
    assertTrue(
        prerequisites == null || prerequisites.isEmpty(),
        "Prerequisites should be empty when field is explicit null in JSON");
  }

  @Test
  @Transactional
  void given_scenarioWithWorkflow_when_enterpriseLicenseInactive_should_failImport()
      throws Exception {
    // -- Arrange --
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(true);
    JsonNode workflowImport =
        new ObjectMapper()
            .readTree(
                Files.readAllBytes(
                    Paths.get(
                        "src/test/resources/importer-v1/import-scenario-with-workflow.json")));

    // -- Act & Assert --
    assertThrows(
        EnterpriseEditionException.class,
        () ->
            importer.importData(
                workflowImport,
                Map.of(),
                null,
                null,
                null,
                null,
                Constants.IMPORTED_OBJECT_NAME_SUFFIX));
  }

  @Test
  @Transactional
  void given_scenarioWithWorkflow_should_importWorkflowStepsAndConditions() throws Exception {
    // -- Arrange --
    JsonNode workflowImport =
        new ObjectMapper()
            .readTree(
                Files.readAllBytes(
                    Paths.get(
                        "src/test/resources/importer-v1/import-scenario-with-workflow.json")));

    // -- Act --
    this.importer.importData(
        workflowImport, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    String expectedName = "test workflow import%s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    Scenario scenario =
        scenarioRepository.findAll().stream()
            .filter(s -> expectedName.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    assertTrue(injectRepository.findByScenarioId(scenario.getId()).isEmpty());

    // Workflow
    List<Workflow> workflows =
        workflowRepository.findByScenario_IdAndStatus(scenario.getId(), WorkflowStatus.TEMPLATE);
    assertEquals(1, workflows.size());
    Workflow workflow = workflows.getFirst();
    assertEquals(2, workflow.getVersion());
    assertTrue(workflow.isRateLimitEnabled());
    assertEquals(5, workflow.getMaxAttempts());
    assertTrue(workflow.isTimeoutEnabled());
    assertEquals(300L, workflow.getTimeoutSeconds());
    assertEquals(1, workflow.getWorkflowScopeRules().size());
    assertEquals(1, workflow.getWorkflowScopeVariables().size());

    // Steps
    List<Step> steps = stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());
    assertEquals(2, steps.size());
    assertTrue(steps.stream().allMatch(s -> s.getStatus() == StepStatus.TEMPLATE));

    Step step1 = steps.stream().filter(s -> s.getLimitExecution() == 3).findFirst().orElseThrow();
    Step step2 = steps.stream().filter(s -> s.getLimitExecution() == 1).findFirst().orElseThrow();

    // Conditions on step 1: linked root
    List<Condition> conds1 = conditionRepository.findAllLinkedToStepId(step1.getId());
    assertEquals(1, conds1.size());
    Condition root = conds1.getFirst();
    assertNull(root.getConditionParent());
    assertEquals(ConditionType.EQ, root.getType());
    assertEquals("SUCCESS", root.getValue());

    Condition child =
        conditionRepository.findAll().stream()
            .filter(condition -> workflow.getId().equals(condition.getWorkflowId()))
            .filter(condition -> condition.getConditionParent() != null)
            .filter(condition -> root.getId().equals(condition.getConditionParent().getId()))
            .findFirst()
            .orElseThrow();
    assertEquals("child", child.getName());

    // Condition on step 2: references step 1 via stepFrom
    List<Condition> conds2 = conditionRepository.findAllLinkedToStepId(step2.getId());
    assertEquals(1, conds2.size());
    assertEquals(step1.getId(), conds2.getFirst().getStepFrom().getId());
  }

  @Test
  @Transactional
  void given_scenarioWithWorkflowContainingAssetScopeRules_should_importOnlyNonAssetScopeRules()
      throws Exception {
    // -- Arrange --
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode workflowImport =
        objectMapper.readTree(
            Files.readAllBytes(
                Paths.get("src/test/resources/importer-v1/import-scenario-with-workflow.json")));
    ArrayNode scopeRules =
        (ArrayNode) workflowImport.get("scenario_workflow").get("workflow_scope_rules");

    ObjectNode assetRule = objectMapper.createObjectNode();
    assetRule.put("workflow_scope_rule_selected_mode", "ALLOWLIST");
    assetRule.put("workflow_scope_rule_source", "ASSET");
    assetRule.put("workflow_scope_rule_value", "asset-id-1");
    assetRule.put("workflow_scope_rule_value_type", "ASSET_ID");
    scopeRules.add(assetRule);

    ObjectNode assetGroupRule = objectMapper.createObjectNode();
    assetGroupRule.put("workflow_scope_rule_selected_mode", "ALLOWLIST");
    assetGroupRule.put("workflow_scope_rule_source", "ASSET_GROUP");
    assetGroupRule.put("workflow_scope_rule_value", "asset-group-id-1");
    assetGroupRule.put("workflow_scope_rule_value_type", "ASSET_GROUP_ID");
    scopeRules.add(assetGroupRule);

    // -- Act --
    this.importer.importData(
        workflowImport, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    String expectedName = "test workflow import%s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    Scenario scenario =
        scenarioRepository.findAll().stream()
            .filter(s -> expectedName.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    Workflow workflow =
        workflowRepository
            .findByScenario_IdAndStatus(scenario.getId(), WorkflowStatus.TEMPLATE)
            .getFirst();

    assertEquals(1, workflow.getWorkflowScopeRules().size());
    assertTrue(
        workflow.getWorkflowScopeRules().stream()
            .noneMatch(
                rule ->
                    ScopeRuleSource.ASSET.equals(rule.getRuleSource())
                        || ScopeRuleSource.ASSET_GROUP.equals(rule.getRuleSource())));
  }

  @Test
  @Transactional
  void
      given_stepDataWithObjectContract_when_resolvingMappedContract_should_preserveContractObjectShape() {
    // -- Arrange --
    String oldContractId = UUID.randomUUID().toString();
    String newContractId = UUID.randomUUID().toString();
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.put(
        "step_data",
        """
        {
          "inject_injector_contract": {
            "injector_contract_id": "%s",
            "injector_contract_payload": {"payload_type":"COMMAND"}
          }
        }
        """
            .formatted(oldContractId));
    Map<String, String> resolvedContracts = Map.of(oldContractId, newContractId);
    Exercise simulation = new Exercise();
    simulation.setId("sim-test");
    Workflow workflow = Workflow.builder().simulation(simulation).build();

    // -- Act --
    V1_DataImporter.StepDataResolution resolution =
        ReflectionTestUtils.invokeMethod(
            importer, "resolveStepData", stepNode, resolvedContracts, new HashMap<>(), workflow);
    String resolvedStepData = resolution.stepData();
    JsonNode resolvedJson = assertDoesNotThrow(() -> objectMapper.readTree(resolvedStepData));

    // -- Assert --
    assertTrue(resolvedJson.get("inject_injector_contract").isObject());
    assertEquals(
        newContractId,
        resolvedJson.get("inject_injector_contract").get("injector_contract_id").asText());
    assertEquals(
        "COMMAND",
        resolvedJson
            .get("inject_injector_contract")
            .get("injector_contract_payload")
            .get("payload_type")
            .asText());
  }

  @Test
  @Transactional
  void
      given_stepDataWithTextualContract_when_resolvingMappedContract_should_normalizeToContractObject() {
    // -- Arrange --
    String oldContractId = UUID.randomUUID().toString();
    String newContractId = UUID.randomUUID().toString();
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.put("step_data", "{\"inject_injector_contract\":\"%s\"}".formatted(oldContractId));
    Map<String, String> resolvedContracts = Map.of(oldContractId, newContractId);
    Exercise simulation = new Exercise();
    simulation.setId("sim-test");
    Workflow workflow = Workflow.builder().simulation(simulation).build();

    // -- Act --
    V1_DataImporter.StepDataResolution resolution =
        ReflectionTestUtils.invokeMethod(
            importer, "resolveStepData", stepNode, resolvedContracts, new HashMap<>(), workflow);
    String resolvedStepData = resolution.stepData();
    JsonNode resolvedJson = assertDoesNotThrow(() -> objectMapper.readTree(resolvedStepData));

    // -- Assert --
    assertTrue(resolvedJson.get("inject_injector_contract").isObject());
    assertEquals(
        newContractId,
        resolvedJson.get("inject_injector_contract").get("injector_contract_id").asText());
  }

  @Test
  @Transactional
  void given_stepDataWithRuntimeReferences_when_resolvingStepData_should_preserveRuntimeFields() {
    // -- Arrange --
    String contractId = UUID.randomUUID().toString();
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.put(
        "step_data",
        """
        {
          "inject_id": "old-inject-id",
          "inject_status": "old-status-id",
          "inject_depends_on": ["old-parent-id"],
          "inject_exercise": "old-exercise-id",
          "inject_scenario": "old-scenario-id",
          "inject_assets": ["old-asset-id"],
          "inject_asset_groups": ["old-asset-group-id"],
          "inject_teams": ["old-team-id"],
          "inject_injector_contract": {
            "injector_contract_id": "%s"
          }
        }
        """
            .formatted(contractId));
    Exercise simulation = new Exercise();
    simulation.setId("sim-test");
    Workflow workflow = Workflow.builder().simulation(simulation).build();
    // -- Act --
    V1_DataImporter.StepDataResolution resolution =
        ReflectionTestUtils.invokeMethod(
            importer,
            "resolveStepData",
            stepNode,
            new HashMap<String, String>(),
            new HashMap<>(),
            workflow);
    String resolvedStepData = resolution.stepData();
    JsonNode resolvedJson = assertDoesNotThrow(() -> objectMapper.readTree(resolvedStepData));

    // -- Assert --
    assertEquals("old-inject-id", resolvedJson.get("inject_id").asText());
    assertEquals("old-status-id", resolvedJson.get("inject_status").asText());
    assertEquals("old-parent-id", resolvedJson.get("inject_depends_on").get(0).asText());
    assertEquals("sim-test", resolvedJson.get("inject_exercise").asText());
    assertTrue(resolvedJson.get("inject_scenario").isNull());
    assertFalse(resolvedJson.has("inject_assets"));
    assertFalse(resolvedJson.has("inject_asset_groups"));
    assertFalse(resolvedJson.has("inject_teams"));
    assertEquals(
        contractId,
        resolvedJson.get("inject_injector_contract").get("injector_contract_id").asText());
  }

  @Test
  @Transactional
  @WithMockUser
  void given_stepDataWithSourceTeams_when_importing_should_stripInjectTeams() throws Exception {
    // -- Arrange --
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf teams stripped " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithStepTags(
            om, scenarioName, om.createArrayNode(), om.createArrayNode(), om.createArrayNode());
    ObjectNode stepData =
        (ObjectNode)
            importData.get("scenario_workflow").get("workflow_steps").get(0).get("step_data");
    stepData.set(
        "inject_teams", tagIdArray(om, UUID.randomUUID().toString(), UUID.randomUUID().toString()));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertFalse(
        storedData.has("inject_teams"),
        "runtime inject_teams must be stripped exactly like inject_assets/inject_asset_groups");
  }

  @Test
  @Transactional
  void given_workflowStandaloneMapperConditions_should_ignoreMapperStandaloneOnImport()
      throws Exception {
    // -- Arrange --
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode workflowImport =
        objectMapper.readTree(
            Files.readAllBytes(
                Paths.get("src/test/resources/importer-v1/import-scenario-with-workflow.json")));

    ArrayNode standaloneConditions = objectMapper.createArrayNode();
    ObjectNode mapperStandalone = objectMapper.createObjectNode();
    mapperStandalone.put("condition_id", "standalone-mapper-1");
    mapperStandalone.put("condition_type", "MAPPER");
    mapperStandalone.put("condition_key", "target_ip");
    mapperStandalone.put("condition_key_type", "IPv4");
    mapperStandalone.put("condition_value", "$.output.parsed.ip");
    mapperStandalone.put("condition_is_root", true);
    standaloneConditions.add(mapperStandalone);

    ObjectNode validStandalone = objectMapper.createObjectNode();
    validStandalone.put("condition_id", "standalone-event-1");
    validStandalone.put("condition_type", "EQ");
    validStandalone.put("condition_key", "status");
    validStandalone.put("condition_key_type", "text");
    validStandalone.put("condition_value", "SUCCESS");
    validStandalone.put("condition_is_root", true);
    standaloneConditions.add(validStandalone);

    ((ObjectNode) workflowImport.get("scenario_workflow"))
        .set("workflow_standalone_conditions", standaloneConditions);

    // -- Act --
    this.importer.importData(
        workflowImport, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    String expectedName = "test workflow import%s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    Scenario scenario =
        scenarioRepository.findAll().stream()
            .filter(s -> expectedName.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    Workflow workflow =
        workflowRepository
            .findByScenario_IdAndStatus(scenario.getId(), WorkflowStatus.TEMPLATE)
            .getFirst();

    List<Step> steps = stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());
    Set<String> linkedRootConditionIds =
        steps.stream()
            .flatMap(step -> conditionRepository.findAllLinkedToStepId(step.getId()).stream())
            .map(Condition::getId)
            .collect(java.util.stream.Collectors.toSet());

    List<Condition> standaloneRoots =
        conditionRepository.findAllByWorkflowIdAndConditionParentIsNull(workflow.getId()).stream()
            .filter(condition -> !linkedRootConditionIds.contains(condition.getId()))
            .toList();

    assertEquals(1, standaloneRoots.size());
    assertEquals(ConditionType.EQ, standaloneRoots.getFirst().getType());
  }

  @Test
  @Transactional
  void given_workflowStepConditionsOutOfOrder_when_importing_should_preserveParentChildLinks()
      throws Exception {
    // -- Arrange --
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode workflowImport =
        objectMapper.readTree(
            Files.readAllBytes(
                Paths.get("src/test/resources/importer-v1/import-scenario-with-workflow.json")));
    ObjectNode workflowNode = (ObjectNode) workflowImport.get("scenario_workflow");
    ArrayNode workflowSteps = (ArrayNode) workflowNode.get("workflow_steps");

    int expectedChildConditions = 0;
    for (JsonNode stepNode : workflowSteps) {
      JsonNode stepConditionsNode = stepNode.get("step_conditions");
      if (!(stepConditionsNode instanceof ArrayNode stepConditions) || stepConditions.isEmpty()) {
        continue;
      }

      ArrayNode reversed = objectMapper.createArrayNode();
      for (int i = stepConditions.size() - 1; i >= 0; i--) {
        JsonNode conditionNode = stepConditions.get(i);
        reversed.add(conditionNode);
        if (conditionNode.has("condition_parent_id")
            && !conditionNode.get("condition_parent_id").isNull()) {
          expectedChildConditions++;
        }
      }
      ((ObjectNode) stepNode).set("step_conditions", reversed);
    }

    assertTrue(
        expectedChildConditions > 0, "Test fixture must include at least one child condition");

    // -- Act --
    this.importer.importData(
        workflowImport, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    String expectedName = "test workflow import%s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    Scenario scenario =
        scenarioRepository.findAll().stream()
            .filter(s -> expectedName.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    Workflow workflow =
        workflowRepository
            .findByScenario_IdAndStatus(scenario.getId(), WorkflowStatus.TEMPLATE)
            .getFirst();

    long importedChildConditions =
        conditionRepository.findAll().stream()
            .filter(condition -> workflow.getId().equals(condition.getWorkflowId()))
            .filter(condition -> condition.getConditionParent() != null)
            .count();

    assertEquals(expectedChildConditions, importedChildConditions);
  }

  @Test
  @Transactional
  void
      given_existingContractWithoutOutputParsers_andStepDataWithOutputParsers_when_resolvingWorkflowStepData_should_forceContractResolution()
          throws Exception {
    // -- Arrange --
    String existingContractId = UUID.randomUUID().toString();
    InjectorContract existingContract = new InjectorContract();
    existingContract.setId(existingContractId);
    existingContract.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    existingContract.setContent("{}");
    existingContract.setLabels(Map.of("en", "existing"));
    existingContract.setCustom(false);
    existingContract.setManual(false);
    existingContract.setNeedsExecutor(false);
    existingContract.setPlatforms(new Endpoint.PLATFORM_TYPE[0]);
    injectorContractRepository.save(existingContract);

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode injectContractNode = objectMapper.createObjectNode();
    injectContractNode.put("injector_contract_id", existingContractId);
    ObjectNode payloadNode = objectMapper.createObjectNode();
    ArrayNode outputParsers = objectMapper.createArrayNode();
    outputParsers.add(objectMapper.createObjectNode());
    payloadNode.set("payload_output_parsers", outputParsers);
    injectContractNode.set("injector_contract_payload", payloadNode);

    // -- Act --
    Boolean shouldResolve =
        ReflectionTestUtils.invokeMethod(
            importer, "shouldResolveContractFromStepData", injectContractNode, existingContractId);

    // -- Assert --
    assertEquals(Boolean.TRUE, shouldResolve);
  }

  @Test
  @Transactional
  void given_scenarioWithLegacyPredefinedExpectations_should_migrateToAvailableExpectations()
      throws IOException {
    // Arrange
    ObjectMapper mapper = new ObjectMapper();
    String jsonContent =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/scenario_with_injects_from_injector.json")));
    JsonNode importNode = mapper.readTree(jsonContent);

    // Act
    this.importer.importData(
        importNode, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // Assert — the injector contract should have been created with migrated expectations
    InjectorContract importedContract =
        this.injectorContractRepository
            .findById("93d27459-68d0-43b1-ad65-eacc3cfa5cf7")
            .orElseThrow();

    JsonNode content = mapper.readTree(importedContract.getContent());
    assertNotNull(content.get("fields"), "Contract content should have fields");

    JsonNode expectationsField =
        java.util.stream.StreamSupport.stream(content.get("fields").spliterator(), false)
            .filter(f -> "expectations".equals(f.path("key").asText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing expectations field in contract"));

    // predefinedExpectations should no longer exist
    assertFalse(
        expectationsField.has("predefinedExpectations"),
        "predefinedExpectations should have been removed after import");

    // availableExpectations should be present
    assertTrue(
        expectationsField.has("availableExpectations"),
        "availableExpectations should be present after migration");
    JsonNode available = expectationsField.get("availableExpectations");
    assertTrue(available.isArray());
    assertFalse(available.isEmpty(), "availableExpectations should not be empty");

    // Every expectation must have the expectation_is_predefined flag
    for (JsonNode exp : available) {
      assertTrue(
          exp.has("expectation_is_predefined"),
          "Expectation "
              + exp.path("expectation_type").asText()
              + " should have expectation_is_predefined flag");
    }

    // DETECTION was in predefinedExpectations → should be marked as predefined
    long predefinedCount =
        java.util.stream.StreamSupport.stream(available.spliterator(), false)
            .filter(e -> e.path("expectation_is_predefined").asBoolean())
            .count();
    assertTrue(predefinedCount > 0, "At least one expectation should be marked as predefined");

    // Verify the inject was also created
    List<Inject> injects = injectRepository.findAll();
    assertFalse(injects.isEmpty(), "Injects should have been created from the scenario import");
  }

  @Test
  @Transactional
  @WithMockUser
  void given_workflowStepDataWithSourceInjectUser_when_importing_should_rewriteToCurrentUser()
      throws Exception {
    // -- Arrange --
    // The workflow step's step_data references a source-instance user UUID that does not exist in
    // the target database. Importing a simulation on another instance must never resolve/recreate
    // that user: inject_user must be rewritten to the user executing the import.
    // The step references the OpenAEV implant injector and a contract id: both must be resolvable
    // on
    // the target, otherwise importWorkflowSteps now skips the step (no step_data persisted).
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    persistResolvableStepContract("dddddddd-0001-0001-0001-000000000098");
    String sourceUserId = "ffffffff-ffff-ffff-ffff-ffffffffffff";
    assertTrue(
        userRepository.findById(sourceUserId).isEmpty(),
        "Source inject_user UUID must not exist in the test database");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode workflowImport =
        mapper.readTree(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/import-scenario-with-workflow-inject-user.json")));

    // -- Act --
    this.importer.importData(
        workflowImport, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    String expectedName =
        "test workflow import inject user%s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    Scenario scenario =
        scenarioRepository.findAll().stream()
            .filter(s -> expectedName.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    Workflow workflow =
        workflowRepository
            .findByScenario_IdAndStatus(scenario.getId(), WorkflowStatus.TEMPLATE)
            .getFirst();
    List<Step> steps = stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());
    assertEquals(1, steps.size());

    Step step = steps.getFirst();
    JsonNode storedData = mapper.readTree(step.getData());
    String currentUserId = testUserHolder.get().getId();

    assertEquals(
        currentUserId,
        storedData.get("inject_user").asText(),
        "inject_user must be rewritten to the user executing the import");
    assertNotEquals(
        sourceUserId,
        storedData.get("inject_user").asText(),
        "The source-instance inject_user UUID must not survive the import");
  }

  // ---------------------------------------------------------------------------
  // step_data nested tag id rewriting (injector_contract_tags / inject_tags)
  //
  // At run time InjectExecutionStep.getInjectFromDataStep() deserializes step_data into an Inject
  // via MonoIdDeserializerHelper (em.getReference() -> Hibernate proxy). A SOURCE-instance tag id
  // absent from the TARGET instance triggers EntityNotFoundException as soon as the proxy is
  // initialized (hashCode() on the Set<Tag>) -> JsonMappingException -> fatal ChainingException.
  // The importer must rewrite these nested tag ids to the resolved target tag ids (or drop them).
  // ---------------------------------------------------------------------------

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataTagMatchingExistingTargetTagByName_when_importing_should_rewriteToExistingTagWithoutDuplicate()
          throws Exception {
    // -- Arrange --
    // A tag already exists on the TARGET instance; the export references the same tag by a
    // DIFFERENT
    // (source) id but the SAME name at root level (scenario_tags). importTags() must resolve the
    // source id to the existing target tag, and the nested injector_contract_tags / inject_tags in
    // step_data must be rewritten to that existing tag id (no duplicate tag created).
    String tagName = "v1-import-shared-tag-" + UUID.randomUUID();
    Tag existingTag = new Tag();
    existingTag.setName(tagName);
    existingTag.setColor("#112233");
    existingTag = tagRepository.save(existingTag);
    String targetTagId = existingTag.getId();
    String sourceTagId = UUID.randomUUID().toString();
    assertNotEquals(targetTagId, sourceTagId);

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf tag existing " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithStepTags(
            om,
            scenarioName,
            tagObjects(om, sourceTagId, tagName, "#112233"),
            tagIdArray(om, sourceTagId),
            tagIdArray(om, sourceTagId));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    // No duplicate tag created: the source id was mapped to the existing tag found by name.
    assertEquals(1, tagRepository.findByNameIgnoreCase(tagName).size());

    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        List.of(targetTagId),
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_tags")),
        "injector_contract_tags must be rewritten to the existing target tag id");
    assertEquals(
        List.of(targetTagId),
        tagIdList(storedData.get("inject_tags")),
        "inject_tags must be rewritten to the existing target tag id");

    // Run-time deserialization (as InjectExecutionStep does) must not throw.
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void given_stepDataTagUnknownByNameAndId_when_importing_should_createNewTagAndRewriteStepData()
      throws Exception {
    // -- Arrange --
    // The export references a tag by a source id AND a name that do not exist on the target. It is
    // present at root (scenario_tags), so importTags() creates a brand new target tag; the nested
    // step_data tag ids must be rewritten to that newly created tag id.
    String tagName = "v1-import-brandnew-tag-" + UUID.randomUUID();
    assertTrue(tagRepository.findByNameIgnoreCase(tagName).isEmpty());
    String sourceTagId = UUID.randomUUID().toString();

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf tag new " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithStepTags(
            om,
            scenarioName,
            tagObjects(om, sourceTagId, tagName, "#445566"),
            tagIdArray(om, sourceTagId),
            om.createArrayNode());

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    List<Tag> created = tagRepository.findByNameIgnoreCase(tagName);
    assertEquals(1, created.size(), "A new target tag must have been created by importTags()");
    String newTagId = created.getFirst().getId();
    assertNotEquals(sourceTagId, newTagId, "The new tag must not reuse the source id");

    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        List.of(newTagId),
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_tags")),
        "injector_contract_tags must be rewritten to the newly created tag id");
    assertFalse(
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_tags"))
            .contains(sourceTagId),
        "The source tag id must not survive the import");

    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void given_stepDataTagUnresolvable_when_importing_should_dropTagAndNotCrashAtRun()
      throws Exception {
    // -- Arrange --
    // Reproduces the original bug: injector_contract_tags references a source tag id that is NOT
    // present anywhere in the export (no root tag object with that id or matching name), so
    // importTags() never resolves it. The importer must DROP it from step_data so the run does not
    // crash.
    String unknownTagId = UUID.randomUUID().toString();
    assertTrue(tagRepository.findById(unknownTagId).isEmpty());

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf tag unresolved " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithStepTags(
            om,
            scenarioName,
            om.createArrayNode(), // no root tags => unknownTagId cannot be resolved
            tagIdArray(om, unknownTagId),
            om.createArrayNode());

    // Sanity check: WITHOUT the fix, leaving the unresolved id in step_data crashes the run-time
    // deserialization exactly as reported (EntityNotFoundException wrapped by Jackson).
    ObjectNode rawUnsanitized = om.createObjectNode();
    rawUnsanitized.put("inject_title", "raw");
    ObjectNode rawContract = om.createObjectNode();
    rawContract.put("injector_contract_id", UUID.randomUUID().toString());
    rawContract.set("injector_contract_tags", tagIdArray(om, unknownTagId));
    rawUnsanitized.set("inject_injector_contract", rawContract);
    Exception bug =
        assertThrows(Exception.class, () -> deserializeStepDataAsRun(rawUnsanitized.toString()));
    assertTrue(
        hasCause(bug, EntityNotFoundException.class),
        "Unresolved tag id must trigger EntityNotFoundException at run-time deserialization");

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertTrue(
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_tags"))
            .isEmpty(),
        "Unresolved injector_contract_tags id must be dropped from step_data");

    // WITH the fix, the persisted step_data deserializes without crashing at run time.
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  // ---------------------------------------------------------------------------
  // step_data nested domain id rewriting (injector_contract_domains)
  //
  // InjectorContract.domains is a Set<Domain> deserialized via MonoIdDeserializerHelper
  // (em.getReference() -> Hibernate proxy, no existence check, no @NotFound(IGNORE)). A source
  // domain id absent from the target instance triggers EntityNotFoundException as soon as the proxy
  // is initialized (hashCode() on the Set) -> JsonMappingException -> fatal ChainingException, even
  // though that InjectorContract is discarded and re-read from the DB right after. The importer
  // must
  // resolve/rewrite these nested domain ids (reusing importDomains) or drop them.
  // ---------------------------------------------------------------------------

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractDomainMatchingExistingTargetDomainByName_when_importing_should_rewriteToExistingDomainWithoutDuplicate()
          throws Exception {
    // -- Arrange --
    // A domain already exists on the TARGET instance; the export references the same domain by a
    // DIFFERENT (source) id but the SAME name (object-shaped injector_contract_domains, as produced
    // by real exports). importDomains() must resolve it to the existing target domain by name and
    // the nested step_data must be rewritten to that existing id (no duplicate domain created).
    String domainName = "v1-import-domain-existing-" + UUID.randomUUID();
    Domain existing =
        domainService.upsert(domainName, "#389CFF", new Tenant(TenantContext.getCurrentTenant()));
    String targetDomainId = existing.getId();
    String sourceDomainId = UUID.randomUUID().toString();
    assertNotEquals(targetDomainId, sourceDomainId);
    assertTrue(domainRepository.findById(sourceDomainId).isEmpty());

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf domain existing name " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithContractDomains(
            om, scenarioName, domainObjectArray(om, sourceDomainId, domainName, "#389CFF"));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    assertEquals(
        1,
        domainRepository.findByNameIn(List.of(domainName)).size(),
        "No duplicate domain must be created: the source id maps to the existing domain by name");
    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        List.of(targetDomainId),
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_domains")),
        "injector_contract_domains must be rewritten to the existing target domain id");
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractDomainWithSameIdOnTarget_when_importing_should_resolveByIdWithoutCreation()
          throws Exception {
    // -- Arrange --
    // The referenced domain id exists TEL QUEL on the target: importDomains() must resolve it via
    // the tenant-scoped id lookup directly, without going through creation. Bare-id form (the
    // format that crashes at run time) is used here.
    String domainName = "v1-import-domain-sameid-" + UUID.randomUUID();
    Domain existing =
        domainService.upsert(domainName, "#66CCFF", new Tenant(TenantContext.getCurrentTenant()));
    String existingId = existing.getId();
    long domainCountBefore = domainRepository.count();

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf domain same id " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithContractDomains(om, scenarioName, tagIdArray(om, existingId));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    assertEquals(
        domainCountBefore,
        domainRepository.count(),
        "No new domain must be created: the id is resolved directly by the tenant-scoped lookup");
    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        List.of(existingId),
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_domains")),
        "injector_contract_domains must keep the id that already exists on the target");
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractDomainBareSourceIdWithRichInjectFormat_when_importing_should_resolveViaInjectContractDomainsByName()
          throws Exception {
    // -- Arrange --
    // injector_contract_domains (contract level) is serialized by MultiIdSetSerializer as a BARE
    // source id only, unknown on this target instance (Domains get a fresh per-tenant UUID). The
    // inject-level RICH field inject_contract_domains carries the SAME source id WITH domain_name,
    // so resolveInjectContractDomainsFromInjectFormat can pre-resolve it by name (upsert) into
    // baseIds — letting importDomains rewrite the bare id to the target id instead of dropping it.
    String domainName = "v1-import-domain-richinject-" + UUID.randomUUID();
    String sourceDomainId = UUID.randomUUID().toString();
    assertTrue(domainRepository.findById(sourceDomainId).isEmpty());
    assertTrue(domainRepository.findByNameIn(List.of(domainName)).isEmpty());

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf domain rich inject " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithContractDomains(om, scenarioName, tagIdArray(om, sourceDomainId));
    // Inject-level rich format sharing the SAME source domain id as injector_contract_domains.
    ObjectNode stepData =
        (ObjectNode)
            importData.get("scenario_workflow").get("workflow_steps").get(0).get("step_data");
    stepData.set(
        "inject_contract_domains", domainObjectArray(om, sourceDomainId, domainName, "#AABBCC"));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    List<Domain> created = domainRepository.findByNameIn(List.of(domainName));
    assertEquals(
        1,
        created.size(),
        "the domain must be resolved/created by name from the rich inject_contract_domains format");
    String newDomainId = created.getFirst().getId();
    assertNotEquals(
        sourceDomainId, newDomainId, "a fresh per-tenant id is assigned, not the source id");

    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        List.of(newDomainId),
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_domains")),
        "the bare injector_contract_domains source id must be rewritten to the resolved target id, not dropped");
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractDomainUnknownByIdAndName_when_importing_should_createNewDomainAndRewrite()
          throws Exception {
    // -- Arrange --
    // The export references a domain by a source id AND a name that do not exist on the target
    // (object-shaped). importDomains()/upsert() must create a brand new target domain and the
    // nested step_data must be rewritten to that new id.
    String domainName = "v1-import-domain-new-" + UUID.randomUUID();
    assertTrue(domainRepository.findByNameIn(List.of(domainName)).isEmpty());
    String sourceDomainId = UUID.randomUUID().toString();

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf domain new " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithContractDomains(
            om, scenarioName, domainObjectArray(om, sourceDomainId, domainName, "#123456"));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    List<Domain> created = domainRepository.findByNameIn(List.of(domainName));
    assertEquals(1, created.size(), "A new target domain must have been created by upsert()");
    String newDomainId = created.getFirst().getId();
    assertNotEquals(sourceDomainId, newDomainId, "The new domain must not reuse the source id");

    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        List.of(newDomainId),
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_domains")),
        "injector_contract_domains must be rewritten to the newly created domain id");
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void given_stepDataContractDomainUnresolvable_when_importing_should_dropDomainAndNotCrashAtRun()
      throws Exception {
    // -- Arrange --
    // Reproduces the original bug: injector_contract_domains references a bare source domain id
    // that
    // is NOT present on the target (no matching id, no name to create from). importDomains() cannot
    // resolve it, so the importer must DROP it so the run does not crash.
    String unknownDomainId = UUID.randomUUID().toString();
    assertTrue(domainRepository.findById(unknownDomainId).isEmpty());

    ObjectMapper om = new ObjectMapper();

    // Sanity check: WITHOUT the fix, leaving the unresolved id in step_data crashes the run-time
    // deserialization exactly as reported (EntityNotFoundException wrapped by Jackson).
    ObjectNode rawUnsanitized = om.createObjectNode();
    rawUnsanitized.put("inject_title", "raw");
    ObjectNode rawContract = om.createObjectNode();
    rawContract.put("injector_contract_id", UUID.randomUUID().toString());
    rawContract.set("injector_contract_domains", tagIdArray(om, unknownDomainId));
    rawUnsanitized.set("inject_injector_contract", rawContract);
    Exception bug =
        assertThrows(Exception.class, () -> deserializeStepDataAsRun(rawUnsanitized.toString()));
    assertTrue(
        hasCause(bug, EntityNotFoundException.class),
        "Unresolved domain id must trigger EntityNotFoundException at run-time deserialization");

    String scenarioName = "wf domain unresolved " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithContractDomains(om, scenarioName, tagIdArray(om, unknownDomainId));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertTrue(
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_domains"))
            .isEmpty(),
        "Unresolved injector_contract_domains id must be dropped from step_data");
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  // ---------------------------------------------------------------------------
  // Workflow configuration defaults (importWorkflow): absent fields must fall back to the business
  // default, not silently become false. Explicit values must always be honoured.
  // ---------------------------------------------------------------------------

  @Test
  @Transactional
  @WithMockUser
  void given_workflowConfigFieldsAbsent_when_importing_should_applyBusinessDefaults() {
    // -- Arrange --
    // Older/partial export: the workflow node carries no config flags at all.
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf config absent " + UUID.randomUUID();
    ObjectNode workflowNode = om.createObjectNode();
    workflowNode.set("workflow_steps", om.createArrayNode());
    ObjectNode importData = buildScenarioImportWithWorkflow(om, scenarioName, workflowNode);

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Workflow workflow = findImportedWorkflow(scenarioName);
    assertTrue(workflow.isTimeoutEnabled(), "timeout_enabled absent -> default true");
    assertEquals(
        DEFAULT_TIMEOUT_SECONDS,
        workflow.getTimeoutSeconds(),
        "timeout_seconds absent -> DEFAULT_TIMEOUT_SECONDS");
    assertTrue(workflow.isSafeModeEnabled(), "safe_mode_enabled absent -> default true");
    assertFalse(
        workflow.isRateLimitEnabled(),
        "rate_limit_enabled absent -> false (intentionally no business default true)");
  }

  @Test
  @Transactional
  @WithMockUser
  void given_workflowConfigFieldsExplicit_when_importing_should_honourExplicitValues() {
    // -- Arrange --
    // All four fields present with values that differ from the defaults. Critical non-regression:
    // "present with false" must NOT be overwritten by the absence fallback.
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf config explicit " + UUID.randomUUID();
    ObjectNode workflowNode = om.createObjectNode();
    workflowNode.put("workflow_timeout_enabled", false);
    workflowNode.put("workflow_timeout_seconds", 25800);
    workflowNode.put("workflow_safe_mode_enabled", false);
    workflowNode.put("workflow_rate_limit_enabled", true);
    workflowNode.set("workflow_steps", om.createArrayNode());
    ObjectNode importData = buildScenarioImportWithWorkflow(om, scenarioName, workflowNode);

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Workflow workflow = findImportedWorkflow(scenarioName);
    assertFalse(
        workflow.isTimeoutEnabled(),
        "explicit timeout_enabled=false must be honoured, not defaulted to true");
    assertEquals(25800L, workflow.getTimeoutSeconds(), "explicit timeout_seconds must be honoured");
    assertFalse(
        workflow.isSafeModeEnabled(),
        "explicit safe_mode_enabled=false must be honoured, not defaulted to true");
    assertTrue(workflow.isRateLimitEnabled(), "explicit rate_limit_enabled=true must be honoured");
  }

  @Test
  @Transactional
  @WithMockUser
  void given_workflowFullConfigFixture_when_importing_should_preserveExplicitConfig()
      throws Exception {
    // -- Arrange --
    // Non-regression: reuse the full-config fixture (rate limit on, timeout on = 300s, safe mode
    // off) to confirm the explicit values are unchanged by the absence-fallback fix.
    ObjectMapper om = new ObjectMapper();
    JsonNode importData =
        om.readTree(
            Files.readAllBytes(
                Paths.get("src/test/resources/importer-v1/import-scenario-with-workflow.json")));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    String expectedName = "test workflow import%s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    Workflow workflow = findImportedWorkflow(expectedName, false);
    assertTrue(workflow.isRateLimitEnabled());
    assertTrue(workflow.isTimeoutEnabled());
    assertEquals(300L, workflow.getTimeoutSeconds());
    assertFalse(workflow.isSafeModeEnabled());
  }

  // ---------------------------------------------------------------------------
  // step_data nested attack pattern rewriting (injector_contract_attack_patterns and the nested
  // injector_contract_payload.payload_attack_patterns).
  //
  // This is NOT a missing-FK problem (unlike tags/domains) and would crash even re-importing on the
  // SAME instance: InjectorContract.attackPatterns / Payload.attackPatterns are deserialized with
  // MonoIdDeserializerHelper (expects scalar UUIDs), but Mixins disable the id serializer on
  // getAttackPatterns() so the export writes FULL OBJECTS. At run time MonoIdDeserializerHelper
  // returns null for each object element, then InjectorContract.setAttackPatterns() ->
  // Base.collectIds() NPEs on element.getId(). The importer must normalize these arrays to resolved
  // scalar ids.
  // ---------------------------------------------------------------------------

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractAttackPatternMatchingExistingExternalId_when_importing_should_rewriteToExistingScalarIdWithoutDuplicate()
          throws Exception {
    // -- Arrange --
    // An attack pattern already exists on the TARGET (same MITRE external id); the export carries a
    // FULL OBJECT with a different source id. importAttackPattern() must resolve it by external id
    // and the array must be rewritten to that existing scalar id (no duplicate created).
    String externalId = "T1021.002";
    AttackPattern existing =
        attackPatternRepository.save(
            AttackPatternFixture.createAttackPatternsWithExternalId(externalId));
    String targetId = existing.getId();
    String sourceId = UUID.randomUUID().toString();
    assertNotEquals(targetId, sourceId);

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf ap existing " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithContractAttackPatterns(
            om,
            scenarioName,
            attackPatternObjectArray(om, sourceId, externalId, "SMB/Windows Admin Shares"));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    assertEquals(
        1,
        attackPatternRepository
            .findAllByExternalIdInIgnoreCaseAndTenantId(
                List.of(externalId), TenantContext.getCurrentTenant())
            .size(),
        "No duplicate attack pattern must be created for an existing external id");
    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        List.of(targetId),
        tagIdList(
            storedData.get("inject_injector_contract").get("injector_contract_attack_patterns")),
        "injector_contract_attack_patterns must be rewritten to the existing target scalar id");
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractAttackPatternUnknownExternalId_when_importing_should_createAndRewriteToScalarId()
          throws Exception {
    // -- Arrange --
    // The export references an attack pattern by an external id absent from the target: a new one
    // must be created and the array rewritten to its scalar id.
    String externalId = "T9999.001-" + UUID.randomUUID();
    assertTrue(
        attackPatternRepository
            .findAllByExternalIdInIgnoreCaseAndTenantId(
                List.of(externalId), TenantContext.getCurrentTenant())
            .isEmpty());
    String sourceId = UUID.randomUUID().toString();

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf ap new " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithContractAttackPatterns(
            om, scenarioName, attackPatternObjectArray(om, sourceId, externalId, "Brand New AP"));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    List<AttackPattern> created =
        attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
            List.of(externalId), TenantContext.getCurrentTenant());
    assertEquals(1, created.size(), "A new attack pattern must have been created");
    String newId = created.getFirst().getId();
    assertNotEquals(sourceId, newId, "The new attack pattern must not reuse the source id");

    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        List.of(newId),
        tagIdList(
            storedData.get("inject_injector_contract").get("injector_contract_attack_patterns")),
        "injector_contract_attack_patterns must be rewritten to the newly created scalar id");
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractAttackPatternAsFullObject_when_importing_should_normalizeAndNotCrashAtRun()
          throws Exception {
    // -- Arrange --
    // Reproduces the original bug: injector_contract_attack_patterns carries FULL OBJECTS (as the
    // export writes them). Left as-is they crash the run-time deserialization with a NPE in
    // Base.collectIds via InjectorContract.setAttackPatterns.
    String externalId = "T1055-" + UUID.randomUUID();
    String sourceId = UUID.randomUUID().toString();
    ObjectMapper om = new ObjectMapper();

    // Sanity check: WITHOUT the fix, a full-object attack pattern crashes the run-time
    // deserialization (MonoIdDeserializerHelper -> null element -> NPE on getId()).
    ObjectNode rawUnsanitized = om.createObjectNode();
    rawUnsanitized.put("inject_title", "raw");
    ObjectNode rawContract = om.createObjectNode();
    rawContract.put("injector_contract_id", UUID.randomUUID().toString());
    rawContract.set(
        "injector_contract_attack_patterns",
        attackPatternObjectArray(om, sourceId, externalId, "Process Injection"));
    rawUnsanitized.set("inject_injector_contract", rawContract);
    Exception bug =
        assertThrows(Exception.class, () -> deserializeStepDataAsRun(rawUnsanitized.toString()));
    assertTrue(
        hasCause(bug, NullPointerException.class),
        "A full-object attack pattern must trigger a NullPointerException at run-time deserialization");

    String scenarioName = "wf ap object " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithContractAttackPatterns(
            om,
            scenarioName,
            attackPatternObjectArray(om, sourceId, externalId, "Process Injection"));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    JsonNode storedData = readStoredStepData(scenarioName, om);
    JsonNode rewrittenAttackPatterns =
        storedData.get("inject_injector_contract").get("injector_contract_attack_patterns");
    assertEquals(
        1, rewrittenAttackPatterns.size(), "The attack pattern must be resolved to a scalar id");
    assertTrue(
        rewrittenAttackPatterns.get(0).isTextual(),
        "The stored attack pattern entry must be a scalar id, not a full object");
    // WITH the fix, the persisted step_data deserializes without crashing at run time.
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_contractAndNestedPayloadAttackPatternsAsObjects_when_rewriting_should_normalizeBothArraysToScalarIds() {
    // -- Arrange --
    // Both injector_contract_attack_patterns and the nested
    // injector_contract_payload.payload_attack_patterns carry full objects (Mixins disable the id
    // serializer on Payload.getAttackPatterns() too). Exercised directly on the private helper to
    // isolate the nested-payload path without triggering full payload/contract resolution.
    String contractExternalId = "T1021.002-" + UUID.randomUUID();
    String payloadExternalId = "T1059.001-" + UUID.randomUUID();
    ObjectMapper om = new ObjectMapper();

    ObjectNode contractNode = om.createObjectNode();
    contractNode.set(
        "injector_contract_attack_patterns",
        attackPatternObjectArray(
            om, UUID.randomUUID().toString(), contractExternalId, "Contract AP"));
    ObjectNode payloadNode = om.createObjectNode();
    payloadNode.set(
        "payload_attack_patterns",
        attackPatternObjectArray(
            om, UUID.randomUUID().toString(), payloadExternalId, "Payload AP"));
    contractNode.set("injector_contract_payload", payloadNode);

    // -- Act --
    ReflectionTestUtils.invokeMethod(
        importer,
        "rewriteInjectorContractAttackPatterns",
        contractNode,
        new HashMap<String, Base>());

    // -- Assert --
    List<String> contractIds = tagIdList(contractNode.get("injector_contract_attack_patterns"));
    List<String> payloadIds =
        tagIdList(contractNode.get("injector_contract_payload").get("payload_attack_patterns"));
    assertEquals(1, contractIds.size(), "Contract attack pattern must be normalized to one id");
    assertEquals(1, payloadIds.size(), "Payload attack pattern must be normalized to one id");
    assertEquals(
        contractIds.getFirst(),
        attackPatternRepository
            .findAllByExternalIdInIgnoreCaseAndTenantId(
                List.of(contractExternalId), TenantContext.getCurrentTenant())
            .getFirst()
            .getId(),
        "Contract array must reference the resolved target attack pattern id");
    assertEquals(
        payloadIds.getFirst(),
        attackPatternRepository
            .findAllByExternalIdInIgnoreCaseAndTenantId(
                List.of(payloadExternalId), TenantContext.getCurrentTenant())
            .getFirst()
            .getId(),
        "Nested payload array must reference the resolved target attack pattern id");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractAttackPatternScalarIdExistingOnTarget_when_rewriting_should_preserveScalarId() {
    // -- Arrange --
    // A re-import on the SAME instance can carry an already-normalized scalar attack pattern id
    // (bare UUID) that importAttackPattern ignores (it only reads object entries). Such an id must
    // be preserved when it still exists on the target, not silently wiped to an empty array.
    AttackPattern existing =
        attackPatternRepository.save(
            AttackPatternFixture.createAttackPatternsWithExternalId("T1105-" + UUID.randomUUID()));
    ObjectMapper om = new ObjectMapper();
    ObjectNode contractNode = om.createObjectNode();
    contractNode.set("injector_contract_attack_patterns", tagIdArray(om, existing.getId()));

    // -- Act --
    ReflectionTestUtils.invokeMethod(
        importer,
        "rewriteInjectorContractAttackPatterns",
        contractNode,
        new HashMap<String, Base>());

    // -- Assert --
    assertEquals(
        List.of(existing.getId()),
        tagIdList(contractNode.get("injector_contract_attack_patterns")),
        "An existing scalar attack pattern id must be preserved, not dropped");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractAttackPatternScalarIdMissingFromTarget_when_rewriting_should_dropScalarId() {
    // -- Arrange --
    // A scalar id absent from the target cannot be preserved (it would crash the run-time
    // deserialization), so it must be dropped.
    String unknownId = UUID.randomUUID().toString();
    assertTrue(attackPatternRepository.findById(unknownId).isEmpty());
    ObjectMapper om = new ObjectMapper();
    ObjectNode contractNode = om.createObjectNode();
    contractNode.set("injector_contract_attack_patterns", tagIdArray(om, unknownId));

    // -- Act --
    ReflectionTestUtils.invokeMethod(
        importer,
        "rewriteInjectorContractAttackPatterns",
        contractNode,
        new HashMap<String, Base>());

    // -- Assert --
    assertTrue(
        tagIdList(contractNode.get("injector_contract_attack_patterns")).isEmpty(),
        "An unresolvable scalar attack pattern id must be dropped");
  }

  @Test
  @Transactional
  @WithMockUser
  void given_stepDataTagIdExistingOnTargetButNotInExport_when_importing_should_keepExistingTag()
      throws Exception {
    // -- Arrange --
    // Re-import on the same instance: injector_contract_tags references a tag that exists on the
    // target but is NOT carried as a root tag object (so importTags never seeds baseIds with it).
    // The id must be kept as a safe fallback (it exists), not dropped as if unresolvable.
    Tag existing = new Tag();
    existing.setName("v1-import-existing-not-in-export-" + UUID.randomUUID());
    existing.setColor("#778899");
    existing = tagRepository.save(existing);
    String existingTagId = existing.getId();

    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf tag existing not in export " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithStepTags(
            om,
            scenarioName,
            om.createArrayNode(), // no root tag object seeding baseIds
            tagIdArray(om, existingTagId),
            om.createArrayNode());

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        List.of(existingTagId),
        tagIdList(storedData.get("inject_injector_contract").get("injector_contract_tags")),
        "A tag id that already exists on the target must be kept even when absent from the export");
    assertDoesNotThrow(() -> deserializeStepDataAsRun(storedData.toString()));
  }

  @Test
  @Transactional
  @WithMockUser
  void given_workflowBooleanConfigFieldsExplicitNull_when_importing_should_applyDefaults() {
    // -- Arrange --
    // Partial export: the boolean config flags are present but explicitly JSON null. asBoolean() on
    // a NullNode returns false, so a naive read would silently disable timeout/safe-mode. They must
    // be treated like "absent" and fall back to the business default (enabled).
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf config null booleans " + UUID.randomUUID();
    ObjectNode workflowNode = om.createObjectNode();
    workflowNode.putNull("workflow_timeout_enabled");
    workflowNode.putNull("workflow_safe_mode_enabled");
    workflowNode.putNull("workflow_rate_limit_enabled");
    workflowNode.set("workflow_steps", om.createArrayNode());
    ObjectNode importData = buildScenarioImportWithWorkflow(om, scenarioName, workflowNode);

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Workflow workflow = findImportedWorkflow(scenarioName);
    assertTrue(workflow.isTimeoutEnabled(), "explicit null timeout_enabled -> default true");
    assertTrue(workflow.isSafeModeEnabled(), "explicit null safe_mode_enabled -> default true");
    assertFalse(
        workflow.isRateLimitEnabled(),
        "explicit null rate_limit_enabled -> false (no business default true)");
  }

  // ---------------------------------------------------------------------------
  // resolveStepData / sanitizateStepData branch coverage (workflow step_data)
  //
  // resolveStepData is the single entry point that decides whether a workflow step's inline
  // injector contract must be resolved/recreated before the step_data is sanitized and persisted.
  // Each early-return guard corresponds to a distinct real export shape and must funnel through
  // sanitizateStepData so runtime-only fields (assets, exercise/scenario, user) are always cleaned.
  // ---------------------------------------------------------------------------

  @Test
  @Transactional
  @WithMockUser
  void given_stepDataWithoutInjectorContract_when_resolving_should_sanitizeAndBindToScenario() {
    // -- Arrange --
    // A workflow step with no inject_injector_contract at all (e.g. a non-inject action step):
    // there is nothing to resolve, but sanitization must still bind it to the target scenario and
    // strip source-instance runtime references.
    ObjectMapper om = new ObjectMapper();
    ObjectNode stepData = om.createObjectNode();
    stepData.put("inject_id", "src-inject");
    stepData.put("inject_title", "no contract step");
    stepData.set("inject_assets", tagIdArray(om, UUID.randomUUID().toString()));
    stepData.put("inject_exercise", "src-exercise");
    ObjectNode stepNode = om.createObjectNode();
    stepNode.set("step_data", stepData);

    Scenario scenario = new Scenario();
    scenario.setId(UUID.randomUUID().toString());
    Workflow workflow = Workflow.builder().scenario(scenario).build();

    // -- Act --
    V1_DataImporter.StepDataResolution resolution =
        ReflectionTestUtils.invokeMethod(
            importer,
            "resolveStepData",
            stepNode,
            new HashMap<String, String>(),
            new HashMap<String, Base>(),
            workflow);
    String resolved = resolution.stepData();
    JsonNode json = assertDoesNotThrow(() -> new ObjectMapper().readTree(resolved));

    // -- Assert --
    assertFalse(json.has("inject_assets"), "runtime inject_assets must be stripped");
    assertEquals(
        scenario.getId(),
        json.get("inject_scenario").asText(),
        "step_data must be rebound to the target scenario");
    assertTrue(
        json.get("inject_exercise").isNull(), "inject_exercise must be nulled for a scenario");
  }

  @Test
  @Transactional
  @WithMockUser
  void given_stepDataInjectorContractWithoutId_when_resolving_should_sanitizeWithoutResolution() {
    // -- Arrange --
    // The contract object carries no injector_contract_id (extractInjectorContractId -> null):
    // nothing can be resolved, the step_data is only sanitized.
    ObjectMapper om = new ObjectMapper();
    ObjectNode contract = om.createObjectNode();
    contract.put("injector_contract_manual", false);
    ObjectNode stepData = om.createObjectNode();
    stepData.put("inject_title", "contract without id");
    stepData.set("inject_injector_contract", contract);
    ObjectNode stepNode = om.createObjectNode();
    stepNode.set("step_data", stepData);

    Scenario scenario = new Scenario();
    scenario.setId(UUID.randomUUID().toString());
    Workflow workflow = Workflow.builder().scenario(scenario).build();

    // -- Act --
    V1_DataImporter.StepDataResolution resolution =
        ReflectionTestUtils.invokeMethod(
            importer,
            "resolveStepData",
            stepNode,
            new HashMap<String, String>(),
            new HashMap<String, Base>(),
            workflow);
    String resolved = resolution.stepData();
    JsonNode json = assertDoesNotThrow(() -> new ObjectMapper().readTree(resolved));

    // -- Assert --
    // The contract object is preserved (no id to resolve) and the step is bound to the scenario.
    assertTrue(json.get("inject_injector_contract").isObject());
    assertEquals(scenario.getId(), json.get("inject_scenario").asText());
  }

  @Test
  @Transactional
  @WithMockUser
  void given_stepDataTextualContractMissingFromDb_when_resolving_should_logAndSanitize() {
    // -- Arrange --
    // A textual contract id that does not exist on the target and cannot be recreated (textual form
    // carries no payload). resolveStepData must not crash: it logs and falls back to sanitization.
    String missingContractId = UUID.randomUUID().toString();
    assertTrue(injectorContractRepository.findById(missingContractId).isEmpty());
    ObjectMapper om = new ObjectMapper();
    ObjectNode stepData = om.createObjectNode();
    stepData.put("inject_title", "textual missing contract");
    stepData.put("inject_injector_contract", missingContractId);
    ObjectNode stepNode = om.createObjectNode();
    stepNode.set("step_data", stepData);

    Scenario scenario = new Scenario();
    scenario.setId(UUID.randomUUID().toString());
    Workflow workflow = Workflow.builder().scenario(scenario).build();

    // -- Act --
    V1_DataImporter.StepDataResolution resolution =
        ReflectionTestUtils.invokeMethod(
            importer,
            "resolveStepData",
            stepNode,
            new HashMap<String, String>(),
            new HashMap<String, Base>(),
            workflow);
    String resolved = resolution.stepData();
    JsonNode json = assertDoesNotThrow(() -> new ObjectMapper().readTree(resolved));

    // -- Assert --
    // The unresolved textual id survives untouched (nothing to remap), the step is still sanitized.
    assertEquals(missingContractId, json.get("inject_injector_contract").asText());
    assertEquals(scenario.getId(), json.get("inject_scenario").asText());
  }

  @Test
  @Transactional
  @WithMockUser
  void given_stepDataContractExistingOnTarget_when_resolving_should_sanitizeWithoutResolution() {
    // -- Arrange --
    // The referenced contract already exists on the target and has no output parsers forcing a
    // re-resolution: resolveStepData short-circuits to sanitization (no duplicate contract).
    String existingContractId = UUID.randomUUID().toString();
    InjectorContract existingContract = new InjectorContract();
    existingContract.setId(existingContractId);
    existingContract.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    existingContract.setContent("{}");
    existingContract.setLabels(Map.of("en", "existing step contract"));
    existingContract.setCustom(false);
    existingContract.setManual(false);
    existingContract.setNeedsExecutor(false);
    existingContract.setPlatforms(new Endpoint.PLATFORM_TYPE[0]);
    injectorContractRepository.save(existingContract);
    long contractCountBefore = injectorContractRepository.count();

    ObjectMapper om = new ObjectMapper();
    ObjectNode contract = om.createObjectNode();
    contract.put("injector_contract_id", existingContractId);
    ObjectNode stepData = om.createObjectNode();
    stepData.put("inject_title", "existing contract step");
    stepData.set("inject_injector_contract", contract);
    ObjectNode stepNode = om.createObjectNode();
    stepNode.set("step_data", stepData);

    Scenario scenario = new Scenario();
    scenario.setId(UUID.randomUUID().toString());
    Workflow workflow = Workflow.builder().scenario(scenario).build();

    // -- Act --
    V1_DataImporter.StepDataResolution resolution =
        ReflectionTestUtils.invokeMethod(
            importer,
            "resolveStepData",
            stepNode,
            new HashMap<String, String>(),
            new HashMap<String, Base>(),
            workflow);
    String resolved = resolution.stepData();
    JsonNode json = assertDoesNotThrow(() -> new ObjectMapper().readTree(resolved));

    // -- Assert --
    assertEquals(
        contractCountBefore,
        injectorContractRepository.count(),
        "An already-existing contract must not be re-created during step resolution");
    assertEquals(
        existingContractId,
        json.get("inject_injector_contract").get("injector_contract_id").asText());
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataWithSourceInjectorIdAndLinkedTargetInjector_when_importing_should_rewriteInjectInjectorToTargetId()
          throws Exception {
    // -- Arrange --
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf rewrite inject injector " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithStepTags(
            om, scenarioName, om.createArrayNode(), om.createArrayNode(), om.createArrayNode());
    ObjectNode stepData =
        (ObjectNode)
            importData.get("scenario_workflow").get("workflow_steps").get(0).get("step_data");
    String sourceInjectorId = UUID.randomUUID().toString();
    stepData.put("inject_injector", sourceInjectorId);

    String contractId =
        stepData.get("inject_injector_contract").get("injector_contract_id").asText();
    Injector targetInjector = new Injector();
    targetInjector.setId(UUID.randomUUID().toString());
    targetInjector.setName("target-injector-" + UUID.randomUUID());
    targetInjector.setType(NMAP_DUMMY_INJECTOR_TYPE);
    targetInjector = injectorRepository.save(targetInjector);

    InjectorContract contract = injectorContractRepository.findById(contractId).orElseThrow();
    contract.addInjector(targetInjector);
    injectorContractRepository.save(contract);

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertEquals(
        targetInjector.getId(),
        storedData.get("inject_injector").asText(),
        "inject_injector must be rewritten to the target injector linked to the resolved contract");
    assertNotEquals(
        sourceInjectorId,
        storedData.get("inject_injector").asText(),
        "the source-instance injector id must not survive the import");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataWithSourceInjectorIdAndNoLinkedTargetInjector_when_importing_should_dropInjectInjector()
          throws Exception {
    // -- Arrange --
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf drop inject injector " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithStepTags(
            om, scenarioName, om.createArrayNode(), om.createArrayNode(), om.createArrayNode());
    ObjectNode stepData =
        (ObjectNode)
            importData.get("scenario_workflow").get("workflow_steps").get(0).get("step_data");
    stepData.put("inject_injector", UUID.randomUUID().toString());

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    JsonNode storedData = readStoredStepData(scenarioName, om);
    assertFalse(
        storedData.has("inject_injector"),
        "inject_injector must be removed when the resolved target contract has no linked injector");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_missingContractWithManualPayload_when_importing_should_bindInjectInjectorToResolvedOpenaevInjector()
          throws Exception {
    // -- Arrange --
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());

    // -- Act --
    this.importer.importData(
        readMissingContractWithPayloadFixture(),
        Map.of(),
        null,
        null,
        null,
        null,
        Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    JsonNode storedData =
        readStoredStepData("wf step missing contract payload", new ObjectMapper());
    String resolvedContractId =
        storedData.get("inject_injector_contract").get("injector_contract_id").asText();
    InjectorContract resolvedContract =
        injectorContractRepository.findById(resolvedContractId).orElseThrow();
    Injector resolvedInjector = resolvedContract.getFirstInjector();
    assertNotNull(
        resolvedInjector,
        "the recreated MANUAL payload contract must be linked to a real target OpenAEV Implant injector");
    assertTrue(
        storedData.hasNonNull("inject_injector"),
        "inject_injector must be present after sanitization on this MANUAL/OpenAEV path");
    assertEquals(
        resolvedInjector.getId(),
        storedData.get("inject_injector").asText(),
        "inject_injector must point to the actual target injector linked to the resolved contract");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_workflowStepReferencingMissingContractWithRecreatablePayload_when_importing_should_recreatePayloadAndImportStep()
          throws Exception {
    // -- Arrange --
    // The workflow step references a contract absent from the target but carries a self-sufficient
    // embedded payload (MANUAL Command). Such a payload is recreatable locally: with a
    // payload-supporting injector registered, the step must NOT be skipped upfront —
    // resolveStepData recreates the payload/contract and the step is imported normally.
    // (Regression: it used to be wrongly skipped because the read-only external-id lookup found
    // nothing and the contract was absent.)
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());

    ObjectMapper mapper = new ObjectMapper();
    JsonNode importData =
        mapper.readTree(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/import-scenario-workflow-step-missing-contract-with-payload.json")));
    String sourceContractId = "cccccccc-0003-0003-0003-000000000003";
    assertTrue(injectorContractRepository.findById(sourceContractId).isEmpty());
    long payloadCountBefore = payloadRepository.count();

    // -- Act --
    ImportResult result =
        this.importer.importData(
            importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    // The embedded payload is recreated and attached to a freshly created injector contract.
    List<Payload> recreated = new ArrayList<>();
    payloadRepository
        .findAll()
        .forEach(
            p -> {
              if ("step missing contract payload".equals(p.getName())) {
                recreated.add(p);
              }
            });
    assertEquals(
        1, recreated.size(), "the embedded payload must be recreated exactly once from step_data");
    assertEquals(
        payloadCountBefore + 1,
        payloadRepository.count(),
        "exactly one payload must be created by the recreatable step");
    assertTrue(
        injectorContractRepository.findInjectorContractByPayload(recreated.getFirst()).isPresent(),
        "the recreated payload must be attached to an injector contract (no orphan)");

    // The workflow step is imported (not skipped).
    Workflow workflow = findImportedWorkflow("wf step missing contract payload");
    assertEquals(
        1,
        stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId()).size(),
        "a step carrying a recreatable embedded payload must be imported, not skipped");

    // No missing action reported: the dependency was resolved by recreation.
    assertTrue(
        result.missingActions().isEmpty(),
        "recreating the embedded payload must not report a missing action");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_sameSimulationImportedTwiceWithReadableContract_when_importing_should_reuseExistingPayload()
          throws Exception {
    // -- Arrange --
    // Re-importing the same chaining simulation must NOT pile up duplicate payloads: the embedded
    // Command payload is recreated once, then reused on the second import — provided the current
    // user can read its injector contract (RBAC on THREAT_ARSENAL grants).
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    long payloadCountBefore = payloadRepository.count();

    // First import: creates the payload + its injector contract.
    this.importer.importData(
        readMissingContractWithPayloadFixture(),
        Map.of(),
        null,
        null,
        null,
        null,
        Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    Payload created = findSinglePayloadByName("step missing contract payload");
    assertEquals(
        payloadCountBefore + 1, payloadRepository.count(), "first import creates one payload");
    InjectorContract createdContract =
        injectorContractRepository.findInjectorContractByPayload(created).orElseThrow();

    // Grant the current user read access on the created contract so the dedup RBAC check passes.
    addGrantToCurrentUser(
        Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL,
        Grant.GRANT_TYPE.OBSERVER,
        createdContract.getId());
    long payloadCountAfterFirst = payloadRepository.count();

    // -- Act --
    // Second import of the SAME simulation.
    this.importer.importData(
        readMissingContractWithPayloadFixture(),
        Map.of(),
        null,
        null,
        null,
        null,
        Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    assertEquals(
        payloadCountAfterFirst,
        payloadRepository.count(),
        "the second import must reuse the existing readable payload, not create a duplicate");
    assertEquals(
        1,
        countPayloadsByName("step missing contract payload"),
        "there must remain exactly one payload with that name after re-import");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_sameSimulationImportedTwiceWithUnreadableContract_when_importing_should_recreatePayload()
          throws Exception {
    // -- Arrange --
    // RBAC is a priority filter but must NEVER block the import: when the equivalent existing
    // payload's contract is NOT readable by the current user, the dedup cascade falls through and a
    // fresh payload is recreated (product goal: maximise the chance the imported simulation runs).
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());

    // First import: creates the payload + contract, WITHOUT granting the user any read access.
    this.importer.importData(
        readMissingContractWithPayloadFixture(),
        Map.of(),
        null,
        null,
        null,
        null,
        Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    long payloadCountAfterFirst = payloadRepository.count();
    assertEquals(1, countPayloadsByName("step missing contract payload"));

    // -- Act --
    // Second import: the existing payload's contract is unreadable (no grant) -> RBAC denied.
    this.importer.importData(
        readMissingContractWithPayloadFixture(),
        Map.of(),
        null,
        null,
        null,
        null,
        Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    assertEquals(
        payloadCountAfterFirst + 1,
        payloadRepository.count(),
        "an unreadable equivalent must not stop the cascade: a fresh payload is recreated");
    assertEquals(
        2,
        countPayloadsByName("step missing contract payload"),
        "the denied candidate is skipped and the payload is duplicated (RBAC never blocks import)");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataContractIdExistingOnlyInAnotherTenant_when_resolving_should_notTreatForeignContractAsPresent()
          throws Exception {
    // -- Arrange --
    // The step_data references a contract id that exists ONLY in ANOTHER tenant. InjectorContract's
    // PK is (tenant_id, id), so a non-tenant-scoped existence check would treat the foreign row as
    // "already present" and persist step_data pointing at another tenant's contract. The embedded
    // payload must instead be recreated locally, exactly as if the contract did not exist at all.
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    Tenant foreignTenant =
        tenantRepository.save(TenantFixture.getTenant("v1-import-foreign-tenant-contract"));
    String sourceContractId = "cccccccc-0003-0003-0003-000000000003";
    persistResolvableStepContractForTenant(sourceContractId, foreignTenant);

    JsonNode stepNode =
        readMissingContractWithPayloadFixture()
            .get("scenario_workflow")
            .get("workflow_steps")
            .get(0);
    Scenario scenario = new Scenario();
    scenario.setId(UUID.randomUUID().toString());
    Workflow workflow = Workflow.builder().scenario(scenario).build();

    // Simulate a session WITHOUT the Hibernate tenant filter (the filter aspect only arms it around
    // @Transactional entry points): the tenant-scoped repository query must protect on its own.
    entityManager.flush();
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");

    // -- Act --
    V1_DataImporter.StepDataResolution resolution =
        ReflectionTestUtils.invokeMethod(
            importer,
            "resolveStepData",
            stepNode,
            new HashMap<String, String>(),
            new HashMap<String, Base>(),
            workflow);

    // -- Assert --
    assertNotNull(resolution);
    assertFalse(
        resolution.isFailed(), "the embedded payload is recreatable: no missing step expected");
    JsonNode resolved = new ObjectMapper().readTree(resolution.stepData());
    String rewrittenContractId =
        resolved.get("inject_injector_contract").get("injector_contract_id").asText();
    assertNotEquals(
        sourceContractId,
        rewrittenContractId,
        "step_data must never keep a contract id that only exists in another tenant");
    assertTrue(
        injectorContractRepository.existsByContractIdAndTenant(
            rewrittenContractId, TenantContext.getCurrentTenant()),
        "the step must point to a contract recreated in the current tenant");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_importedPayloadUuidExistingOnlyInAnotherTenant_when_deduplicating_should_notReuseForeignPayload()
          throws Exception {
    // -- Arrange --
    // The imported payload carries a source UUID that collides with a payload belonging to ANOTHER
    // tenant. findById is a PK load that bypasses the Hibernate tenant filter, so the dedup lookup
    // must be tenant-scoped: the foreign payload (and its contract) must never be reused, even when
    // the current user would pass the RBAC check on the foreign contract.
    Tenant foreignTenant =
        tenantRepository.save(TenantFixture.getTenant("v1-import-foreign-tenant-payload"));
    String sourcePayloadId = "cccccccc-0003-0003-0003-000000000004";
    Command foreignPayload = PayloadFixture.createCommand("sh", "echo hello", null, null);
    foreignPayload.setId(sourcePayloadId);
    foreignPayload.setName("step missing contract payload");
    foreignPayload.setTenant(foreignTenant);
    payloadRepository.save(foreignPayload);
    InjectorContract foreignContract = new InjectorContract();
    foreignContract.setId(UUID.randomUUID().toString());
    foreignContract.setTenant(foreignTenant);
    foreignContract.setContent("{}");
    foreignContract.setLabels(Map.of("en", "foreign tenant contract"));
    foreignContract.setCustom(false);
    foreignContract.setManual(false);
    foreignContract.setNeedsExecutor(false);
    foreignContract.setPlatforms(new Endpoint.PLATFORM_TYPE[0]);
    foreignContract.setPayload(foreignPayload);
    injectorContractRepository.save(foreignContract);
    // Make the RBAC check pass on the foreign contract, so only tenant scoping protects.
    addGrantToCurrentUser(
        Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL,
        Grant.GRANT_TYPE.OBSERVER,
        foreignContract.getId());

    JsonNode payloadNode =
        readMissingContractWithPayloadFixture()
            .get("scenario_workflow")
            .get("workflow_steps")
            .get(0)
            .get("step_data")
            .get("inject_injector_contract")
            .get("injector_contract_payload");

    // Simulate a session WITHOUT the Hibernate tenant filter (background paths): the tenant-scoped
    // lookup must protect on its own.
    entityManager.flush();
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");

    // -- Act --
    Optional<InjectorContract> reusable =
        ReflectionTestUtils.invokeMethod(
            importer, "findReusableContractForImportedPayload", payloadNode);

    // -- Assert --
    assertNotNull(reusable);
    assertTrue(
        reusable.isEmpty(),
        "a payload UUID colliding with another tenant's payload must never be reused by dedup");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_contractIdExistingInCurrentAndForeignTenant_when_rewritingInjectInjector_should_neverResolveForeignInjector() {
    // -- Arrange --
    // InjectorContract's PK is (tenant_id, id): the same contract id can exist in several tenants
    // (e.g. starter-pack contracts imported into every tenant). A bare findById matches only
    // compositeId.id, so without the Hibernate tenant filter it either throws on the duplicate or
    // resolves the FOREIGN tenant's contract - and rewriteInjectInjector would then write the
    // foreign tenant's injector id into inject_injector. The tenant-scoped lookup must resolve the
    // CURRENT tenant's contract only.
    Tenant foreignTenant =
        tenantRepository.save(TenantFixture.getTenant("v1-import-foreign-tenant-injector"));
    String contractId = UUID.randomUUID().toString();
    // Current tenant: the contract exists but has NO linked injector.
    persistResolvableStepContract(contractId);
    // Foreign tenant: the SAME contract id, linked to a foreign injector.
    Injector foreignInjector = new Injector();
    foreignInjector.setId(UUID.randomUUID().toString());
    foreignInjector.setName("foreign-injector-" + UUID.randomUUID());
    foreignInjector.setType("v1_import_foreign_injector_type");
    foreignInjector.setTenantId(foreignTenant.getId());
    foreignInjector = injectorRepository.save(foreignInjector);
    InjectorContract foreignContract = new InjectorContract();
    foreignContract.setId(contractId);
    foreignContract.setTenant(foreignTenant);
    foreignContract.setContent("{}");
    foreignContract.setLabels(Map.of("en", "foreign tenant contract with injector"));
    foreignContract.setCustom(false);
    foreignContract.setManual(false);
    foreignContract.setNeedsExecutor(false);
    foreignContract.setPlatforms(new Endpoint.PLATFORM_TYPE[0]);
    foreignContract.addInjector(foreignInjector);
    injectorContractRepository.save(foreignContract);

    ObjectMapper om = new ObjectMapper();
    ObjectNode dataObject = om.createObjectNode();
    ObjectNode contractNode = om.createObjectNode();
    contractNode.put("injector_contract_id", contractId);
    dataObject.set("inject_injector_contract", contractNode);
    dataObject.put("inject_injector", UUID.randomUUID().toString());

    // Simulate a session WITHOUT the Hibernate tenant filter (the filter aspect only arms it
    // around @Transactional entry points): the tenant-scoped query must protect on its own.
    entityManager.flush();
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");

    // -- Act --
    assertDoesNotThrow(
        () -> ReflectionTestUtils.invokeMethod(importer, "rewriteInjectInjector", dataObject));

    // -- Assert --
    assertFalse(
        dataObject.has("inject_injector"),
        "the current tenant's contract has no linked injector: inject_injector must be dropped,"
            + " never rewritten to the foreign tenant's injector");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_missingContractWithPayloadCarryingExistingTagId_when_importing_should_keepTagOnRecreatedContract()
          throws Exception {
    // -- Arrange --
    // QA regression: tags were dropped from the recreated payload. The step_data contract carries
    // bare tag ids (no root tag object seeds baseIds on this path); an id that already exists on
    // the target tenant must land on the recreated payload's contract instead of being dropped.
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    Tag existing = new Tag();
    existing.setName("v1-import-recreated-payload-tag-" + UUID.randomUUID());
    existing.setColor("#127796");
    existing = tagRepository.save(existing);
    String existingTagId = existing.getId();

    ObjectMapper om = new ObjectMapper();
    ObjectNode importData = (ObjectNode) readMissingContractWithPayloadFixture();
    ObjectNode contractNode =
        (ObjectNode)
            importData
                .get("scenario_workflow")
                .get("workflow_steps")
                .get(0)
                .get("step_data")
                .get("inject_injector_contract");
    contractNode.set("injector_contract_tags", tagIdArray(om, existingTagId));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Payload recreated = findSinglePayloadByName("step missing contract payload");
    InjectorContract recreatedContract =
        injectorContractRepository.findInjectorContractByPayload(recreated).orElseThrow();
    assertTrue(
        recreatedContract.getTags().stream().anyMatch(t -> existingTagId.equals(t.getId())),
        "a tag existing on the target tenant must be preserved on the recreated payload's"
            + " contract");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_missingContractWithPayloadCarryingScalarAttackPatternId_when_importing_should_keepAttackPatternOnRecreatedContract()
          throws Exception {
    // -- Arrange --
    // QA regression: attack patterns were dropped from the recreated payload. step_data arrays
    // already normalized on the source instance carry SCALAR ids that importAttackPattern used to
    // ignore; an id that still exists on the target tenant must land on the recreated payload's
    // contract instead of being dropped.
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    AttackPattern existing =
        attackPatternRepository.save(
            AttackPatternFixture.createAttackPatternsWithExternalId("T1105-" + UUID.randomUUID()));
    String existingAttackPatternId = existing.getId();

    ObjectMapper om = new ObjectMapper();
    ObjectNode importData = (ObjectNode) readMissingContractWithPayloadFixture();
    ObjectNode contractNode =
        (ObjectNode)
            importData
                .get("scenario_workflow")
                .get("workflow_steps")
                .get(0)
                .get("step_data")
                .get("inject_injector_contract");
    contractNode.set("injector_contract_attack_patterns", tagIdArray(om, existingAttackPatternId));

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Payload recreated = findSinglePayloadByName("step missing contract payload");
    InjectorContract recreatedContract =
        injectorContractRepository.findInjectorContractByPayload(recreated).orElseThrow();
    assertTrue(
        recreatedContract.getAttackPatterns().stream()
            .anyMatch(ap -> existingAttackPatternId.equals(ap.getId())),
        "a scalar attack pattern id existing on the target tenant must be preserved on the"
            + " recreated payload's contract");
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_workflowStepReferencingMissingContractWithoutPayload_when_importing_should_skipStepAndReportMissingAction()
          throws Exception {
    // -- Arrange --
    // A pure reference to an external-collector contract absent from the target and carrying NO
    // embedded payload (nothing to recreate). The injector type IS registered, so the only
    // unresolvable dependency is the injector contract itself: the step must be skipped and the
    // missing action reported (the "keep current behaviour" branch of the resolvability check).
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode importData =
        (ObjectNode)
            mapper.readTree(
                Files.readAllBytes(
                    Paths.get(
                        "src/test/resources/importer-v1/import-scenario-workflow-step-missing-contract-with-payload.json")));
    // Drop the embedded payload: the contract becomes a pure, non-recreatable reference.
    ObjectNode contract =
        (ObjectNode)
            importData
                .get("scenario_workflow")
                .get("workflow_steps")
                .get(0)
                .get("step_data")
                .get("inject_injector_contract");
    contract.remove("injector_contract_payload");
    String sourceContractId = "cccccccc-0003-0003-0003-000000000003";
    assertTrue(injectorContractRepository.findById(sourceContractId).isEmpty());
    long payloadCountBefore = payloadRepository.count();

    // -- Act --
    ImportResult result =
        this.importer.importData(
            importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    assertEquals(
        payloadCountBefore,
        payloadRepository.count(),
        "a non-recreatable step must not create any payload");
    assertTrue(
        injectorContractRepository.findById(sourceContractId).isEmpty(),
        "a non-recreatable step must not recreate its contract");

    Workflow workflow = findImportedWorkflow("wf step missing contract payload");
    assertEquals(
        0,
        stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId()).size(),
        "a step referencing a missing contract with no embedded payload must be skipped");

    assertEquals(1, result.missingActions().size());
    MissingImportedAction missing = result.missingActions().getFirst();
    assertEquals("InjectorContract/Payload", missing.type());
    assertEquals("step inject missing contract with payload", missing.name());
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_stepDataEmbeddedPayloadCreationFailsSilently_when_resolving_should_reportStepAsMissing() {
    // -- Arrange --
    // resolveStepData attempts to recreate the embedded payload via the payload creation service.
    // When no payload-supporting injector is registered, that service returns a transient,
    // never-persisted injector contract whose id is null (silent business failure). resolveStepData
    // must detect this and surface the step as missing so importWorkflowSteps skips it exactly like
    // evaluateChainingStepResolvability would — instead of persisting a step with a broken contract
    // reference.
    // NOTE: no payload injector is registered here on purpose, so createPayload cannot build a
    // contract.
    ObjectMapper om = new ObjectMapper();
    ObjectNode payload = om.createObjectNode();
    payload.put("payload_type", "Command");
    payload.put("payload_name", "silent failure payload");
    payload.put("payload_description", "");
    payload.put("payload_source", "MANUAL");
    payload.put("payload_status", "VERIFIED");
    payload.put("payload_execution_arch", "ALL_ARCHITECTURES");
    payload.put("payload_elevation_required", false);
    payload.set("payload_platforms", tagIdArray(om, "Linux"));
    payload.set("payload_arguments", om.createArrayNode());
    payload.set("payload_prerequisites", om.createArrayNode());
    payload.set("payload_tags", om.createArrayNode());
    payload.set("payload_attack_patterns", om.createArrayNode());
    payload.put("command_executor", "sh");
    payload.put("command_content", "echo hello");

    ObjectNode contract = om.createObjectNode();
    contract.put("injector_contract_id", UUID.randomUUID().toString());
    contract.put("injector_contract_injector_type", "openaev_implant");
    contract.set("injector_contract_payload", payload);

    ObjectNode stepData = om.createObjectNode();
    stepData.put("inject_title", "silent failure step");
    stepData.set("inject_injector_contract", contract);
    ObjectNode stepNode = om.createObjectNode();
    stepNode.set("step_data", stepData);

    Scenario scenario = new Scenario();
    scenario.setId(UUID.randomUUID().toString());
    Workflow workflow = Workflow.builder().scenario(scenario).build();

    // -- Act --
    V1_DataImporter.StepDataResolution resolution =
        ReflectionTestUtils.invokeMethod(
            importer,
            "resolveStepData",
            stepNode,
            new HashMap<String, String>(),
            new HashMap<String, Base>(),
            workflow);

    // -- Assert --
    assertNotNull(resolution);
    assertTrue(
        resolution.isFailed(),
        "a silent payload-creation failure must be reported as a missing step");
    assertNull(
        resolution.stepData(), "a failed resolution must not carry any step_data to persist");
    V1_DataImporter.SkippedWorkflowStep skipped = resolution.skipped();
    assertEquals(V1_DataImporter.SkippedWorkflowStepType.INJECTOR_CONTRACT, skipped.type());
    assertEquals("silent failure step", skipped.injectTitle());
    assertEquals(
        "silent failure payload",
        skipped.resourceName(),
        "the missing step must carry the embedded payload name as its resource");
  }

  // ---------------------------------------------------------------------------
  // Chaining import: steps whose injector / injector contract / payload cannot be resolved on the
  // target instance are skipped (not recreated) and reported back via ImportResult so the front can
  // display a partial-import toast.
  // ---------------------------------------------------------------------------

  @Test
  @Transactional
  @WithMockUser
  void
      given_workflowStepReferencingMissingInjector_when_importing_should_skipStepAndReportMissingInjector() {
    // -- Arrange --
    // The step's contract is resolvable (persisted on the target by the builder) but its injector
    // type is not registered on the instance: the step must be skipped and reported as a missing
    // Injector.
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf missing injector " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithStepTags(
            om, scenarioName, om.createArrayNode(), om.createArrayNode(), om.createArrayNode());
    ObjectNode contract =
        (ObjectNode)
            importData
                .get("scenario_workflow")
                .get("workflow_steps")
                .get(0)
                .get("step_data")
                .get("inject_injector_contract");
    contract.put("injector_contract_injector_type", "openaev_unregistered_type");

    // -- Act --
    ImportResult result =
        this.importer.importData(
            importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Workflow workflow = findImportedWorkflow(scenarioName);
    assertEquals(
        0,
        stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId()).size(),
        "a step whose injector type is missing must be skipped");
    assertEquals(1, result.missingActions().size());
    MissingImportedAction missing = result.missingActions().getFirst();
    assertEquals("Injector", missing.type());
    assertEquals("chaining step inject with tags", missing.name());
  }

  @Test
  @Transactional
  @WithMockUser
  void
      given_workflowStepWithResolvableContract_when_importing_should_createStepAndReportNoMissingAction() {
    // -- Arrange --
    // The step references a contract that exists on the target (persisted by the builder) and no
    // injector type: it is resolvable and must be imported normally, with an empty ImportResult.
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf resolvable step " + UUID.randomUUID();
    ObjectNode importData =
        buildScenarioWorkflowWithStepTags(
            om, scenarioName, om.createArrayNode(), om.createArrayNode(), om.createArrayNode());

    // -- Act --
    ImportResult result =
        this.importer.importData(
            importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Workflow workflow = findImportedWorkflow(scenarioName);
    assertEquals(
        1,
        stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId()).size(),
        "a resolvable step must be imported");
    assertTrue(
        result.missingActions().isEmpty(),
        "a fully resolvable import must not report any missing action");
  }

  @Test
  void given_importResult_should_beNullSafeAndCarryActions() {
    // ImportResult normalises a null action list to an empty one and exposes its actions as-is.
    assertTrue(new ImportResult(null).missingActions().isEmpty(), "null actions -> empty list");
    assertTrue(ImportResult.empty().missingActions().isEmpty());

    MissingImportedAction action = new MissingImportedAction("Injector", "step X");
    ImportResult result = new ImportResult(new ArrayList<>(List.of(action)));
    assertEquals(1, result.missingActions().size());
    assertEquals("Injector", result.missingActions().getFirst().type());
    assertEquals("step X", result.missingActions().getFirst().name());
  }

  @Test
  @Transactional
  @WithMockUser
  void given_nullWorkflowOrNonObjectData_when_sanitizing_should_returnFallbackUnchanged() {
    // -- Arrange --
    // sanitizateStepData is defensive: without a workflow to bind to (or a non-object payload) it
    // must return the raw fallback untouched rather than throw.
    ObjectMapper om = new ObjectMapper();
    ObjectNode dataObject = om.createObjectNode();
    dataObject.put("inject_title", "no workflow");
    String fallback = "{\"raw\":true}";

    // -- Act / Assert --
    String nullWorkflowResult =
        ReflectionTestUtils.invokeMethod(
            importer,
            "sanitizateStepData",
            dataObject,
            fallback,
            null,
            new HashMap<String, Base>());
    assertEquals(fallback, nullWorkflowResult, "null workflow -> fallback returned unchanged");

    Scenario scenario = new Scenario();
    scenario.setId(UUID.randomUUID().toString());
    Workflow workflow = Workflow.builder().scenario(scenario).build();
    String nonObjectResult =
        ReflectionTestUtils.invokeMethod(
            importer,
            "sanitizateStepData",
            om.getNodeFactory().textNode("not-an-object"),
            fallback,
            workflow,
            new HashMap<String, Base>());
    assertEquals(fallback, nonObjectResult, "non-object step_data -> fallback returned unchanged");
  }

  @Test
  @Transactional
  void given_nullOrNonArrayField_when_rewritingTagIds_should_skipUnresolvableEntries() {
    // -- Arrange --
    // rewriteImportedTagIds must tolerate a missing field, JSON null / non-textual entries, and
    // unresolved ids: only resolvable textual ids are kept, everything else is dropped safely.
    ObjectMapper om = new ObjectMapper();
    Tag resolvedTag = new Tag();
    resolvedTag.setId(UUID.randomUUID().toString());
    Map<String, Base> baseIds = new HashMap<>();
    baseIds.put("source-tag-id", resolvedTag);

    ArrayNode mixed = om.createArrayNode();
    mixed.addNull(); // null element -> skipped (2409)
    mixed.add(42); // non-textual element -> skipped (2409)
    mixed.add(UUID.randomUUID().toString()); // textual but unresolved -> dropped (2412 false)
    mixed.add("source-tag-id"); // resolvable -> kept (2412 true)
    ObjectNode parent = om.createObjectNode();
    parent.set("inject_tags", mixed);

    // -- Act --
    ReflectionTestUtils.invokeMethod(
        importer, "rewriteImportedTagIds", parent, "inject_tags", baseIds);

    // -- Assert --
    assertEquals(
        List.of(resolvedTag.getId()),
        tagIdList(parent.get("inject_tags")),
        "Only the resolvable tag id must remain after rewriting");

    // Missing field must be a no-op (no NPE, node stays absent).
    ObjectNode empty = om.createObjectNode();
    ReflectionTestUtils.invokeMethod(
        importer, "rewriteImportedTagIds", empty, "inject_tags", baseIds);
    assertFalse(empty.has("inject_tags"), "Absent field must be left untouched");
  }

  @Test
  @Transactional
  void given_nullNode_when_extractingInjectorContractId_should_returnNull() {
    // -- Arrange / Act / Assert --
    // extractInjectorContractId defends against absent/null contract nodes used by resolveStepData.
    ObjectMapper om = new ObjectMapper();
    String fromNull =
        ReflectionTestUtils.invokeMethod(importer, "extractInjectorContractId", om.nullNode());
    assertNull(fromNull, "A JSON null contract node must resolve to a null id");
  }

  @Test
  @Transactional
  @WithMockUser
  void given_workflowNumericConfigFieldsExplicitNull_when_importing_should_applyDefaults() {
    // -- Arrange --
    // Partial export: numeric config fields are present but explicitly JSON null. They must be
    // treated like "absent" (no override), and safe mode explicitly true must be honoured.
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf config null numerics " + UUID.randomUUID();
    ObjectNode workflowNode = om.createObjectNode();
    workflowNode.putNull("workflow_max_attempts");
    workflowNode.putNull("workflow_max_temporal_rate_seconds");
    workflowNode.putNull("workflow_timeout_seconds");
    workflowNode.put("workflow_safe_mode_enabled", true);
    workflowNode.set("workflow_steps", om.createArrayNode());
    ObjectNode importData = buildScenarioImportWithWorkflow(om, scenarioName, workflowNode);

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Workflow workflow = findImportedWorkflow(scenarioName);
    assertEquals(
        DEFAULT_TIMEOUT_SECONDS,
        workflow.getTimeoutSeconds(),
        "explicit null timeout_seconds -> DEFAULT_TIMEOUT_SECONDS");
    assertTrue(workflow.isSafeModeEnabled(), "explicit safe_mode_enabled=true must be honoured");
  }

  @Test
  @Transactional
  @WithMockUser
  void given_exerciseWorkflow_when_importing_should_bindWorkflowToSimulation() {
    // -- Arrange --
    // Workflows can also be attached to a simulation (exercise) export. The simulation branch of
    // importWorkflow must associate the workflow with the exercise instead of a scenario.
    Exercise exercise = new Exercise();
    exercise.setName("wf simulation " + UUID.randomUUID());
    exercise.setFrom("test@openaev.io");
    exercise = exerciseRepository.save(exercise);

    ObjectMapper om = new ObjectMapper();
    ObjectNode workflowNode = om.createObjectNode();
    workflowNode.put("workflow_version", 2);
    workflowNode.set("workflow_steps", om.createArrayNode());
    ObjectNode importData = om.createObjectNode();
    importData.set("exercise_workflow", workflowNode);

    // -- Act --
    ReflectionTestUtils.invokeMethod(
        importer,
        "importWorkflow",
        importData,
        "exercise_",
        exercise,
        null,
        new HashMap<String, Base>(),
        new HashMap<String, String>());

    // -- Assert --
    Workflow workflow =
        workflowRepository.findBySimulation_IdAndStatus(exercise.getId(), WorkflowStatus.TEMPLATE);
    assertNotNull(workflow, "The workflow must be bound to the simulation");
    assertEquals(exercise.getId(), workflow.getSimulation().getId());
    assertNull(workflow.getScenario(), "A simulation workflow must not reference a scenario");
  }

  // ---------------------------------------------------------------------------
  // Shared/duplicate conditions across workflow steps (import pipeline).
  //
  // A root event condition can be referenced by the step_conditions of several steps in the
  // exported file (same condition_id). It must be imported as a SINGLE Condition entity reused
  // with an additional conditions_steps link per step, NOT recreated once per step (which showed
  // N separate "Event" nodes in the Logic tab instead of one shared event connected to N actions).
  // Same class of bug as StepService.copyStepConditionTemplate (PR #7119) but in the JSON import
  // pipeline (importWorkflowSteps / importConditionNodes).
  // ---------------------------------------------------------------------------

  @Test
  @Transactional
  @WithMockUser
  void
      given_rootConditionSharedBetweenTwoSteps_when_importing_should_createSingleConditionLinkedToBothSteps() {
    // -- Arrange --
    // Two steps whose step_conditions reference the SAME exported root event condition_id
    // (reproduces the SEB_AD export pattern).
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf shared root condition " + UUID.randomUUID();
    String contractId = UUID.randomUUID().toString();
    persistResolvableStepContract(contractId);

    String sharedCondId = "shared-event-0001";
    ArrayNode steps = om.createArrayNode();
    ArrayNode conditionsA = om.createArrayNode();
    conditionsA.add(rootEventCondition(om, sharedCondId, "shared-event-root", "SUCCESS"));
    steps.add(buildInjectExecutionStep(om, "shared-step-a", 7, contractId, conditionsA));
    ArrayNode conditionsB = om.createArrayNode();
    conditionsB.add(rootEventCondition(om, sharedCondId, "shared-event-root", "SUCCESS"));
    steps.add(buildInjectExecutionStep(om, "shared-step-b", 9, contractId, conditionsB));

    ObjectNode workflowNode = om.createObjectNode();
    workflowNode.set("workflow_steps", steps);
    ObjectNode importData = buildScenarioImportWithWorkflow(om, scenarioName, workflowNode);

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Workflow workflow = findImportedWorkflow(scenarioName);
    List<Step> workflowSteps =
        stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());
    assertEquals(2, workflowSteps.size(), "both steps must be imported");
    Step stepA =
        workflowSteps.stream().filter(s -> s.getLimitExecution() == 7).findFirst().orElseThrow();
    Step stepB =
        workflowSteps.stream().filter(s -> s.getLimitExecution() == 9).findFirst().orElseThrow();

    // A single Condition entity must exist for the shared root event, not one per step.
    List<Condition> sharedRoots =
        conditionRepository.findAll().stream()
            .filter(c -> workflow.getId().equals(c.getWorkflowId()))
            .filter(c -> "shared-event-root".equals(c.getName()))
            .toList();
    assertEquals(
        1,
        sharedRoots.size(),
        "a root event shared between steps must be imported as ONE Condition, not duplicated per step");
    Condition shared = sharedRoots.getFirst();

    // The single event must be linked to BOTH steps via conditions_steps.
    assertEquals(
        List.of(shared.getId()),
        conditionRepository.findAllLinkedToStepId(stepA.getId()).stream()
            .map(Condition::getId)
            .toList(),
        "the shared event must be linked to the first step");
    assertEquals(
        List.of(shared.getId()),
        conditionRepository.findAllLinkedToStepId(stepB.getId()).stream()
            .map(Condition::getId)
            .toList(),
        "the SAME shared event must also be linked to the second step");
  }

  @Test
  @Transactional
  @WithMockUser
  void given_conditionIdDuplicatedWithinSingleStep_when_importing_should_createSingleCondition() {
    // -- Arrange --
    // The step_conditions of ONE step repeats the same child condition_id twice (the "1ef8d76a"
    // duplicate observed in the SEB_AD export). The duplicate must be ignored (already wired via
    // its parent on first creation), producing a single Condition entity.
    ObjectMapper om = new ObjectMapper();
    String scenarioName = "wf duplicated condition in step " + UUID.randomUUID();
    String contractId = UUID.randomUUID().toString();
    persistResolvableStepContract(contractId);

    ObjectNode root = rootEventCondition(om, "dup-root-0001", "dup-root", "SUCCESS");
    ObjectNode child = om.createObjectNode();
    child.put("condition_id", "dup-child-0001");
    child.put("condition_key", "port");
    child.set("condition_key_types", tagIdArray(om, "port"));
    child.put("condition_type", "GT");
    child.put("condition_value", "80");
    child.put("condition_name", "dup-child");
    child.put("condition_parent_id", "dup-root-0001");
    child.put("condition_is_root", false);

    ArrayNode conditions = om.createArrayNode();
    conditions.add(root);
    conditions.add(child);
    conditions.add(child.deepCopy()); // same condition_id repeated within the same step

    ArrayNode steps = om.createArrayNode();
    steps.add(buildInjectExecutionStep(om, "dup-step-0001", 7, contractId, conditions));
    ObjectNode workflowNode = om.createObjectNode();
    workflowNode.set("workflow_steps", steps);
    ObjectNode importData = buildScenarioImportWithWorkflow(om, scenarioName, workflowNode);

    // -- Act --
    this.importer.importData(
        importData, Map.of(), null, null, null, null, Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // -- Assert --
    Workflow workflow = findImportedWorkflow(scenarioName);
    List<Condition> workflowConditions =
        conditionRepository.findAll().stream()
            .filter(c -> workflow.getId().equals(c.getWorkflowId()))
            .toList();
    assertEquals(
        2,
        workflowConditions.size(),
        "a condition_id duplicated within one step must yield a single entity (one root + one child)");
    assertEquals(
        1,
        workflowConditions.stream().filter(c -> "dup-child".equals(c.getName())).count(),
        "the duplicated child condition must be created exactly once");

    Step step =
        stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId()).getFirst();
    assertEquals(
        1,
        conditionRepository.findAllLinkedToStepId(step.getId()).size(),
        "only the single root event must be linked to the step (no duplicate link)");
  }

  // -- UTILS --

  /**
   * Builds an INJECT_EXECUTION workflow step referencing a resolvable contract and carrying the
   * given step_conditions array.
   */
  private ObjectNode buildInjectExecutionStep(
      ObjectMapper om, String stepId, int limitExecution, String contractId, ArrayNode conditions) {
    ObjectNode stepData = om.createObjectNode();
    stepData.put("inject_id", UUID.randomUUID().toString());
    stepData.put("inject_title", "shared condition step " + stepId);
    ObjectNode contract = om.createObjectNode();
    contract.put("injector_contract_id", contractId);
    stepData.set("inject_injector_contract", contract);

    ObjectNode step = om.createObjectNode();
    step.put("step_id", stepId);
    step.put("step_action_class", "INJECT_EXECUTION");
    step.put("step_limit_execution", limitExecution);
    step.set("step_data", stepData);
    step.set("step_conditions", conditions);
    return step;
  }

  /** Builds a root event condition node (EQ on {@code status}) with the given exported id. */
  private ObjectNode rootEventCondition(
      ObjectMapper om, String conditionId, String name, String value) {
    ObjectNode cond = om.createObjectNode();
    cond.put("condition_id", conditionId);
    cond.put("condition_key", "status");
    cond.set("condition_key_types", tagIdArray(om, "text"));
    cond.put("condition_type", "EQ");
    cond.put("condition_value", value);
    cond.put("condition_name", name);
    cond.put("condition_is_root", true);
    return cond;
  }

  /**
   * Builds a minimal scenario+workflow export with a single INJECT_EXECUTION step carrying tags.
   */
  private ObjectNode buildScenarioWorkflowWithStepTags(
      ObjectMapper om,
      String scenarioName,
      ArrayNode rootScenarioTags,
      ArrayNode injectorContractTags,
      ArrayNode injectTags) {
    ObjectNode root = om.createObjectNode();

    ObjectNode scenarioInfo = om.createObjectNode();
    scenarioInfo.put("scenario_id", UUID.randomUUID().toString());
    scenarioInfo.put("scenario_name", scenarioName);
    scenarioInfo.put("scenario_description", "");
    scenarioInfo.put("scenario_subtitle", "");
    scenarioInfo.put("scenario_category", "attack-scenario");
    scenarioInfo.put("scenario_main_focus", "incident-response");
    scenarioInfo.put("scenario_severity", "high");
    scenarioInfo.put("scenario_message_header", "");
    scenarioInfo.put("scenario_message_footer", "");
    scenarioInfo.put("scenario_mail_from", "test@openaev.io");
    scenarioInfo.set("scenario_tags", om.createArrayNode());
    scenarioInfo.set("scenario_documents", om.createArrayNode());
    root.set("scenario_information", scenarioInfo);

    // Root-level tag OBJECTS are resolved up-front by importTags() into baseIds.
    root.set("scenario_tags", rootScenarioTags);
    root.set("scenario_teams", om.createArrayNode());
    root.set("scenario_users", om.createArrayNode());
    root.set("scenario_organizations", om.createArrayNode());
    root.set("scenario_injects", om.createArrayNode());

    ObjectNode workflow = om.createObjectNode();
    workflow.put("workflow_version", 2);
    workflow.put("workflow_rate_limit_enabled", false);
    workflow.put("workflow_timeout_enabled", false);
    workflow.put("workflow_safe_mode_enabled", false);
    workflow.set("workflow_scope_rules", om.createArrayNode());
    workflow.set("workflow_scope_variables", om.createArrayNode());

    ObjectNode stepData = om.createObjectNode();
    stepData.put("inject_id", UUID.randomUUID().toString());
    stepData.put("inject_title", "chaining step inject with tags");
    stepData.set("inject_tags", injectTags);
    ObjectNode contract = om.createObjectNode();
    // The referenced contract must exist on the target instance, otherwise importWorkflowSteps now
    // skips the step (unresolvable injector contract) and no step_data is persisted. Persisting it
    // keeps this builder focused on the nested-id rewriting these tests actually assert.
    String stepContractId = UUID.randomUUID().toString();
    persistResolvableStepContract(stepContractId);
    contract.put("injector_contract_id", stepContractId);
    contract.set("injector_contract_tags", injectorContractTags);
    stepData.set("inject_injector_contract", contract);

    ObjectNode step = om.createObjectNode();
    step.put("step_id", "step-tags-0001");
    step.put("step_action_class", "INJECT_EXECUTION");
    step.put("step_limit_execution", 1);
    step.set("step_data", stepData);
    step.set("step_conditions", om.createArrayNode());

    ArrayNode steps = om.createArrayNode();
    steps.add(step);
    workflow.set("workflow_steps", steps);

    root.set("scenario_workflow", workflow);
    return root;
  }

  /**
   * Builds a minimal scenario+workflow export whose single INJECT_EXECUTION step carries the given
   * injector_contract_domains array (bare ids or full domain objects). Reuses the tags builder.
   */
  private ObjectNode buildScenarioWorkflowWithContractDomains(
      ObjectMapper om, String scenarioName, ArrayNode injectorContractDomains) {
    ObjectNode root =
        buildScenarioWorkflowWithStepTags(
            om, scenarioName, om.createArrayNode(), om.createArrayNode(), om.createArrayNode());
    ObjectNode contract =
        (ObjectNode)
            root.get("scenario_workflow")
                .get("workflow_steps")
                .get(0)
                .get("step_data")
                .get("inject_injector_contract");
    contract.set("injector_contract_domains", injectorContractDomains);
    return root;
  }

  private ArrayNode domainObjectArray(
      ObjectMapper om, String domainId, String domainName, String domainColor) {
    ObjectNode domain = om.createObjectNode();
    domain.put("domain_id", domainId);
    domain.put("domain_name", domainName);
    domain.put("domain_color", domainColor);
    ArrayNode arr = om.createArrayNode();
    arr.add(domain);
    return arr;
  }

  /**
   * Builds a minimal scenario+workflow export whose single INJECT_EXECUTION step carries the given
   * injector_contract_attack_patterns array (bare ids or full objects). Reuses the tags builder.
   */
  private ObjectNode buildScenarioWorkflowWithContractAttackPatterns(
      ObjectMapper om, String scenarioName, ArrayNode injectorContractAttackPatterns) {
    ObjectNode root =
        buildScenarioWorkflowWithStepTags(
            om, scenarioName, om.createArrayNode(), om.createArrayNode(), om.createArrayNode());
    ObjectNode contract =
        (ObjectNode)
            root.get("scenario_workflow")
                .get("workflow_steps")
                .get(0)
                .get("step_data")
                .get("inject_injector_contract");
    contract.set("injector_contract_attack_patterns", injectorContractAttackPatterns);
    return root;
  }

  private ArrayNode attackPatternObjectArray(
      ObjectMapper om, String attackPatternId, String externalId, String name) {
    ObjectNode attackPattern = om.createObjectNode();
    attackPattern.put("attack_pattern_id", attackPatternId);
    attackPattern.put("attack_pattern_name", name);
    attackPattern.put("attack_pattern_description", "");
    attackPattern.put("attack_pattern_external_id", externalId);
    attackPattern.set("attack_pattern_kill_chain_phases", om.createArrayNode());
    ArrayNode arr = om.createArrayNode();
    arr.add(attackPattern);
    return arr;
  }

  /**
   * Persists a minimal injector contract so that a workflow step referencing it is treated as
   * resolvable by importWorkflowSteps (contract present on the target instance).
   */
  private void persistResolvableStepContract(String contractId) {
    persistResolvableStepContractForTenant(
        contractId, new Tenant(TenantContext.getCurrentTenant()));
  }

  /**
   * Same as {@link #persistResolvableStepContract(String)} but under an explicit tenant, to build
   * cross-tenant collisions on the (tenant_id, id) composite PK.
   */
  private void persistResolvableStepContractForTenant(String contractId, Tenant tenant) {
    InjectorContract contract = new InjectorContract();
    contract.setId(contractId);
    contract.setTenant(tenant);
    contract.setContent("{}");
    contract.setLabels(Map.of("en", "resolvable step contract"));
    contract.setCustom(false);
    contract.setManual(false);
    contract.setNeedsExecutor(false);
    contract.setPlatforms(new Endpoint.PLATFORM_TYPE[0]);
    injectorContractRepository.save(contract);
  }

  /** Builds a minimal scenario export wrapping the provided scenario_workflow node as-is. */
  private ObjectNode buildScenarioImportWithWorkflow(
      ObjectMapper om, String scenarioName, ObjectNode workflowNode) {
    ObjectNode root = om.createObjectNode();
    ObjectNode scenarioInfo = om.createObjectNode();
    scenarioInfo.put("scenario_id", UUID.randomUUID().toString());
    scenarioInfo.put("scenario_name", scenarioName);
    scenarioInfo.put("scenario_description", "");
    scenarioInfo.put("scenario_subtitle", "");
    scenarioInfo.put("scenario_category", "attack-scenario");
    scenarioInfo.put("scenario_main_focus", "incident-response");
    scenarioInfo.put("scenario_severity", "high");
    scenarioInfo.put("scenario_message_header", "");
    scenarioInfo.put("scenario_message_footer", "");
    scenarioInfo.put("scenario_mail_from", "test@openaev.io");
    scenarioInfo.set("scenario_tags", om.createArrayNode());
    scenarioInfo.set("scenario_documents", om.createArrayNode());
    root.set("scenario_information", scenarioInfo);
    root.set("scenario_tags", om.createArrayNode());
    root.set("scenario_teams", om.createArrayNode());
    root.set("scenario_users", om.createArrayNode());
    root.set("scenario_organizations", om.createArrayNode());
    root.set("scenario_injects", om.createArrayNode());
    root.set("scenario_workflow", workflowNode);
    return root;
  }

  private Workflow findImportedWorkflow(String scenarioBaseName) {
    return findImportedWorkflow(
        "%s%s".formatted(scenarioBaseName, Constants.IMPORTED_OBJECT_NAME_SUFFIX), false);
  }

  private Workflow findImportedWorkflow(String scenarioName, boolean appendSuffix) {
    String expectedName =
        appendSuffix
            ? "%s%s".formatted(scenarioName, Constants.IMPORTED_OBJECT_NAME_SUFFIX)
            : scenarioName;
    Scenario scenario =
        scenarioRepository.findAll().stream()
            .filter(s -> expectedName.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    return workflowRepository
        .findByScenario_IdAndStatus(scenario.getId(), WorkflowStatus.TEMPLATE)
        .getFirst();
  }

  private ArrayNode tagObjects(ObjectMapper om, String tagId, String tagName, String tagColor) {
    ObjectNode tag = om.createObjectNode();
    tag.put("tag_id", tagId);
    tag.put("tag_name", tagName);
    tag.put("tag_color", tagColor);
    ArrayNode arr = om.createArrayNode();
    arr.add(tag);
    return arr;
  }

  private ArrayNode tagIdArray(ObjectMapper om, String... ids) {
    ArrayNode arr = om.createArrayNode();
    for (String id : ids) {
      arr.add(id);
    }
    return arr;
  }

  private List<String> tagIdList(JsonNode arrayNode) {
    List<String> ids = new ArrayList<>();
    if (arrayNode != null && arrayNode.isArray()) {
      arrayNode.forEach(n -> ids.add(n.asText()));
    }
    return ids;
  }

  private JsonNode readStoredStepData(String scenarioName, ObjectMapper om) throws IOException {
    String expectedName = "%s%s".formatted(scenarioName, Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    Scenario scenario =
        scenarioRepository.findAll().stream()
            .filter(s -> expectedName.equals(s.getName()))
            .findFirst()
            .orElseThrow();
    Workflow workflow =
        workflowRepository
            .findByScenario_IdAndStatus(scenario.getId(), WorkflowStatus.TEMPLATE)
            .getFirst();
    List<Step> steps = stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());
    assertEquals(1, steps.size());
    return om.readTree(steps.getFirst().getData());
  }

  private JsonNode readMissingContractWithPayloadFixture() throws IOException {
    return new ObjectMapper()
        .readTree(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/importer-v1/import-scenario-workflow-step-missing-contract-with-payload.json")));
  }

  private Payload findSinglePayloadByName(String name) {
    List<Payload> matches = new ArrayList<>();
    payloadRepository
        .findAll()
        .forEach(
            p -> {
              if (name.equals(p.getName())) {
                matches.add(p);
              }
            });
    assertEquals(1, matches.size(), "expected exactly one payload named '" + name + "'");
    return matches.getFirst();
  }

  private long countPayloadsByName(String name) {
    long[] count = {0};
    payloadRepository
        .findAll()
        .forEach(
            p -> {
              if (name.equals(p.getName())) {
                count[0]++;
              }
            });
    return count[0];
  }

  /** Deserializes step_data exactly like InjectExecutionStep.getInjectFromDataStep() does. */
  private Inject deserializeStepDataAsRun(String data) throws IOException {
    ObjectMapper om =
        new ObjectMapper()
            .findAndRegisterModules()
            .setInjectableValues(
                new InjectableValues.Std().addValue(EntityManager.class, this.entityManager))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return om.readValue(data, Inject.class);
  }

  private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
    for (Throwable t = throwable; t != null; t = t.getCause()) {
      if (type.isInstance(t)) {
        return true;
      }
    }
    return false;
  }

  private static Specification<Exercise> exerciseByName(@NotNull final String name) {
    return (root, query, cb) -> cb.equal(root.get("name"), name);
  }
}
