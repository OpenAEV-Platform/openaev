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
import io.openaev.rest.payload.form.PayloadCreateInput;
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
  void
      given_payloadWithoutDomainsAndForeignContractDomainIds_when_buildingPayloadInput_should_resolveDomainsFromConvertedContent() {
    // -- Arrange --
    Domain networkDomain =
        domainRepository
            .findByName("Network")
            .orElseGet(
                () -> {
                  Domain domain = new Domain();
                  domain.setName("Network");
                  domain.setColor("#009933");
                  domain.setTenant(new Tenant(TenantContext.getCurrentTenant()));
                  return domainRepository.save(domain);
                });
    Domain toClassify =
        domainRepository.findByName(PresetDomain.getToClassify().getName()).orElseThrow();

    String foreignDomainId = UUID.randomUUID().toString();
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode payloadNode = objectMapper.createObjectNode();
    payloadNode.put("payload_type", "Command");
    payloadNode.put("payload_name", "netstat");
    payloadNode.put("payload_source", "MANUAL");
    payloadNode.put("payload_status", "VERIFIED");
    payloadNode.set("payload_platforms", objectMapper.createArrayNode().add("Windows"));
    payloadNode.set(
        "payload_expectations", objectMapper.createArrayNode().add("DETECTION").add("PREVENTION"));
    payloadNode.set("payload_arguments", objectMapper.createArrayNode());
    payloadNode.set("payload_prerequisites", objectMapper.createArrayNode());
    payloadNode.set("payload_output_parsers", objectMapper.createArrayNode());

    ObjectNode injectorContractNode = objectMapper.createObjectNode();
    injectorContractNode.set(
        "injector_contract_domains", objectMapper.createArrayNode().add(foreignDomainId));
    ObjectNode convertedContent = objectMapper.createObjectNode();
    convertedContent.set(
        "domains",
        objectMapper
            .createArrayNode()
            .add(
                objectMapper
                    .createObjectNode()
                    .put("domain_id", foreignDomainId)
                    .put("domain_name", "Network")
                    .put("domain_color", "#009933")));
    injectorContractNode.set("convertedContent", convertedContent);

    // -- Act --
    PayloadCreateInput payloadCreateInput =
        ReflectionTestUtils.invokeMethod(
            importer,
            "buildPayloadCreateInput",
            new HashMap<>(),
            payloadNode,
            injectorContractNode);

    // -- Assert --
    assertNotNull(payloadCreateInput);
    assertTrue(payloadCreateInput.getDomainIds().contains(networkDomain.getId()));
    assertFalse(payloadCreateInput.getDomainIds().contains(toClassify.getId()));
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
    String resolvedStepData =
        ReflectionTestUtils.invokeMethod(
            importer, "resolveStepData", stepNode, resolvedContracts, new HashMap<>(), workflow);
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
    String resolvedStepData =
        ReflectionTestUtils.invokeMethod(
            importer, "resolveStepData", stepNode, resolvedContracts, new HashMap<>(), workflow);
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
      given_stepDataWithTextualContractTags_when_resolvingMappedContract_should_notThrowAndKeepContractId() {
    // -- Arrange --
    String oldContractId = UUID.randomUUID().toString();
    String newContractId = UUID.randomUUID().toString();
    String sourceTagId = UUID.randomUUID().toString();
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.put(
        "step_data",
        """
        {
          "inject_injector_contract": {
            "injector_contract_id": "%s",
            "injector_contract_tags": ["%s"]
          }
        }
        """
            .formatted(oldContractId, sourceTagId));
    Map<String, String> resolvedContracts = Map.of(oldContractId, newContractId);
    Exercise simulation = new Exercise();
    simulation.setId("sim-test");
    Workflow workflow = Workflow.builder().simulation(simulation).build();

    // -- Act --
    String resolvedStepData =
        assertDoesNotThrow(
            () ->
                ReflectionTestUtils.invokeMethod(
                    importer,
                    "resolveStepData",
                    stepNode,
                    resolvedContracts,
                    new HashMap<>(),
                    workflow));
    JsonNode resolvedJson = assertDoesNotThrow(() -> objectMapper.readTree(resolvedStepData));

    // -- Assert --
    assertTrue(resolvedJson.get("inject_injector_contract").isObject());
    assertEquals(
        newContractId,
        resolvedJson.get("inject_injector_contract").get("injector_contract_id").asText());
    assertFalse(resolvedJson.get("inject_injector_contract").has("injector_contract_tags"));
  }

  @Test
  @Transactional
  void
      given_stepDataWithPayloadOutputParsers_when_resolvingMappedContract_should_preservePayloadOutputParsersForChainingLogic() {
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
            "injector_contract_payload": {
              "payload_output_parsers": [
                {
                  "output_parser_contract_output_elements": [
                    {"contract_output_element_type":"portscan"}
                  ]
                }
              ]
            }
          }
        }
        """
            .formatted(oldContractId));
    Map<String, String> resolvedContracts = Map.of(oldContractId, newContractId);
    Exercise simulation = new Exercise();
    simulation.setId("sim-test");
    Workflow workflow = Workflow.builder().simulation(simulation).build();

    // -- Act --
    String resolvedStepData =
        ReflectionTestUtils.invokeMethod(
            importer, "resolveStepData", stepNode, resolvedContracts, new HashMap<>(), workflow);
    JsonNode resolvedJson = assertDoesNotThrow(() -> objectMapper.readTree(resolvedStepData));

    // -- Assert --
    assertEquals(
        newContractId,
        resolvedJson.get("inject_injector_contract").get("injector_contract_id").asText());
    assertFalse(resolvedJson.get("inject_injector_contract").has("injector_contract_providing"));
    assertEquals(
        "portscan",
        resolvedJson
            .get("inject_injector_contract")
            .get("injector_contract_payload")
            .get("payload_output_parsers")
            .get(0)
            .get("output_parser_contract_output_elements")
            .get(0)
            .get("contract_output_element_type")
            .asText());
  }

  @Test
  @Transactional
  void
      given_stepDataWithForeignDomainIdsAndConvertedContent_when_resolvingMappedContract_should_resolveDomainByName() {
    // -- Arrange --
    Domain savedDomain =
        domainRepository
            .findByName("Network")
            .orElseGet(
                () -> {
                  Domain networkDomain = new Domain();
                  networkDomain.setName("Network");
                  networkDomain.setColor("#009933");
                  networkDomain.setTenant(new Tenant(TenantContext.getCurrentTenant()));
                  return domainRepository.save(networkDomain);
                });

    String oldContractId = UUID.randomUUID().toString();
    String newContractId = UUID.randomUUID().toString();
    String foreignDomainId = UUID.randomUUID().toString();
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode stepNode = objectMapper.createObjectNode();
    stepNode.put(
        "step_data",
        """
        {
          "inject_injector_contract": {
            "injector_contract_id": "%s",
            "injector_contract_domains": ["%s"],
            "convertedContent": {
              "domains": [
                {"domain_id":"%s","domain_name":"Network","domain_color":"#009933"}
              ]
            }
          }
        }
        """
            .formatted(oldContractId, foreignDomainId, foreignDomainId));
    Map<String, String> resolvedContracts = Map.of(oldContractId, newContractId);
    Exercise simulation = new Exercise();
    simulation.setId("sim-test");
    Workflow workflow = Workflow.builder().simulation(simulation).build();

    // -- Act --
    String resolvedStepData =
        ReflectionTestUtils.invokeMethod(
            importer, "resolveStepData", stepNode, resolvedContracts, new HashMap<>(), workflow);
    JsonNode resolvedJson = assertDoesNotThrow(() -> objectMapper.readTree(resolvedStepData));

    // -- Assert --
    JsonNode domains =
        resolvedJson.get("inject_injector_contract").get("injector_contract_domains");
    assertTrue(domains.isArray());
    assertEquals(1, domains.size());
    assertEquals(savedDomain.getId(), domains.get(0).asText());
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
    String resolvedStepData =
        ReflectionTestUtils.invokeMethod(
            importer,
            "resolveStepData",
            stepNode,
            new HashMap<String, String>(),
            new HashMap<>(),
            workflow);
    JsonNode resolvedJson = assertDoesNotThrow(() -> objectMapper.readTree(resolvedStepData));

    // -- Assert --
    assertEquals("old-inject-id", resolvedJson.get("inject_id").asText());
    assertEquals("old-status-id", resolvedJson.get("inject_status").asText());
    assertEquals("old-parent-id", resolvedJson.get("inject_depends_on").get(0).asText());
    assertEquals("sim-test", resolvedJson.get("inject_exercise").asText());
    assertTrue(resolvedJson.get("inject_scenario").isNull());
    assertFalse(resolvedJson.has("inject_assets"));
    assertFalse(resolvedJson.has("inject_asset_groups"));
    assertEquals(
        contractId,
        resolvedJson.get("inject_injector_contract").get("injector_contract_id").asText());
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

  // -- UTILS --

  private static Specification<Exercise> exerciseByName(@NotNull final String name) {
    return (root, query, cb) -> cb.equal(root.get("name"), name);
  }
}
