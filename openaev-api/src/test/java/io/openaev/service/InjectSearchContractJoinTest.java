package io.openaev.service;

import static io.openaev.rest.atomic_testing.AtomicTestingApi.TENANT_ATOMIC_TESTING_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inject searches join the injector contract and group by inject, so that the inject's
 * collections can be aggregated into arrays, while projecting contract columns.
 *
 * <p>Once {@code injectors_contracts} is tenant-active, the statement inspector replaces the joined
 * table with a scoped derived table. PostgreSQL only accepts an ungrouped column of a base table
 * whose primary key is grouped, so every projected contract column must be grouped explicitly.
 * These tests arm the contract tables and pin the row count and the contract values both searches
 * return.
 */
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties =
        "openaev.tenant.active-tables=injectors_contracts,injector_contract_tags,"
            + "injectors_contracts_attack_patterns,injectors_contracts_domains,"
            + "injectors_contracts_vulnerabilities,injectors_injector_contracts")
@DisplayName("Inject searches joining the injector contract")
class InjectSearchContractJoinTest extends IntegrationTest {

  private static final Map<String, String> CONTRACT_LABELS = Map.of("en", "Contract label");
  private static final Endpoint.PLATFORM_TYPE[] CONTRACT_PLATFORMS = {
    Endpoint.PLATFORM_TYPE.Linux, Endpoint.PLATFORM_TYPE.Windows
  };
  private static final String CONTRACT_CONTENT =
      "{\"fields\": [], \"label\": {\"en\": \"pinned\"}}";

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  @BeforeEach
  void setUp() {
    injectComposer.reset();
    injectorContractComposer.reset();
    endpointComposer.reset();
    scenarioComposer.reset();
  }

  private InjectComposer.Composer injectWithContractOnTwoAssets() throws Exception {
    InjectorContract contract =
        InjectorContractFixture.createInjectorContract(CONTRACT_LABELS, CONTRACT_CONTENT);
    contract.setPlatforms(CONTRACT_PLATFORMS);
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectorContract(injectorContractComposer.forInjectorContract(contract))
        .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint()))
        .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint()));
  }

  @Nested
  @DisplayName("Atomic testing search, projecting contract columns")
  class AtomicTestingSearch {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("Given an atomic testing with a contract, should return one row with its values")
    void given_atomicTestingWithContract_should_returnOneRowWithContractValues() throws Exception {
      // Arrange
      Inject inject = injectWithContractOnTwoAssets().persist().get();
      entityManager.flush();
      entityManager.clear();

      // Act
      String response =
          mvc.perform(
                  post(TENANT_ATOMIC_TESTING_URI + "/search", Tenant.DEFAULT_TENANT_UUID)
                      .content(asJsonString(PaginationFixture.simpleTextSearch(inject.getTitle())))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).node("totalElements").isEqualTo(1);
      assertThatJson(response).node("content").isArray().hasSize(1);
      assertThatJson(response).node("content[0].inject_id").isEqualTo(inject.getId());
      assertThatJson(response)
          .node("content[0].inject_injector_contract")
          .isObject()
          .containsEntry("injector_contract_id", inject.getInjectorContract().get().getId())
          .containsEntry("injector_contract_content", CONTRACT_CONTENT)
          .containsEntry("injector_contract_labels", CONTRACT_LABELS);
      assertThatJson(response)
          .node("content[0].inject_injector_contract.injector_contract_platforms")
          .isArray()
          .containsExactlyInAnyOrder("Linux", "Windows");
      assertThatJson(response)
          .node("content[0].inject_injector_contract.convertedContent.label.en")
          .isEqualTo("pinned");
    }
  }

  @Nested
  @DisplayName("Scenario inject list, projecting the whole contract")
  class ScenarioInjectList {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("Given a scenario inject with a contract, should return one row with its values")
    void given_scenarioInjectWithContract_should_returnOneRowWithContractValues() throws Exception {
      // Arrange
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .withInject(injectWithContractOnTwoAssets())
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();

      // Act
      String response =
          mvc.perform(
                  get(
                          TENANT_SCENARIO_URI + "/{scenarioId}/injects/simple",
                          Tenant.DEFAULT_TENANT_UUID,
                          scenario.getId())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).isArray().hasSize(1);
      assertThatJson(response).node("[0].inject_scenario").isEqualTo(scenario.getId());
      assertThatJson(response).node("[0].inject_assets").isArray().hasSize(2);
      assertThatJson(response)
          .node("[0].inject_injector_contract")
          .isObject()
          .containsEntry("injector_contract_content", CONTRACT_CONTENT)
          .containsEntry("injector_contract_labels", CONTRACT_LABELS);
      assertThatJson(response)
          .node("[0].inject_injector_contract.injector_contract_platforms")
          .isArray()
          .containsExactlyInAnyOrder("Linux", "Windows");
    }
  }
}
