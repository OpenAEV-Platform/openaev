package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectTestStatusRepository;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.rest.inject.form.InjectBulkProcessingInput;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectTestStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectTestStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utilstest.RowTenantScope;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
public class ExerciseInjectTestApiTest extends IntegrationTest {

  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private MockMvc mvc;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private ManagerFactory managerFactory;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectTestStatusComposer injectTestStatusComposer;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private RowTenantScope rowTenantScope;

  @Autowired private InjectTestStatusRepository injectTestStatusRepository;

  private Exercise simulation;
  private Inject inject1, inject2, otherSimulationInject;
  private InjectTestStatus injectTestStatus1, injectTestStatus2, otherSimulationInjectTestStatus;

  @BeforeEach
  void beforeEach() throws Exception {
    builtinConnectorRegistration.register(
        emailInjectorIntegrationFactory, TenantContext.getCurrentTenant());
    managerFactory.getManager(TenantContext.getCurrentTenant()).monitorIntegrations();
  }

  @BeforeAll
  void setupData() {
    // @BeforeAll runs outside any transaction, so injectors_contracts (tenant-active) needs an
    // explicit scope for the whole arrangement: resolving the contract and persisting the injects
    // that reference it must share one scope, or the contract is detached by the time the inject
    // composer tries to save a reference to it (matching the default tenant @BeforeEach registers
    // the built-in email injector into).
    rowTenantScope.write(
        Tenant.DEFAULT_TENANT_UUID,
        () -> {
          InjectorContract injectorContract =
              injectorContractFixture.getWellKnownSingleEmailContract();

          InjectTestStatusComposer.Composer injectTestStatusComposer1 =
              injectTestStatusComposer.forInjectTestStatus(
                  InjectTestStatusFixture.createSuccessInjectStatus());

          InjectTestStatusComposer.Composer injectTestStatusComposer2 =
              injectTestStatusComposer.forInjectTestStatus(
                  InjectTestStatusFixture.createSuccessInjectStatus());

          InjectComposer.Composer injectComposer1 =
              injectComposer
                  .forInject(InjectFixture.getInjectForEmailContract(injectorContract))
                  .withInjectTestStatus(injectTestStatusComposer1);

          InjectComposer.Composer injectComposer2 =
              injectComposer
                  .forInject(InjectFixture.getInjectForEmailContract(injectorContract))
                  .withInjectTestStatus(injectTestStatusComposer2);

          inject1 = injectComposer1.persist().get();
          inject2 = injectComposer2.persist().get();

          injectTestStatus1 = injectTestStatusComposer1.persist().get();
          injectTestStatus2 = injectTestStatusComposer2.persist().get();

          simulation =
              simulationComposer
                  .forExercise(ExerciseFixture.createDefaultExercise())
                  .withInjects(List.of(injectComposer1, injectComposer2))
                  .persist()
                  .get();

          InjectTestStatusComposer.Composer otherSimulationInjectTestStatusComposer =
              injectTestStatusComposer.forInjectTestStatus(
                  InjectTestStatusFixture.createSuccessInjectStatus());
          InjectComposer.Composer otherSimulationInjectComposer =
              injectComposer
                  .forInject(InjectFixture.getInjectForEmailContract(injectorContract))
                  .withInjectTestStatus(otherSimulationInjectTestStatusComposer);
          otherSimulationInject = otherSimulationInjectComposer.persist().get();
          otherSimulationInjectTestStatus = otherSimulationInjectTestStatusComposer.persist().get();
          simulationComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(otherSimulationInjectComposer)
              .persist();
        });
  }

  @Nested
  @DisplayName("As SimulationPlanner")
  class SimulationPlannerAccess {

    @Test
    @DisplayName("Should return paginated inject test results when inject tests exist")
    @WithMockUser
    void should_return_paginated_results_when_inject_tests_exist() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER, simulation.getId());
      // Real tenant membership for the current mock user, matching the ambient default tenant
      // this test's @BeforeEach already scoped the transaction to (via
      // managerFactory.getManager(TenantContext.getCurrentTenant())): without it, the mock user's
      // caller-authorized tenant set is empty, so this endpoint's own TxCtx resolution collapses
      // to the missing scope and conflicts with the scope already set by @BeforeEach within the
      // same test transaction (see TenantScopeTransactionAspect).
      String currentTenantId = TenantContext.getCurrentTenant();
      tenantRepository.addUserToTenant(testUserHolder.get().getId(), currentTenantId);
      tenantMembershipCacheManager.evict(testUserHolder.get().getId(), currentTenantId);

      SearchPaginationInput searchPaginationInput = new SearchPaginationInput();
      String response =
          mvc.perform(
                  post(EXERCISE_URI + "/{simulationId}/injects/test/search", simulation.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(searchPaginationInput))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .inPath("$.content[*].status_id")
          .isArray()
          .contains(injectTestStatus1.getId());
    }

