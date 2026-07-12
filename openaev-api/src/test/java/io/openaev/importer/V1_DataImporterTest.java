package io.openaev.importer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.utils.constants.Constants;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
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
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private ConditionRepository conditionRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

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
      testScenario_given_injects_nuclei_without_nuclei_injector_registered_when_starterpack_then_should_create_dummy_injector()
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

    // dummy injector should be created with 1 associated injector contract
    Injector dummyInjector =
        this.injectorRepository
            .findByTypeAndTenantId(NMAP_DUMMY_INJECTOR_TYPE, TenantContext.getCurrentTenant())
            .orElseThrow();
    List<InjectorContract> injectorContracts =
        injectorContractRepository.findByInjectorsContaining(dummyInjector);
    assertEquals(1, injectorContracts.size());
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

    // Conditions on step 1: root + child
    List<Condition> conds1 = conditionRepository.findAllLinkedToStepId(step1.getId());
    assertEquals(2, conds1.size());
    Condition root =
        conds1.stream().filter(c -> c.getConditionParent() == null).findFirst().orElseThrow();
    Condition child =
        conds1.stream().filter(c -> c.getConditionParent() != null).findFirst().orElseThrow();
    assertEquals(ConditionType.EQ, root.getType());
    assertEquals("SUCCESS", root.getValue());
    assertEquals(root.getId(), child.getConditionParent().getId());

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

    // -- Act --
    String resolvedStepData =
        ReflectionTestUtils.invokeMethod(
            importer, "resolveStepData", stepNode, resolvedContracts, new HashMap<>());
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

    // -- Act --
    String resolvedStepData =
        ReflectionTestUtils.invokeMethod(
            importer, "resolveStepData", stepNode, resolvedContracts, new HashMap<>());
    JsonNode resolvedJson = assertDoesNotThrow(() -> objectMapper.readTree(resolvedStepData));

    // -- Assert --
    assertTrue(resolvedJson.get("inject_injector_contract").isObject());
    assertEquals(
        newContractId,
        resolvedJson.get("inject_injector_contract").get("injector_contract_id").asText());
  }

  @Test
  @Transactional
  void
      given_stepDataWithRuntimeReferences_when_resolvingStepData_should_sanitizeStaleInjectFields() {
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
          "inject_injector_contract": {
            "injector_contract_id": "%s"
          }
        }
        """
            .formatted(contractId));

    // -- Act --
    String resolvedStepData =
        ReflectionTestUtils.invokeMethod(
            importer, "resolveStepData", stepNode, new HashMap<String, String>(), new HashMap<>());
    JsonNode resolvedJson = assertDoesNotThrow(() -> objectMapper.readTree(resolvedStepData));

    // -- Assert --
    assertFalse(resolvedJson.has("inject_id"));
    assertFalse(resolvedJson.has("inject_status"));
    assertFalse(resolvedJson.has("inject_depends_on"));
    assertFalse(resolvedJson.has("inject_exercise"));
    assertFalse(resolvedJson.has("inject_scenario"));
    assertEquals(
        contractId,
        resolvedJson.get("inject_injector_contract").get("injector_contract_id").asText());
  }

  // -- UTILS --

  private static Specification<Exercise> exerciseByName(@NotNull final String name) {
    return (root, query, cb) -> cb.equal(root.get("name"), name);
  }
}
