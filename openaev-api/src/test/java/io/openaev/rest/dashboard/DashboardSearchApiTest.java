package io.openaev.rest.dashboard;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.facade.EngineService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * F408690-40: the dashboard free-text search, called over HTTP by a read-only (non-admin,
 * ACCESS_DASHBOARDS only) user. The caller is a member of both tenants, so an injected clause that
 * escaped the tenant {@code bool.filter} would surface tenant B's document under tenant A's path.
 */
@Transactional
@WithMockUser(isAdmin = false)
@DisplayName("dashboard search treats its input as a value for a read-only user")
class DashboardSearchApiTest extends IntegrationTest {

  private static final String SEARCH_URI = TENANT_PREFIX + "/dashboards/search/{search}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;

  private String tenantA;
  private String zorblaxId;
  private String tenantBZorblaxId;

  @BeforeEach
  void seedOneMatchingEndpointPerTenant() throws Exception {
    endpointComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
    tenantA =
        tenantHelper
            .createTenantWithCapabilities(
                "dashboard-search-a", Set.of(Capability.ACCESS_DASHBOARDS))
            .getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("dashboard-search-b").getId();
    zorblaxId = persistEndpointForTenant(tenantA, "zorblax-server");
    persistEndpointForTenant(tenantA, "quintor-gateway");
    tenantBZorblaxId = persistEndpointForTenant(tenantB, "zorblax-tenant-b");

    entityManager.flush();
    entityManager.clear();
    engineService.bulkProcessing(engineContext.getModels().stream());
    // ES processes indexing asynchronously — give it time
    Thread.sleep(1_000);
  }

  private String persistEndpointForTenant(String tenantId, String name) {
    Endpoint endpoint = EndpointFixture.createEndpoint(name);
    endpoint.setTenant(new Tenant(tenantId));
    return endpointComposer.forEndpoint(endpoint).persist().get().getId();
  }

  @Test
  @DisplayName("control: a plain term returns the caller's tenant document only")
  void given_plainTerm_should_returnOwnTenantDocumentOnly() throws Exception {
    // Without this positive case the empty results below could be an RBAC refusal or an empty
    // index in disguise.
    mvc.perform(get(SEARCH_URI, tenantA, "zorblax"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].base_id").value(zorblaxId));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"zorb", "zorblax server", "ZORBLAX"})
  @DisplayName("legitimate searches still find the caller's document")
  void given_legitimateSearch_should_findOwnDocument(String term) throws Exception {
    mvc.perform(get(SEARCH_URI, tenantA, term))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].base_id").value(zorblaxId));
  }

  @Test
  @DisplayName("an exact id finds the document")
  void given_exactId_should_findDocument() throws Exception {
    // Guards SEARCH_ID_FIELD: a term on a sub-field that does not exist matches nothing silently.
    mvc.perform(get(SEARCH_URI, tenantA, zorblaxId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].base_id").value(zorblaxId));
  }

  @Test
  @DisplayName("a blank search is rejected")
  void given_blankSearch_should_return400() throws Exception {
    mvc.perform(get(SEARCH_URI, tenantA, "   ")).andExpect(status().isBadRequest());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        "*",
        "NOT zorblax",
        "zorblax OR quintor",
        "zorblax) OR (*",
        "*) OR base_tenant_side:*",
        "base_entity:asset",
        "_exists_:base_id"
      })
  @DisplayName("Lucene syntax neither widens the search nor escapes the tenant filter")
  void given_luceneSyntax_should_returnNothing(String term) throws Exception {
    mvc.perform(get(SEARCH_URI, tenantA, term))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)))
        .andExpect(jsonPath("$[?(@.base_id=='" + tenantBZorblaxId + "')]").doesNotExist());
  }

  @Test
  @DisplayName("a grouping injection around a real term keeps tenant B's document out")
  void given_groupingInjection_should_notLeakOtherTenant() throws Exception {
    mvc.perform(get(SEARCH_URI, tenantA, "(zorblax) OR (*"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.base_id=='" + tenantBZorblaxId + "')]").doesNotExist());
  }

  @Test
  @DisplayName("a one-character search is still accepted")
  void given_oneCharacterSearch_should_return200() throws Exception {
    mvc.perform(get(SEARCH_URI, tenantA, "z")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("a search of 200 characters is accepted")
  void given_maxLengthSearch_should_return200() throws Exception {
    mvc.perform(get(SEARCH_URI, tenantA, "z".repeat(200))).andExpect(status().isOk());
  }

  @Test
  @DisplayName("a search longer than 200 characters is rejected")
  void given_tooLongSearch_should_return400() throws Exception {
    mvc.perform(get(SEARCH_URI, tenantA, "z".repeat(201))).andExpect(status().isBadRequest());
  }
}