    @Test
    @DisplayName("Should return test status using test id")
    @WithMockUser
    void should_return_test_status_by_testId() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER, simulation.getId());
      mvc.perform(
              get(EXERCISE_URI + "/injects/test/{testId}", injectTestStatus1.getId()).with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return test status when testing a specific inject")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void should_return_test_status_when_testing_specific_inject() throws Exception {
      mvc.perform(
              get(
                      EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                      simulation.getId(),
                      inject1.getId())
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.inject_id").value(inject1.getId()));
    }

    @Test
    @DisplayName("Should return test statuses when performing bulk test with inject IDs")
    @WithMockUser(autoJoinDefaultTenant = true)
    void should_return_test_statuses_when_bulk_testing_with_inject_ids() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER, simulation.getId());

      InjectBulkProcessingInput input = new InjectBulkProcessingInput();
      input.setInjectIDsToProcess(List.of(inject1.getId()));
      input.setSimulationOrScenarioId(simulation.getId());

      mvc.perform(
              post(EXERCISE_URI + "/{simulationId}/injects/test", simulation.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should return 200 when deleting an inject test status")
    @WithMockUser(isAdmin = true)
    void should_return_200_when_fetching_deleting_an_inject_test_status() throws Exception {
      mvc.perform(
              delete(
                      EXERCISE_URI + "/{simulationId}/injects/test/{testId}",
                      simulation.getId(),
                      injectTestStatus2.getId())
                  .with(csrf()))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("As Unauthorized User")
  class UnauthorizedUserAccess {

    @Test
    @DisplayName("Should return 200 when search a paginated inject test results")
    @WithMockUser
    void should_return_200_when_search_paginated_results() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulation.getId());
      SearchPaginationInput searchPaginationInput = new SearchPaginationInput();
      mvc.perform(
              post(EXERCISE_URI + "/{simulationId}/injects/test/search", simulation.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 200 when search by id")
    @WithMockUser
    void should_return_200_when_search_by_testId() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulation.getId());
      mvc.perform(
              get(EXERCISE_URI + "/injects/test/{testId}", injectTestStatus1.getId()).with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 403 when testing a specific inject")
    @WithMockUser
    void should_return_403_when_testing_specific_inject() throws Exception {
      mvc.perform(
              get(
                      EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                      simulation.getId(),
                      inject1.getId())
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when performing bulk test with inject IDs")
    @WithMockUser
    void should_return_403_when_bulk_testing_with_inject_ids() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulation.getId());

      InjectBulkProcessingInput input = new InjectBulkProcessingInput();
      input.setInjectIDsToProcess(List.of(inject1.getId(), inject2.getId()));
      input.setSimulationOrScenarioId(simulation.getId());

      mvc.perform(
              post(EXERCISE_URI + "/{simulationId}/injects/test", simulation.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when fetching a deleted inject test status")
    @WithMockUser
    void should_return_403_when_fetching_deleted_inject_test_status() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulation.getId());
      mvc.perform(
              delete(
                      EXERCISE_URI + "/{simulationId}/injects/test/{testId}",
                      simulation.getId(),
                      injectTestStatus1.getId())
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Inject test scoped to its simulation")
  class InjectTestScopedToSimulation {

    @Test
    @DisplayName("A simulation launcher can test an inject of the simulation")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_launcherGrant_should_testInject() throws Exception {
      // Arrange
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.LAUNCHER, simulation.getId());

      // Act & Assert
      mvc.perform(
              get(
                      EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                      simulation.getId(),
                      inject1.getId())
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.inject_id").value(inject1.getId()));
    }

    @Test
    @DisplayName("A simulation observer cannot test an inject")
    @WithMockUser(autoJoinDefaultTenant = true)
    void given_observerGrant_should_return403_when_testingInject() throws Exception {
      // Arrange
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulation.getId());

      // Act & Assert
      mvc.perform(
              get(
                      EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                      simulation.getId(),
                      inject1.getId())
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Testing an inject of another simulation returns 404")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_injectOfAnotherSimulation_should_return404_when_testingInject() throws Exception {
      // Act & Assert
      mvc.perform(
              get(
                      EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                      simulation.getId(),
                      otherSimulationInject.getId())
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deleting a test of another simulation returns 404 and keeps it")
    @WithMockUser(isAdmin = true)
    void given_testOfAnotherSimulation_should_return404_when_deletingInjectTest() throws Exception {
      // Act
      mvc.perform(
              delete(
                      EXERCISE_URI + "/{simulationId}/injects/test/{testId}",
                      simulation.getId(),
                      otherSimulationInjectTestStatus.getId())
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(injectTestStatusRepository.existsById(otherSimulationInjectTestStatus.getId()))
          .isTrue();
    }
  }
}
