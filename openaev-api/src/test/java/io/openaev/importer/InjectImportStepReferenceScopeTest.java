package io.openaev.importer;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.service.ImportService.EXPORT_ENTRY_EXERCISE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * A chaining step imported into a simulation carries references the bundle does not resolve by
 * itself: a document id in its {@code inject_documents} that the bundle does not ship (a re-import
 * on the same instance), or the security platform of a detection remediation of its embedded
 * payload, by id or by collector type. The importer resolves them against the database. Those ids
 * and names come from the import file, so the lookups must be confined to the tenant the import
 * writes into, the parent's tenant: the injects import endpoints pass the caller's whole scope, and
 * a caller who is a member of several tenants and sends no selector would otherwise bind a row of
 * one of its other tenants into the imported step.
 *
 * <p>The stored rows are read back with raw JDBC on the test connection, so the statement inspector
 * never rewrites the ground-truth read. {@code domains} is armed next to {@code documents} and
 * {@code assets} because the import resolves the preset domain by name and relies on the scope to
 * get a single row.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=documents,assets,domains")
@WithMockUser(isAdmin = true)
@DisplayName("The injects import resolves unbundled step references in the tenant it writes into")
class InjectImportStepReferenceScopeTest extends IntegrationTest {

  private static final String MISSING_CONTRACT_WITH_PAYLOAD_FIXTURE =
      "src/test/resources/importer-v1/import-scenario-workflow-step-missing-contract-with-payload.json";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private final ObjectMapper mapper = new ObjectMapper();
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoMemberTenants() throws Exception {
    // The caller is a member of both tenants and sends no selector, so the request scope holds
    // both while the parent simulation, hence the write tenant, is in A.
    tenantA = tenantHelper.createTenantWithCurrentUser("inj-step-ref-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("inj-step-ref-b").getId();
    // Detection remediations are dropped from a payload create without an enterprise licence.
    Mockito.when(enterpriseEditionService.isEnterpriseLicenseInactive(Mockito.any()))
        .thenReturn(false);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Step documents, caller in two tenants, no selector")
  class StepDocuments {

    @Test
    @DisplayName("given_stepDocumentOfAnotherMemberTenant_should_dropTheLink")
    void given_stepDocumentOfAnotherMemberTenant_should_dropTheLink() throws Exception {
      // Arrange
      String parentId = saveExercise(tenantA);
      String foreignDocumentId = saveDocument(tenantB);
      String contractId = persistStepContract(tenantA);
      String exerciseName = uniqueName();
      byte[] zip =
          exerciseWorkflowZip(exerciseName, stepWithDocument(contractId, foreignDocumentId));

      // Act
      importZip(parentId, zip).andExpect(status().isOk());

      // Assert
      assertThat(storedStepDocumentIds(exerciseName))
          .as("a document of another tenant of the caller must not be bound into the step")
          .doesNotContain(foreignDocumentId)
          .isEmpty();
    }

    @Test
    @DisplayName("given_stepDocumentOfTheWriteTenant_should_keepTheLink")
    void given_stepDocumentOfTheWriteTenant_should_keepTheLink() throws Exception {
      // Arrange
      String parentId = saveExercise(tenantA);
      String ownDocumentId = saveDocument(tenantA);
      String contractId = persistStepContract(tenantA);
      String exerciseName = uniqueName();
      byte[] zip = exerciseWorkflowZip(exerciseName, stepWithDocument(contractId, ownDocumentId));

      // Act
      importZip(parentId, zip).andExpect(status().isOk());

      // Assert
      // The fallback still resolves inside the write tenant, so a same-instance re-import keeps
      // its attachment.
      assertThat(storedStepDocumentIds(exerciseName)).containsExactly(ownDocumentId);
    }
  }

  @Nested
  @DisplayName(
      "Security platform of a step payload remediation, caller in two tenants, no selector")
  class StepPayloadSecurityPlatform {

    @BeforeEach
    void registerPayloadCapableInjector() throws Exception {
      // The step's contract is absent and its embedded payload is recreated on import, which the
      // importer only attempts when an injector of the contract's type exists in the write tenant.
      openaevInjectorIntegrationFactory.registerConnectorForTenant(tenantA);
    }

    @Test
    @DisplayName("given_platformOfAnotherMemberTenantById_should_notBindIt")
    void given_platformOfAnotherMemberTenantById_should_notBindIt() throws Exception {
      // Arrange
      String parentId = saveExercise(tenantA);
      String foreignPlatformId = savePlatform(tenantB, "inj-step-ref-" + UUID.randomUUID());
      String payloadName = uniqueName();
      byte[] zip =
          exerciseWorkflowZip(
              uniqueName(), stepWithPayloadRemediation(payloadName, foreignPlatformId, ""));

      // Act
      importZip(parentId, zip).andExpect(status().isOk());

      // Assert
      // The id resolves to nothing in the write tenant and no collector type names a fallback, so
      // the remediation is skipped rather than bound to the other tenant's platform.
      assertThat(boundRemediationPlatforms(payloadName))
          .as("a platform of another tenant of the caller must not be bound by id")
          .isEmpty();
    }

    @Test
    @DisplayName("given_platformOfTheWriteTenantById_should_bindIt")
    void given_platformOfTheWriteTenantById_should_bindIt() throws Exception {
      // Arrange
      String parentId = saveExercise(tenantA);
      String ownPlatformId = savePlatform(tenantA, "inj-step-ref-" + UUID.randomUUID());
      String payloadName = uniqueName();
      byte[] zip =
          exerciseWorkflowZip(
              uniqueName(), stepWithPayloadRemediation(payloadName, ownPlatformId, ""));

      // Act
      importZip(parentId, zip).andExpect(status().isOk());

      // Assert
      assertThat(boundRemediationPlatforms(payloadName))
          .containsExactly(tenantA + "|" + ownPlatformId);
    }

    @Test
    @DisplayName(
        "given_platformOfAnotherMemberTenantByCollectorType_should_createOneInTheWriteTenant")
    void given_platformOfAnotherMemberTenantByCollectorType_should_createOneInTheWriteTenant()
        throws Exception {
      // Arrange
      // The collector type humanizes to the platform name, so a same-name platform of the other
      // tenant would match a lookup that is not confined to the write tenant.
      String suffix = String.valueOf(System.nanoTime());
      String foreignPlatformId = savePlatform(tenantB, "Acme Sensor " + suffix);
      String parentId = saveExercise(tenantA);
      String payloadName = uniqueName();
      byte[] zip =
          exerciseWorkflowZip(
              uniqueName(),
              stepWithPayloadRemediation(payloadName, "", "openaev_acme_sensor_" + suffix));

      // Act
      importZip(parentId, zip).andExpect(status().isOk());

      // Assert
      List<String> bound = boundRemediationPlatforms(payloadName);
      assertThat(bound)
          .as("the remediation is bound to a platform created in the write tenant")
          .hasSize(1);
      assertThat(bound.getFirst())
          .startsWith(tenantA + "|")
          .doesNotEndWith("|" + foreignPlatformId);
    }
  }

  // -- Helpers --

  private ResultActions importZip(String simulationId, byte[] zip) throws Exception {
    return mvc.perform(
        multipart(EXERCISE_URI + "/{simulationId}/injects/import", simulationId)
            .file(new MockMultipartFile("file", zip))
            .with(csrf()));
  }

  /** A step of an existing contract referencing {@code documentId} with no bundled file. */
  private Consumer<ObjectNode> stepWithDocument(String contractId, String documentId) {
    return stepData -> {
      ObjectNode contract = mapper.createObjectNode();
      contract.put("injector_contract_id", contractId);
      stepData.set("inject_injector_contract", contract);
      // The link-object shape the exporter writes into step data.
      ObjectNode link = mapper.createObjectNode();
      link.put("document_id", documentId);
      link.put("document_attached", true);
      stepData.set("inject_documents", mapper.createArrayNode().add(link));
    };
  }

  /**
   * A step of a contract absent from this instance, carrying its recreatable payload whose one
   * detection remediation names a platform by id, by collector type, or both.
   */
  private Consumer<ObjectNode> stepWithPayloadRemediation(
      String payloadName, String platformId, String collectorType) {
    return stepData -> {
      ObjectNode contract;
      try {
        contract =
            (ObjectNode)
                mapper
                    .readTree(Files.readAllBytes(Paths.get(MISSING_CONTRACT_WITH_PAYLOAD_FIXTURE)))
                    .at("/scenario_workflow/workflow_steps/0/step_data/inject_injector_contract")
                    .deepCopy();
      } catch (java.io.IOException e) {
        throw new IllegalStateException(e);
      }
      contract.put("injector_contract_id", UUID.randomUUID().toString());
      ObjectNode payload = (ObjectNode) contract.get("injector_contract_payload");
      payload.put("payload_id", UUID.randomUUID().toString());
      payload.put("payload_name", payloadName);
      payload.put("payload_external_id", payloadName);
      ObjectNode remediation = mapper.createObjectNode();
      remediation.put("detection_remediation_values", "detection rule");
      remediation.put("detection_remediation_security_platform", platformId);
      remediation.put("detection_remediation_collector_type", collectorType);
      payload.set("payload_detection_remediations", mapper.createArrayNode().add(remediation));
      stepData.set("inject_injector_contract", contract);
    };
  }

  /** A minimal simulation export whose workflow carries one inject step, shaped by {@code step}. */
  private byte[] exerciseWorkflowZip(String exerciseName, Consumer<ObjectNode> step)
      throws Exception {
    ObjectNode root = mapper.createObjectNode();
    root.put("export_version", 1);
    ObjectNode exerciseInfo = mapper.createObjectNode();
    exerciseInfo.put("exercise_id", UUID.randomUUID().toString());
    exerciseInfo.put("exercise_name", exerciseName);
    exerciseInfo.put("exercise_description", "");
    exerciseInfo.put("exercise_subtitle", "");
    exerciseInfo.put("exercise_message_header", "");
    exerciseInfo.put("exercise_message_footer", "");
    exerciseInfo.put("exercise_mail_from", "noreply@openaev.io");
    exerciseInfo.set("exercise_tags", mapper.createArrayNode());
    root.set("exercise_information", exerciseInfo);
    root.set("exercise_tags", mapper.createArrayNode());
    root.set("exercise_documents", mapper.createArrayNode());
    root.set("exercise_teams", mapper.createArrayNode());
    root.set("exercise_users", mapper.createArrayNode());
    root.set("exercise_organizations", mapper.createArrayNode());
    root.set("exercise_injects", mapper.createArrayNode());

    ObjectNode stepData = mapper.createObjectNode();
    stepData.put("inject_id", UUID.randomUUID().toString());
    stepData.put("inject_title", "step with an unbundled reference");
    step.accept(stepData);

    ObjectNode stepNode = mapper.createObjectNode();
    stepNode.put("step_id", "step-ref-0001");
    stepNode.put("step_action_class", "INJECT_EXECUTION");
    stepNode.put("step_limit_execution", 1);
    stepNode.set("step_data", stepData);
    stepNode.set("step_conditions", mapper.createArrayNode());

    ObjectNode workflow = mapper.createObjectNode();
    workflow.put("workflow_version", 2);
    workflow.put("workflow_rate_limit_enabled", false);
    workflow.put("workflow_timeout_enabled", false);
    workflow.put("workflow_safe_mode_enabled", false);
    workflow.set("workflow_scope_rules", mapper.createArrayNode());
    workflow.set("workflow_scope_variables", mapper.createArrayNode());
    workflow.set("workflow_steps", mapper.createArrayNode().add(stepNode));
    root.set("exercise_workflow", workflow);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      ZipEntry entry = new ZipEntry("exercise.json");
      entry.setComment(EXPORT_ENTRY_EXERCISE);
      zip.putNextEntry(entry);
      zip.write(mapper.writeValueAsBytes(root));
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  private String saveExercise(String tenantId) {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(tenantId));
    String id = exerciseRepository.save(exercise).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  /** Flushed out of the persistence context, so the importer's lookup issues real SQL. */
  private String saveDocument(String tenantId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setTarget("inj-step-ref-" + UUID.randomUUID() + ".jpg");
    document.setTenant(new Tenant(tenantId));
    String id = documentRepository.save(document).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  private String savePlatform(String tenantId, String name) {
    SecurityPlatform platform = SecurityPlatformFixture.createDefault(name, "EDR");
    platform.setTenant(new Tenant(tenantId));
    String id = securityPlatformRepository.save(platform).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  /** The step's contract must exist in the write tenant, or the importer skips the step. */
  private String persistStepContract(String tenantId) {
    InjectorContract contract = new InjectorContract();
    contract.setId(UUID.randomUUID().toString());
    contract.setTenant(new Tenant(tenantId));
    contract.setContent("{}");
    contract.setLabels(Map.of("en", "resolvable step contract"));
    contract.setCustom(false);
    contract.setManual(false);
    contract.setNeedsExecutor(false);
    contract.setPlatforms(new Endpoint.PLATFORM_TYPE[0]);
    String id = injectorContractRepository.save(contract).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  private static String uniqueName() {
    return "inject-import-step-ref-" + UUID.randomUUID();
  }

  /** The document ids persisted in the imported step data, in either serialized shape. */
  private List<String> storedStepDocumentIds(String exerciseName) throws Exception {
    List<String> stepData =
        column(
            "SELECT s.step_data::text FROM steps s"
                + " JOIN workflows w ON s.step_workflow_id = w.workflow_id"
                + " JOIN exercises e ON w.workflow_simulation_id = e.exercise_id"
                + " WHERE e.exercise_name = ? AND s.step_template_id IS NULL",
            exerciseName + Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    assertThat(stepData).as("exactly one step is imported").hasSize(1);
    List<String> ids = new ArrayList<>();
    JsonNode documents = mapper.readTree(stepData.getFirst()).get("inject_documents");
    if (documents instanceof ArrayNode links) {
      for (JsonNode link : links) {
        ids.add(link.isTextual() ? link.asText() : link.get("document_id").asText());
      }
    }
    return ids;
  }

  /**
   * {@code tenant|id} of every platform bound to the imported payload's remediations. The payload
   * is found by name: the import clears the external id of the payloads it recreates.
   */
  private List<String> boundRemediationPlatforms(String payloadName) {
    return column(
        "SELECT a.tenant_id || '|' || a.asset_id FROM detection_remediations dr"
            + " JOIN payloads p ON dr.detection_remediation_payload_id = p.payload_id"
            + " JOIN assets a ON dr.detection_remediation_security_platform = a.asset_id"
            + " WHERE p.payload_name = ?",
        payloadName);
  }

  /** Raw JDBC on the test connection: the statement inspector never rewrites it. */
  private List<String> column(String sql, String... parameters) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> values = new ArrayList<>();
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                  while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                  }
                }
              }
              return values;
            });
  }
}
