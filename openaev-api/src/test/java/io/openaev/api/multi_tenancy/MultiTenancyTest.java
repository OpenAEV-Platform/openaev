package io.openaev.api.multi_tenancy;

import static io.openaev.rest.asset.endpoint.EndpointApi.TENANT_ENDPOINT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.TenantUriUtils;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// TODO multi-tenancy: uncomment tests when multi tenancy will work + add tests with URL and tenant
// id with objects linked like scenarios, injects,...
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiTenancyTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private EndpointRepository endpointRepository;

  @Test
  @DisplayName(
      "Should_save_endpoints_on_tenants_and_return_only_endpoints_for_requested_tenant_when_find_all")
  @WithMockUser(isAdmin = true)
  void
      Should_save_endpoints_on_tenants_and_return_only_endpoints_for_requested_tenant_when_find_all()
          throws Exception {

    Tenant tenant1 = TenantFixture.getTenant("tenant1");
    Tenant tenant2 = TenantFixture.getTenant("tenant2");
    tenant1 = tenantComposer.forTenant(tenant1).persist().get();
    tenant2 = tenantComposer.forTenant(tenant2).persist().get();

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

    Tenant tenant1 = TenantFixture.getTenant("tenant1");
    Tenant tenant2 = TenantFixture.getTenant("tenant2");
    tenant1 = tenantComposer.forTenant(tenant1).persist().get();
    tenant2 = tenantComposer.forTenant(tenant2).persist().get();
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
}
