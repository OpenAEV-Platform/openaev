package io.openaev.importer;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Base;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.inject.service.InjectExportService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every injector contract the importer writes takes the import's write tenant, and every lookup it
 * makes to find one resolves in that tenant. {@code injectors_contracts} and {@code payloads} are
 * both tenant-active, so a lookup that names no tenant answers for every tenant of the request's
 * scope: a payload external id held by two tenants of the caller matches both rows, and one held
 * only by another tenant of the caller resolves that tenant's contract for this tenant's inject.
 *
 * <p>The row tenant is read with raw JDBC on the test connection, which the statement inspector
 * never rewrites.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("The importer attributes and resolves injector contracts in the write tenant")
class V1_DataImporterContractTenantTest extends IntegrationTest {

  private static final String STARTER_PACK_CONTRACT_ID = "93d27459-68d0-43b1-ad65-eacc3cfa5cf7";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private V1_DataImporter importer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectExportService injectExportService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorFixture injectorFixture;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void setUp() {
    exerciseComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    Mockito.when(enterpriseEditionService.isEnterpriseLicenseInactive(Mockito.any()))
        .thenReturn(false);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Starter pack contract")
  class StarterPackContract {

    @Test
    @DisplayName("given_nonDefaultTenantAndNoAmbientTenant_should_stampTheContractInThatTenant")
    void given_nonDefaultTenantAndNoAmbientTenant_should_stampTheContractInThatTenant()
        throws Exception {
      // Arrange: the onboarding shape, a scope on tenant B and nothing on the thread.
      String tenantB = tenantHelper.createTenantWithCurrentUser("importer-starter-b").getId();
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
      JsonNode bundle =
          new ObjectMapper()
              .readTree(
                  Files.readAllBytes(
                      Paths.get(
                          "src/test/resources/importer-v1/scenario_with_injects_from_injector.json")));

      // Act
      importer.importData(
          TxCtx.forTenant(tenantB),
          bundle,
          Map.of(),
          null,
          null,
          null,
          null,
          Constants.IMPORTED_OBJECT_NAME_SUFFIX);

      // Assert
      assertThat(contractTenants(STARTER_PACK_CONTRACT_ID)).containsExactly(tenantB);
    }

    @Test
    @DisplayName("given_noAmbientTenant_should_stampTheContractInTheTenantOfTheScope")
    void given_noAmbientTenant_should_stampTheContractInTheTenantOfTheScope() throws Exception {
      // Arrange: the creation step on its own, with nothing on the thread to fall back on.
      String tenantB = tenantHelper.createTenantWithCurrentUser("importer-stamp-b").getId();
      DefaultTenantScopeTestListener.setAmbientScope(entityManager, tenantB);
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
      JsonNode contractNode =
          new ObjectMapper()
              .readTree(
                  Files.readAllBytes(
                      Paths.get(
                          "src/test/resources/importer-v1/scenario_with_injects_from_injector.json")))
              .get("scenario_injects")
              .get(0)
              .get("inject_injector_contract");

      // Act
      ReflectionTestUtils.invokeMethod(
          importer,
          "importInjectorContractFromStarterPack",
          TxCtx.forTenant(tenantB),
          contractNode,
          null,
          new HashMap<String, Base>());

      // Assert
      assertThat(contractTenants(contractNode.get("injector_contract_id").textValue()))
          .containsExactly(tenantB);
    }
  }

  @Nested
  @DisplayName("Contract found through a payload external id")
  class PayloadExternalId {

    @Test
    @DisplayName("given_externalIdInTwoTenantsOfTheCaller_should_resolveTheWriteTenantContract")
    void given_externalIdInTwoTenantsOfTheCaller_should_resolveTheWriteTenantContract()
        throws Exception {
      // Arrange: the caller belongs to the default tenant and to B, the simulation is B's, and both
      // tenants hold a payload with the bundle's external id, each behind its own contract.
      tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
      String tenantB = tenantHelper.createTenantWithCurrentUser("importer-external-b").getId();
      TenantContext.clearCurrentTenant();
      String externalId = "external-" + UUID.randomUUID();
      Inject source = composeSourceInject(externalId);
      byte[] zip = exportedZip(source);
      TenantContext.setCurrentTenant(tenantB);
      String contractInB = composeContract(externalId);
      TenantContext.clearCurrentTenant();
      String exerciseId = saveExercise(tenantB);

      // Act
      mvc.perform(
              multipart(EXERCISE_URI + "/{simulationId}/injects/import", exerciseId)
                  .file(new MockMultipartFile("file", zip))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertThat(
              column(
                  "SELECT inject_injector_contract FROM injects WHERE inject_title = ? AND"
                      + " inject_id <> ? AND tenant_id = ?",
                  source.getTitle(),
                  source.getId(),
                  tenantB))
          .containsExactly(contractInB);
      assertThat(
              column(
                  "SELECT payload_id FROM payloads WHERE payload_external_id = ? AND tenant_id ="
                      + " ?",
                  externalId,
                  tenantB))
          .hasSize(1);
    }

    @Test
    @DisplayName("given_externalIdOnlyInAnotherTenantOfTheCaller_should_notResolveThatContract")
    void given_externalIdOnlyInAnotherTenantOfTheCaller_should_notResolveThatContract()
        throws Exception {
      // Arrange: only the default tenant holds the payload; the simulation is B's.
      tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
      String tenantB = tenantHelper.createTenantWithCurrentUser("importer-external-only-b").getId();
      TenantContext.clearCurrentTenant();
      String externalId = "external-" + UUID.randomUUID();
      Inject source = composeSourceInject(externalId);
      byte[] zip = exportedZip(source);
      String exerciseId = saveExercise(tenantB);

      // Act
      mvc.perform(
              multipart(EXERCISE_URI + "/{simulationId}/injects/import", exerciseId)
                  .file(new MockMultipartFile("file", zip))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert: B's inject points at a contract of B, not at the default tenant's one it would
      // have shared with the payload external id.
      assertThat(
              column(
                  "SELECT ic.tenant_id FROM injects i JOIN injectors_contracts ic ON"
                      + " ic.injector_contract_id = i.inject_injector_contract AND ic.tenant_id ="
                      + " i.tenant_id WHERE i.inject_title = ? AND i.inject_id <> ?",
                  source.getTitle(),
                  source.getId()))
          .containsExactly(tenantB);
      assertThat(
              column(
                  "SELECT inject_injector_contract FROM injects WHERE inject_title = ? AND"
                      + " inject_id <> ?",
                  source.getTitle(),
                  source.getId()))
          .doesNotContain(source.getInjectorContract().orElseThrow().getId());
    }
  }

  @Nested
  @DisplayName("Chaining step resolvability")
  class ChainingStepResolvability {

    @Test
    @DisplayName("given_contractOnlyInTheWriteTenantAndAnotherAmbientTenant_should_keepTheStep")
    void given_contractOnlyInTheWriteTenantAndAnotherAmbientTenant_should_keepTheStep()
        throws Exception {
      // Arrange: the import writes into B, whose contract the step references by id, while the
      // ambient tenant of the thread is the default one (the non-prefixed route).
      String tenantB = tenantHelper.createTenantWithCurrentUser("importer-step-b").getId();
      TenantContext.setCurrentTenant(tenantB);
      String contractInB = composeContract("external-" + UUID.randomUUID());
      TenantContext.clearCurrentTenant();
      DefaultTenantScopeTestListener.setAmbientScope(entityManager, tenantB);
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode stepData = mapper.createObjectNode();
      stepData.put("inject_title", "step-" + UUID.randomUUID());
      stepData.put("inject_injector_contract", contractInB);
      ObjectNode stepNode = mapper.createObjectNode();
      stepNode.put("step_data", mapper.writeValueAsString(stepData));

      // Act
      Optional<?> skipped =
          ReflectionTestUtils.invokeMethod(
              importer,
              "evaluateChainingStepResolvability",
              TxCtx.forTenant(tenantB),
              stepNode,
              new HashMap<String, String>());

      // Assert: the contract is looked up in B, so the step is not reported as a missing action.
      assertThat(skipped).isEmpty();
    }
  }

  // -- Helpers --

  /** One inject with its contract, injector and payload, composed in the ambient tenant. */
  private Inject composeSourceInject(String payloadExternalId) throws Exception {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setExternalId(payloadExternalId);
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTitle("importer-contract-" + UUID.randomUUID());
    Exercise exercise =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInject(
                injectComposer
                    .forInject(inject)
                    .withInjectorContract(
                        injectorContractComposer
                            .forInjectorContract(
                                InjectorContractFixture.createDefaultInjectorContract())
                            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                            .withPayload(payloadComposer.forPayload(payload))))
            .persist()
            .get();
    return exercise.getInjects().getFirst();
  }

  /** A contract of the ambient tenant, behind a payload with the given external id. */
  private String composeContract(String payloadExternalId) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setExternalId(payloadExternalId);
    String id =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withPayload(payloadComposer.forPayload(payload))
            .persist()
            .get()
            .getId();
    entityManager.flush();
    return id;
  }

  private byte[] exportedZip(Inject source) throws Exception {
    byte[] zip =
        injectExportService.exportInjectsToZip(
            List.of(source), ExportOptions.mask(false, false, false));
    entityManager.flush();
    entityManager.clear();
    return zip;
  }

  private String saveExercise(String tenantId) {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(tenantId));
    String id = exerciseRepository.save(exercise).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  private List<String> contractTenants(String contractId) {
    return column(
        "SELECT tenant_id FROM injectors_contracts WHERE injector_contract_id = ?", contractId);
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
