package io.openaev.api.multi_tenancy;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.asset.endpoint.EndpointApi.TENANT_ENDPOINT_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.TenantUriUtils;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiTenancyTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private TagComposer tagComposer;
  @Autowired private TagRepository tagRepository;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ScenarioRepository scenarioRepository;
  private Tenant tenant1;
  private Tenant tenant2;

  @BeforeEach
  public void setup() {
    tenant1 = TenantFixture.getTenant("tenant1");
    tenant2 = TenantFixture.getTenant("tenant2");
    tenant1 = tenantComposer.forTenant(tenant1).persist().get();
    tenant2 = tenantComposer.forTenant(tenant2).persist().get();
  }

  @Test
  @DisplayName(
      "Should_save_endpoints_on_tenants_and_return_only_endpoints_for_requested_tenant_when_find_all")
  @WithMockUser(isAdmin = true)
  void
      Should_save_endpoints_on_tenants_and_return_only_endpoints_for_requested_tenant_when_find_all()
          throws Exception {

    String uriEndpointTenant1 =
        TENANT_ENDPOINT_URI.replace(
            "{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenant1.getId());
    String uriEndpointTenant2 =
        TENANT_ENDPOINT_URI.replace(
            "{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenant2.getId());

    Endpoint endpoint1 = EndpointFixture.createEndpoint("endpoint1");
    mvc.perform(
            post(uriEndpointTenant1 + "/agentless")
                .content(asJsonString(endpoint1))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
    Endpoint endpoint2 = EndpointFixture.createEndpoint("endpoint2");
    mvc.perform(
            post(uriEndpointTenant1 + "/agentless")
                .content(asJsonString(endpoint2))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
    Endpoint endpoint3 = EndpointFixture.createEndpoint("endpoint3");
    mvc.perform(
            post(uriEndpointTenant1 + "/agentless")
                .content(asJsonString(endpoint3))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
    Endpoint endpoint4 = EndpointFixture.createEndpoint("endpoint4");
    mvc.perform(
            post(uriEndpointTenant2 + "/agentless")
                .content(asJsonString(endpoint4))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
    Endpoint endpoint5 = EndpointFixture.createEndpoint("endpoint5");
    mvc.perform(
            post(uriEndpointTenant2 + "/agentless")
                .content(asJsonString(endpoint5))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();
    mvc.perform(
            post(uriEndpointTenant1 + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.numberOfElements").value(3))
        .andReturn()
        .getResponse()
        .getContentAsString();
    mvc.perform(
            post(uriEndpointTenant2 + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.numberOfElements").value(2))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @DisplayName("Should_return_exception_when_trying_to_update_endpoint_with_different_tenant")
  @WithMockUser(isAdmin = true)
  void should_return_exception_when_trying_to_update_endpoint_with_different_tenant() {

    String idTenant2 = tenant2.getId();

    TenantContext.setCurrentTenant(tenant1.getId());
    Endpoint endpoint1 = EndpointFixture.createEndpoint("endpoint1");
    Endpoint persistedEndpoint = endpointComposer.forEndpoint(endpoint1).persist().get();
    Endpoint managedEndpoint = endpointRepository.findById(persistedEndpoint.getId()).orElseThrow();

    assertThatThrownBy(
            () -> {
              TenantContext.setCurrentTenant(idTenant2);
              managedEndpoint.setName("endpoint1-updated");
              entityManager.flush();
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Tenant is immutable");
  }

  @Test
  @DisplayName(
      "Should_save_tags_on_tenants_and_return_only_tags_for_requested_tenant_when_find_all")
  @WithMockUser(isAdmin = true)
  void Should_save_tags_on_tenants_and_return_only_tags_for_requested_tenant_when_find_all()
      throws Exception {

    String tenantTagUri = TENANT_PREFIX + "/tags";
    String uriTagTenant1 =
        tenantTagUri.replace("{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenant1.getId());
    String uriTagTenant2 =
        tenantTagUri.replace("{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenant2.getId());

    Tag tag1 = TagFixture.getTagWithTextAndColour("tag1", "#AA0001");
    mvc.perform(
            post(uriTagTenant1)
                .content(asJsonString(tag1))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    Tag tag2 = TagFixture.getTagWithTextAndColour("tag2", "#AA0002");
    mvc.perform(
            post(uriTagTenant1)
                .content(asJsonString(tag2))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    Tag tag3 = TagFixture.getTagWithTextAndColour("tag3", "#AA0003");
    mvc.perform(
            post(uriTagTenant1)
                .content(asJsonString(tag3))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    Tag tag4 = TagFixture.getTagWithTextAndColour("tag4", "#AA0004");
    mvc.perform(
            post(uriTagTenant2)
                .content(asJsonString(tag4))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    Tag tag5 = TagFixture.getTagWithTextAndColour("tag5", "#AA0005");
    mvc.perform(
            post(uriTagTenant2)
                .content(asJsonString(tag5))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();
    mvc.perform(
            post(uriTagTenant1 + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.numberOfElements").value(3));
    mvc.perform(
            post(uriTagTenant2 + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.numberOfElements").value(2));
  }

  @Test
  @DisplayName("Should_return_exception_when_trying_to_update_tag_with_different_tenant")
  @WithMockUser(isAdmin = true)
  void should_return_exception_when_trying_to_update_tag_with_different_tenant() {

    String idTenant2 = tenant2.getId();

    TenantContext.setCurrentTenant(tenant1.getId());
    Tag tag = TagFixture.getTagNoId();
    tag.setName("tag-update-test");
    Tag persistedTag = tagComposer.forTag(tag).persist().get();
    Tag managedTag = tagRepository.findById(persistedTag.getId()).orElseThrow();

    assertThatThrownBy(
            () -> {
              TenantContext.setCurrentTenant(idTenant2);
              managedTag.setName("tag-update-test-updated");
              entityManager.flush();
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Tenant is immutable");
  }

  @Test
  @DisplayName(
      "Should_save_scenarios_on_tenants_and_return_only_scenarios_for_requested_tenant_when_find_all")
  @WithMockUser(isAdmin = true)
  void
      Should_save_scenarios_on_tenants_and_return_only_scenarios_for_requested_tenant_when_find_all()
          throws Exception {

    String uriScenarioTenant1 =
        TENANT_SCENARIO_URI.replace(
            "{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenant1.getId());
    String uriScenarioTenant2 =
        TENANT_SCENARIO_URI.replace(
            "{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenant2.getId());

    Scenario scenario1 = ScenarioFixture.createDefaultCrisisScenario();
    scenario1.setName("scenario1");
    mvc.perform(
            post(uriScenarioTenant1)
                .content(asJsonString(scenario1))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    Scenario scenario2 = ScenarioFixture.createDefaultCrisisScenario();
    scenario2.setName("scenario2");
    mvc.perform(
            post(uriScenarioTenant1)
                .content(asJsonString(scenario2))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    Scenario scenario3 = ScenarioFixture.createDefaultCrisisScenario();
    scenario3.setName("scenario3");
    mvc.perform(
            post(uriScenarioTenant1)
                .content(asJsonString(scenario3))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    Scenario scenario4 = ScenarioFixture.createDefaultCrisisScenario();
    scenario4.setName("scenario4");
    mvc.perform(
            post(uriScenarioTenant2)
                .content(asJsonString(scenario4))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    Scenario scenario5 = ScenarioFixture.createDefaultCrisisScenario();
    scenario5.setName("scenario5");
    mvc.perform(
            post(uriScenarioTenant2)
                .content(asJsonString(scenario5))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();
    mvc.perform(
            post(uriScenarioTenant1 + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.numberOfElements").value(3));
    mvc.perform(
            post(uriScenarioTenant2 + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.numberOfElements").value(2));
  }

  @Test
  @DisplayName("Should_return_exception_when_trying_to_update_scenario_with_different_tenant")
  @WithMockUser(isAdmin = true)
  void should_return_exception_when_trying_to_update_scenario_with_different_tenant() {

    String idTenant2 = tenant2.getId();

    TenantContext.setCurrentTenant(tenant1.getId());
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setName("scenario-update-test");
    Scenario persistedScenario = scenarioComposer.forScenario(scenario).persist().get();
    Scenario managedScenario = scenarioRepository.findById(persistedScenario.getId()).orElseThrow();

    assertThatThrownBy(
            () -> {
              TenantContext.setCurrentTenant(idTenant2);
              managedScenario.setName("scenario-update-test-updated");
              entityManager.flush();
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Tenant is immutable");
  }
}
