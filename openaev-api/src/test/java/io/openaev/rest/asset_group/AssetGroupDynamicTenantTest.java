package io.openaev.rest.asset_group;

import static io.openaev.rest.asset.endpoint.EndpointApi.ASSET_URI;
import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.utils.fixtures.AssetGroupFixture.createAssetGroupWithDynamicFilter;
import static io.openaev.utils.fixtures.EndpointFixture.createEndpoint;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The endpoints of a dynamic asset group are matched among the endpoints of the tenant that owns
 * the group, whatever the scope of the request. A request scope can hold several tenants (the
 * non-prefixed routes with {@code X-Tenant-Ids} naming two tenants), and a filter such as a
 * hostname matches the endpoints of every tenant in that scope.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed, in transactions
 * opened through {@link TenantScopedTransaction}, so the requests read them through the real scope
 * of each request.
 */
@WithMockUser(isAdmin = true)
@DisplayName("A dynamic asset group matches the endpoints of its own tenant")
class AssetGroupDynamicTenantTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private AssetRepository assetRepository;

  private String tenantA;
  private String tenantB;
  private String hostname;
  private String groupAId;
  private String endpointAId;
  private String endpointBId;

  @BeforeEach
  void setUp() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("dynamic-group-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("dynamic-group-b").getId();
    hostname = "dyn-" + UUID.randomUUID();
    endpointAId = inTenant(tenantA, () -> saveEndpoint(tenantA));
    endpointBId = inTenant(tenantB, () -> saveEndpoint(tenantB));
    groupAId = inTenant(tenantA, this::saveDynamicGroup);
  }

  @AfterEach
  void cleanup() {
    inTenant(
        tenantA,
        () -> {
          assetGroupRepository.deleteById(groupAId);
          assetRepository.deleteById(endpointAId);
          return null;
        });
    inTenant(
        tenantB,
        () -> {
          assetRepository.deleteById(endpointBId);
          return null;
        });
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  @Nested
  @DisplayName("Read the group under a scope holding both tenants")
  class ReadUnderBothTenants {

    @Test
    @DisplayName("given a scope with two tenants should list only the endpoint of the group tenant")
    void given_scopeWithTwoTenants_should_listOnlyTheEndpointOfTheGroupTenant() throws Exception {
      // Arrange: the filter matches the endpoint of tenant A and the endpoint of tenant B.

      // Act & Assert
      mvc.perform(
              get(ASSET_GROUP_URI + "/{id}", groupAId)
                  .header(TENANT_IDS_HEADER, tenantA + "," + tenantB)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.asset_group_dynamic_assets", contains(endpointAId)));
    }

    @Test
    @DisplayName("given a scope with two tenants should list the group with only its own endpoint")
    void given_scopeWithTwoTenants_should_listTheGroupWithOnlyItsOwnEndpoint() throws Exception {
      // Act & Assert
      mvc.perform(
              get(ASSET_GROUP_URI)
                  .header(TENANT_IDS_HEADER, tenantA + "," + tenantB)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath(
                  "$[?(@.asset_group_id == '" + groupAId + "')].asset_group_dynamic_assets",
                  contains(List.of(endpointAId))));
    }

    @Test
    @DisplayName(
        "given a scope with two tenants should not add the group to the endpoint of another tenant")
    void given_scopeWithTwoTenants_should_notAddTheGroupToTheEndpointOfAnotherTenant()
        throws Exception {
      // Act & Assert
      mvc.perform(
              get(ASSET_URI + "/{id}", endpointBId)
                  .header(TENANT_IDS_HEADER, tenantA + "," + tenantB)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.asset_asset_groups", empty()));
    }
  }

  @Nested
  @DisplayName("Read the group under a scope holding its own tenant only")
  class ReadUnderOneTenant {

    @Test
    @DisplayName("given a scope with the group tenant should list the endpoint of the group tenant")
    void given_scopeWithTheGroupTenant_should_listTheEndpointOfTheGroupTenant() throws Exception {
      // Act & Assert
      mvc.perform(
              get(ASSET_GROUP_URI + "/{id}", groupAId)
                  .header(TENANT_IDS_HEADER, tenantA)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.asset_group_dynamic_assets", contains(endpointAId)));
    }

    @Test
    @DisplayName("given a scope with two tenants should add the group to an endpoint of its tenant")
    void given_scopeWithTwoTenants_should_addTheGroupToAnEndpointOfItsTenant() throws Exception {
      // Act & Assert
      mvc.perform(
              get(ASSET_URI + "/{id}", endpointAId)
                  .header(TENANT_IDS_HEADER, tenantA + "," + tenantB)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.asset_asset_groups[*].asset_group_id", hasItem(groupAId)));
    }
  }

  // -- SEEDING --

  private String saveEndpoint(String tenantId) {
    Endpoint endpoint = createEndpoint("endpoint-" + UUID.randomUUID());
    endpoint.setHostname(hostname);
    endpoint.setTenant(new Tenant(tenantId));
    return assetRepository.save(endpoint).getId();
  }

  private String saveDynamicGroup() {
    Filters.FilterGroup dynamicFilter =
        Filters.FilterGroup.builder()
            .filters(
                List.of(
                    Filters.Filter.builder()
                        .key("asset_hostname")
                        .operator(Filters.FilterOperator.contains)
                        .values(List.of(hostname))
                        .build()))
            .build();
    AssetGroup group =
        createAssetGroupWithDynamicFilter("dynamic-" + UUID.randomUUID(), dynamicFilter);
    group.setTenant(new Tenant(tenantA));
    return assetGroupRepository.save(group).getId();
  }

  /**
   * Runs the work in a committed transaction scoped to one tenant. The fixtures read the v1
   * thread-local tenant to attribute some rows, so it is set too.
   */
  private <T> T inTenant(String tenantId, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
