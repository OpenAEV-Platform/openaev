package io.openaev.importer;

import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.TENANT_ATOMIC_TESTING_URI;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.inject.service.InjectExportService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inject and scenario import endpoints resolve one write tenant for the whole bundle. A bundle
 * imported into an existing simulation or scenario is written in the tenant of that parent, which
 * must lie inside the caller's scope. A bundle with no parent (atomic testings, a scenario import)
 * takes the tenant of the request scope, resolved from the caller when no selector is present
 * (issues #6331 / #6332): a single-tenant caller lands in its own tenant, a multi-tenant caller
 * with access to the default tenant falls back to it, and a multi-tenant caller without it is
 * refused with 400.
 *
 * <p>The row tenant is read with raw JDBC on the test connection so the statement inspector never
 * rewrites the ground-truth read. {@code domains} is armed next to {@code documents} because the
 * import resolves the preset domain by name and relies on the scope to get a single row.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=documents,domains")
@WithMockUser(isAdmin = true)
@DisplayName("Inject and scenario imports write in the tenant of the caller or of the parent")
class InjectImportWriteScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectExportService injectExportService;
  @Autowired private ScenarioService scenarioService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorFixture injectorFixture;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String sourceInjectId;
  private String sourceInjectTitle;

  @BeforeEach
  void resetComposers() {
    exerciseComposer.reset();
    scenarioComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
    Mockito.when(enterpriseEditionService.isEnterpriseLicenseInactive(Mockito.any()))
        .thenReturn(false);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Atomic testing import, no parent")
  class AtomicTestingImport {

    @Test
    @DisplayName("given_singleTenantCaller_should_writeInjectInThatTenant")
    void given_singleTenantCaller_should_writeInjectInThatTenant() throws Exception {
      // Arrange
      String tenant = tenantHelper.createTenantWithCurrentUser("inj-import-single").getId();
      byte[] zip = injectZip();

      // Act
      importZip(multipart(ATOMIC_TESTING_URI + "/import"), zip).andExpect(status().isOk());

      // Assert
      assertThat(importedInjectTenants()).containsExactly(tenant);
    }

    @Test
    @DisplayName("given_multiTenantCallerWithDefault_should_writeInjectInDefaultTenant")
    void given_multiTenantCallerWithDefault_should_writeInjectInDefaultTenant() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      tenantHelper.createTenantWithCurrentUser("inj-import-multi-default");
      byte[] zip = injectZip();

      // Act
      importZip(multipart(ATOMIC_TESTING_URI + "/import"), zip).andExpect(status().isOk());

      // Assert
      assertThat(importedInjectTenants()).containsExactly(DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_multiTenantCallerWithoutDefault_should_return400")
    void given_multiTenantCallerWithoutDefault_should_return400() throws Exception {
      // Arrange
      tenantHelper.createTenantWithCurrentUser("inj-import-multi-a");
      tenantHelper.createTenantWithCurrentUser("inj-import-multi-b");
      byte[] zip = injectZip();

      // Act & Assert
      importZip(multipart(ATOMIC_TESTING_URI + "/import"), zip).andExpect(status().isBadRequest());
      assertThat(importedInjectTenants()).isEmpty();
    }

    @Test
    @DisplayName("given_prefixedRoute_should_writeInjectInPathTenant")
    void given_prefixedRoute_should_writeInjectInPathTenant() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String tenantB = tenantHelper.createTenantWithCurrentUser("inj-import-path-b").getId();
      byte[] zip = injectZip();

      // Act
      importZip(multipart(TENANT_ATOMIC_TESTING_URI + "/import", tenantB), zip)
          .andExpect(status().isOk());

      // Assert
      assertThat(importedInjectTenants()).containsExactly(tenantB);
    }
  }

  @Nested
  @DisplayName("Scenario import, no parent")
  class ScenarioImport {

    @Test
    @DisplayName("given_singleTenantCaller_should_writeScenarioInThatTenant")
    void given_singleTenantCaller_should_writeScenarioInThatTenant() throws Exception {
      // Arrange
      String tenant = tenantHelper.createTenantWithCurrentUser("scn-import-single").getId();
      String name = uniqueName();
      byte[] zip = scenarioZip(name);

      // Act
      importZip(multipart(SCENARIO_URI + "/import"), zip).andExpect(status().isOk());

      // Assert
      assertThat(importedScenarioTenants(name)).containsExactly(tenant);
    }

    @Test
    @DisplayName("given_multiTenantCallerWithDefault_should_writeScenarioInDefaultTenant")
    void given_multiTenantCallerWithDefault_should_writeScenarioInDefaultTenant() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      tenantHelper.createTenantWithCurrentUser("scn-import-multi-default");
      String name = uniqueName();
      byte[] zip = scenarioZip(name);

      // Act
      importZip(multipart(SCENARIO_URI + "/import"), zip).andExpect(status().isOk());

      // Assert
      assertThat(importedScenarioTenants(name)).containsExactly(DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_multiTenantCallerWithoutDefault_should_return400")
    void given_multiTenantCallerWithoutDefault_should_return400() throws Exception {
      // Arrange
      tenantHelper.createTenantWithCurrentUser("scn-import-multi-a");
      tenantHelper.createTenantWithCurrentUser("scn-import-multi-b");
      String name = uniqueName();
      byte[] zip = scenarioZip(name);

      // Act & Assert
      importZip(multipart(SCENARIO_URI + "/import"), zip).andExpect(status().isBadRequest());
      assertThat(importedScenarioTenants(name)).isEmpty();
    }

    @Test
    @DisplayName("given_prefixedRoute_should_writeScenarioInPathTenant")
    void given_prefixedRoute_should_writeScenarioInPathTenant() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String tenantB = tenantHelper.createTenantWithCurrentUser("scn-import-path-b").getId();
      String name = uniqueName();
      byte[] zip = scenarioZip(name);

      // Act
      importZip(multipart(TENANT_SCENARIO_URI + "/import", tenantB), zip)
          .andExpect(status().isOk());

      // Assert
      assertThat(importedScenarioTenants(name)).containsExactly(tenantB);
    }
  }

  @Nested
  @DisplayName("Injects import into a simulation, the parent decides")
  class SimulationInjectsImport {

    @Test
    @DisplayName("given_multiTenantCallerAndParentInB_should_writeInjectsInB")
    void given_multiTenantCallerAndParentInB_should_writeInjectsInB() throws Exception {
      // Arrange: no selector, so the caller's scope holds both tenants; the parent picks B.
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String tenantB = tenantHelper.createTenantWithCurrentUser("sim-inj-b").getId();
      byte[] zip = injectZip();
      String exerciseId = saveExercise(tenantB);

      // Act
      importZip(multipart(EXERCISE_URI + "/{simulationId}/injects/import", exerciseId), zip)
          .andExpect(status().isOk());

      // Assert
      assertThat(importedInjectTenants()).containsExactly(tenantB);
    }

    @Test
    @DisplayName("given_multiTenantCallerAndParentInDefault_should_writeInjectsInDefault")
    void given_multiTenantCallerAndParentInDefault_should_writeInjectsInDefault() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      tenantHelper.createTenantWithCurrentUser("sim-inj-multi");
      byte[] zip = injectZip();
      String exerciseId = saveExercise(DEFAULT_TENANT);

      // Act
      importZip(multipart(EXERCISE_URI + "/{simulationId}/injects/import", exerciseId), zip)
          .andExpect(status().isOk());

      // Assert
      assertThat(importedInjectTenants()).containsExactly(DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_singleTenantCallerAndParentInItsTenant_should_writeInjectsThere")
    void given_singleTenantCallerAndParentInItsTenant_should_writeInjectsThere() throws Exception {
      // Arrange
      String tenant = tenantHelper.createTenantWithCurrentUser("sim-inj-single").getId();
      byte[] zip = injectZip();
      String exerciseId = saveExercise(tenant);

      // Act
      importZip(multipart(EXERCISE_URI + "/{simulationId}/injects/import", exerciseId), zip)
          .andExpect(status().isOk());

      // Assert
      assertThat(importedInjectTenants()).containsExactly(tenant);
    }

    @Test
    @DisplayName("given_parentOutsideCallerScope_should_return400AndWriteNothing")
    void given_parentOutsideCallerScope_should_return400AndWriteNothing() throws Exception {
      // Arrange: the caller is a member of one tenant only and the parent lives in another one.
      tenantHelper.createTenantWithCurrentUser("sim-inj-outside");
      byte[] zip = injectZip();
      String exerciseId = saveExercise(DEFAULT_TENANT);

      // Act & Assert
      importZip(multipart(EXERCISE_URI + "/{simulationId}/injects/import", exerciseId), zip)
          .andExpect(status().isBadRequest());
      assertThat(importedInjectTenants()).isEmpty();
    }

    @Test
    @DisplayName("given_prefixedRoute_should_writeInjectsInPathTenant")
    void given_prefixedRoute_should_writeInjectsInPathTenant() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String tenantB = tenantHelper.createTenantWithCurrentUser("sim-inj-path-b").getId();
      byte[] zip = injectZip();
      String exerciseId = saveExercise(tenantB);

      // Act
      importZip(
              multipart(
                  TENANT_EXERCISE_URI + "/{simulationId}/injects/import", tenantB, exerciseId),
              zip)
          .andExpect(status().isOk());

      // Assert
      assertThat(importedInjectTenants()).containsExactly(tenantB);
    }
  }

  @Nested
  @DisplayName("Injects import into a scenario, the parent decides")
  class ScenarioInjectsImport {

    @Test
    @DisplayName("given_multiTenantCallerAndParentInDefault_should_writeInjectsInDefault")
    void given_multiTenantCallerAndParentInDefault_should_writeInjectsInDefault() throws Exception {
      // Arrange: the scenario is looked up in the ambient tenant, the default one on this route.
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      tenantHelper.createTenantWithCurrentUser("scn-inj-multi");
      byte[] zip = injectZip();
      String scenarioId = saveScenario(DEFAULT_TENANT);

      // Act
      importZip(multipart(SCENARIO_URI + "/{scenarioId}/injects/import", scenarioId), zip)
          .andExpect(status().isOk());

      // Assert
      assertThat(importedInjectTenants()).containsExactly(DEFAULT_TENANT);
    }

    @Test
    @DisplayName("given_parentOutsideCallerScope_should_return400AndWriteNothing")
    void given_parentOutsideCallerScope_should_return400AndWriteNothing() throws Exception {
      // Arrange
      tenantHelper.createTenantWithCurrentUser("scn-inj-outside");
      byte[] zip = injectZip();
      String scenarioId = saveScenario(DEFAULT_TENANT);

      // Act & Assert
      importZip(multipart(SCENARIO_URI + "/{scenarioId}/injects/import", scenarioId), zip)
          .andExpect(status().isBadRequest());
      assertThat(importedInjectTenants()).isEmpty();
    }

    @Test
    @DisplayName("given_prefixedRoute_should_writeInjectsInPathTenant")
    void given_prefixedRoute_should_writeInjectsInPathTenant() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String tenantB = tenantHelper.createTenantWithCurrentUser("scn-inj-path-b").getId();
      byte[] zip = injectZip();
      String scenarioId = saveScenario(tenantB);

      // Act
      importZip(
              multipart(TENANT_SCENARIO_URI + "/{scenarioId}/injects/import", tenantB, scenarioId),
              zip)
          .andExpect(status().isOk());

      // Assert
      assertThat(importedInjectTenants()).containsExactly(tenantB);
    }
  }

  // -- Helpers --

  /** One inject with its contract, injector and payload, composed in the ambient tenant. */
  private InjectComposer.Composer composeInject() {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTitle(uniqueName());
    return injectComposer
        .forInject(inject)
        .withInjectorContract(
            injectorContractComposer
                .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
                .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand())));
  }

  private byte[] injectZip() throws Exception {
    InjectComposer.Composer wrapper = composeInject();
    Exercise exercise =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInject(wrapper)
            .persist()
            .get();
    Inject source = exercise.getInjects().getFirst();
    sourceInjectId = source.getId();
    sourceInjectTitle = source.getTitle();
    byte[] zip =
        injectExportService.exportInjectsToZip(
            List.of(source), ExportOptions.mask(false, false, false));
    entityManager.flush();
    entityManager.clear();
    return zip;
  }

  private byte[] scenarioZip(String name) throws Exception {
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setName(name);
    Scenario saved = scenarioComposer.forScenario(scenario).persist().get();
    MockHttpServletResponse response = new MockHttpServletResponse();
    scenarioService.exportScenario(saved.getId(), false, false, false, false, response);
    entityManager.flush();
    entityManager.clear();
    return response.getContentAsByteArray();
  }

  private ResultActions importZip(MockMultipartHttpServletRequestBuilder request, byte[] zip)
      throws Exception {
    return mvc.perform(request.file(new MockMultipartFile("file", zip)).with(csrf()));
  }

  private String saveExercise(String tenantId) {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(tenantId));
    String id = exerciseRepository.save(exercise).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  private String saveScenario(String tenantId) {
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTenant(new Tenant(tenantId));
    String id = scenarioRepository.save(scenario).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  private static String uniqueName() {
    return "inject-import-scope-" + UUID.randomUUID();
  }

  private List<String> importedInjectTenants() {
    return column(
        "SELECT tenant_id FROM injects WHERE inject_title = ? AND inject_id <> ?",
        sourceInjectTitle,
        sourceInjectId);
  }

  private List<String> importedScenarioTenants(String sourceName) {
    return column(
        "SELECT tenant_id FROM scenarios WHERE scenario_name = ?",
        sourceName + Constants.IMPORTED_OBJECT_NAME_SUFFIX);
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
