package io.openaev.rest;

import static io.openaev.rest.asset.endpoint.EndpointApi.ASSET_URI;
import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A decision a handler takes from the caller's capabilities holds in the tenant of the rows it
 * gates.
 *
 * <p>The caller holds the rights {@code @WithMockUser} grants in the default tenant, and is a
 * member of tenant B, where it holds only what a test grants there. The requests select B with the
 * {@code X-Tenant-Ids} header: the rows come from B while the ambient tenant stays the default one.
 * The member contrast grants the same right in B and expects the rows back.
 */
@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.marking.active-tables=assets")
@DisplayName("Capability decisions hold in the tenant of the rows they gate")
class CapabilityDecisionTenantTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private MarkingClearanceCacheManager clearanceCache;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    payloadComposer.reset();
    endpointComposer.reset();
    markingDefinitionComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("capability-decision-b").getId();
  }

  @Nested
  @DisplayName("Payload search")
  @WithMockUser(withCapabilities = {Capability.ACCESS_PAYLOADS})
  class PayloadSearch {

    @Test
    @DisplayName("given the access right only outside B should not search B's payload")
    void given_accessRightOnlyOutsideB_should_notSearchBPayload() throws Exception {
      // -- ARRANGE --
      String payloadId = payloadOfB();

      // -- ACT & ASSERT --
      mvc.perform(inB(post(PAYLOAD_URI + "/search")).content(defaultSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(payloadPath(payloadId)).doesNotExist());
    }

    @Test
    @DisplayName("given the access right in B should search B's payload")
    void given_accessRightInB_should_searchBPayload() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.ACCESS_PAYLOADS);
      String payloadId = payloadOfB();

      // -- ACT & ASSERT --
      mvc.perform(inB(post(PAYLOAD_URI + "/search")).content(defaultSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(payloadPath(payloadId)).exists());
    }

    private String payloadPath(String payloadId) {
      return "$.content[?(@.payload_id == '" + payloadId + "')]";
    }

    private String payloadOfB() {
      Payload payload = PayloadFixture.createDefaultCommand();
      payload.setTenant(new Tenant(tenantB));
      String payloadId = payloadComposer.forPayload(payload).persist().get().getId();
      entityManager.flush();
      return payloadId;
    }
  }

  @Nested
  @DisplayName("Marking read clearance")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSETS, Capability.MANAGE_STIX_BUNDLE})
  class MarkingReadClearance {

    @Test
    @DisplayName("given the clearance capability only outside B should not search B's marked asset")
    void given_clearanceCapabilityOnlyOutsideB_should_notSearchBMarkedAsset() throws Exception {
      // -- ARRANGE --
      String assetId = markedAssetOfB();

      // -- ACT & ASSERT --
      mvc.perform(inB(post(ASSET_URI + "/search")).content(defaultSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(assetPath(assetId)).doesNotExist());
    }

    @Test
    @DisplayName("given the clearance capability in B should search B's marked asset")
    void given_clearanceCapabilityInB_should_searchBMarkedAsset() throws Exception {
      // -- ARRANGE --
      grantInB(Capability.MANAGE_STIX_BUNDLE);
      String assetId = markedAssetOfB();

      // -- ACT & ASSERT --
      mvc.perform(inB(post(ASSET_URI + "/search")).content(defaultSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(assetPath(assetId)).exists());
    }

    private String assetPath(String assetId) {
      return "$.content[?(@.asset_id == '" + assetId + "')]";
    }

    /** An asset of B carrying a marking of B that no group grants to the caller. */
    private String markedAssetOfB() {
      MarkingDefinition marking =
          markingDefinitionComposer
              .forMarkingDefinition(
                  createMarkingDefinition(
                      MarkingDefinition.TYPE_TLP, uniqueDefinition(), 60, "#111111"))
              .withTenantId(tenantB)
              .persist()
              .get();
      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setTenant(new Tenant(tenantB));
      endpoint.setMarkingIds(new String[] {marking.getId()});
      String assetId = endpointComposer.forEndpoint(endpoint).persist().get().getId();
      entityManager.flush();
      // The clearance is read with JDBC and cached: drop what an earlier request left.
      clearanceCache.evictForUser(testUserHolder.get().getId());
      return assetId;
    }
  }

  // -- HELPERS --

  /**
   * The non-prefixed route selecting B. The ambient tenant is cleared first: fixtures leave one on
   * the test thread, and the request must see production's fallback to the default tenant.
   */
  private MockHttpServletRequestBuilder inB(MockHttpServletRequestBuilder request) {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return request
        .header(TENANT_IDS_HEADER, tenantB)
        .contentType(MediaType.APPLICATION_JSON)
        .with(csrf());
  }

  private void grantInB(Capability... capabilities) {
    tenantHelper.grantCapabilitiesInTenant(tenantB, Set.of(capabilities));
  }

  private String defaultSearch() {
    return asJsonString(PaginationFixture.getDefault().textSearch("").build());
  }
}
