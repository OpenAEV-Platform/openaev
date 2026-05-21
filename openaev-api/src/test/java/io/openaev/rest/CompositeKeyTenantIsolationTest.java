package io.openaev.rest;

import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.utils.fixtures.AgentFixture.createAgent;
import static io.openaev.utils.fixtures.EndpointFixture.createEndpoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that composite-key joins (Agent→Executor, Inject→Injector) are correctly scoped to the
 * current tenant after migration V5_07__Composite_pk_connectors.
 *
 * <p>Before the fix, @JoinColumn only referenced the business ID (e.g. executor_id) without
 * tenant_id, causing Hibernate to produce a cartesian product across tenants — the same agent would
 * appear N times (once per tenant that has the same executor type).
 *
 * <p>These tests create the same connector (executor/injector) in two tenants and assert that
 * queries return exactly one result per tenant, not cross-tenant duplicates.
 */
@TestInstance(PER_CLASS)
@DisplayName("Composite key tenant isolation — no cross-tenant duplicates")
@WithMockUser(isAdmin = true)
class CompositeKeyTenantIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private ExecutorComposer executorComposer;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectRepository injectRepository;

  @Nested
  @DisplayName("Agent → Executor join")
  class AgentExecutorJoin {

    @Test
    @DisplayName(
        "Given same executor type in two tenants, endpoint API should return agent without duplicates")
    void givenSameExecutorInTwoTenants_endpointShouldReturnAgentWithoutDuplicates()
        throws Exception {
      // -- SETUP: remember the default tenant --
      String tenantA = TenantContext.getCurrentTenant();

      // Create executor and endpoint+agent in tenant A
      Executor executorA =
          executorComposer.forExecutor(executorFixture.getDefaultExecutor()).get();

      Endpoint endpointA = endpointRepository.save(createEndpoint("Endpoint-TenantA"));
      Agent agentA = createAgent(endpointA, "ext-ref-A");
      agentA.setExecutor(executorA);
      agentA.setTenant(new Tenant(tenantA));
      endpointA.setAgents(List.of(agentA));
      endpointRepository.save(endpointA);

      // -- SETUP: create tenant B with the same executor type --
      Tenant tenantB = tenantHelper.createTenantWithCurrentUser("TenantB-CompositeKey");
      tenantHelper.switchToTenant(tenantB.getId(), entityManager);

      // The same executor type will be auto-created in tenant B by the fixture
      Executor executorB =
          executorComposer
              .forExecutor(executorFixture.createDefaultExecutor("OpenAEV-B"))
              .persist()
              .get();

      // Switch back to tenant A
      tenantHelper.switchToTenant(tenantA, entityManager);

      // -- EXECUTE: GET endpoint details via REST API --
      String response =
          mvc.perform(
                  get(ENDPOINT_URI + "/" + endpointA.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT: exactly 1 agent, not duplicated across tenants --
      JSONArray agents = JsonPath.read(response, "$.asset_agents");
      assertThat(agents)
          .as(
              "Endpoint should have exactly 1 agent — not duplicated by cross-tenant executor join")
          .hasSize(1);

      String returnedExecutorId = JsonPath.read(response, "$.asset_agents[0].agent_executor");
      assertThat(returnedExecutorId)
          .as("Agent should reference the executor from tenant A, not tenant B")
          .isEqualTo(executorA.getId());
    }

    @Test
    @DisplayName(
        "Given agents in different tenants with same executor ID, each tenant sees only its own agents")
    void givenAgentsInDifferentTenants_eachTenantSeesOnlyItsOwn() throws Exception {
      // -- SETUP: tenant A --
      String tenantA = TenantContext.getCurrentTenant();
      Executor executorA =
          executorComposer.forExecutor(executorFixture.getDefaultExecutor()).get();

      Endpoint endpointA = endpointRepository.save(createEndpoint("Endpoint-IsolationA"));
      Agent agentA = createAgent(endpointA, "ext-ref-isolation-A");
      agentA.setExecutor(executorA);
      agentA.setTenant(new Tenant(tenantA));
      endpointA.setAgents(List.of(agentA));
      endpointRepository.save(endpointA);

      entityManager.flush();
      entityManager.clear();

      // -- ASSERT: tenant A sees its endpoint --
      String responseA =
          mvc.perform(
                  get(ENDPOINT_URI + "/" + endpointA.getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JSONArray agentsA = JsonPath.read(responseA, "$.asset_agents");
      assertThat(agentsA).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Inject → Injector join")
  class InjectInjectorJoin {

    @Test
    @DisplayName(
        "Given same injector type in two tenants, loading inject should reference correct tenant injector")
    void givenSameInjectorInTwoTenants_injectShouldReferenceCorrectInjector() throws Exception {
      // -- SETUP: tenant A --
      String tenantA = TenantContext.getCurrentTenant();

      Injector injectorA =
          InjectorFixture.createInjector("shared-injector-id", "Email Injector", "email");
      injectorA.setTenant(new Tenant(tenantA));
      injectorA = injectorRepository.save(injectorA);

      Inject injectA = new Inject();
      injectA.setTitle("Inject-TenantA");
      injectA.setEnabled(true);
      injectA.setDependsDuration(0L);
      injectA.setInjector(injectorA);
      injectA.setTenant(new Tenant(tenantA));
      injectA = injectRepository.save(injectA);

      entityManager.flush();
      entityManager.clear();

      // -- SETUP: tenant B with same injector type --
      Tenant tenantB = tenantHelper.createTenantWithCurrentUser("TenantB-Injector");
      tenantHelper.switchToTenant(tenantB.getId(), entityManager);

      Injector injectorB =
          InjectorFixture.createInjector("shared-injector-id", "Email Injector", "email");
      injectorB.setTenant(new Tenant(tenantB.getId()));
      injectorRepository.save(injectorB);

      // -- Switch back to tenant A and reload the inject --
      tenantHelper.switchToTenant(tenantA, entityManager);

      Inject reloadedInject =
          injectRepository.findById(injectA.getId()).orElseThrow();

      // -- ASSERT: inject references injector from tenant A, not B --
      assertThat(reloadedInject.getInjector())
          .as("Inject should have an injector (not null)")
          .isNotNull();

      assertThat(reloadedInject.getInjector().getTenant().getId())
          .as("Injector should belong to tenant A")
          .isEqualTo(tenantA);
    }
  }
}
