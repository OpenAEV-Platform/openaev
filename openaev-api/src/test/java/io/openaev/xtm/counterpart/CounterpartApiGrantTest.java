package io.openaev.xtm.counterpart;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtm.counterpart.form.CounterpartLookupInput;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reverse lookups another XTM product calls ({@code /xtm/objects/*}) through the real HTTP
 * stack, as a non-admin caller: the asset route answers only with the search right on assets, the
 * finding facts of an asset need the findings right on top of it, and the grant-managed kinds
 * (scenarios, injects) answer only the objects the caller holds a grant on. {@link
 * CounterpartApiTest} calls the controller methods directly, so it never exercises the access
 * control annotations.
 */
@Transactional
@WithMockUser(isAdmin = false)
// EXPLORATION_GRAPH off on purpose: the routes have their own flag, XTM_COUNTERPARTS, on here.
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,XTM_COUNTERPARTS")
@DisplayName("XTM reverse lookups: rights through the HTTP stack")
class CounterpartApiGrantTest extends IntegrationTest {

  private static final String OBJECTS = TENANT_PREFIX + "/xtm/objects/{kind}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private FindingComposer findingComposer;

  private String tenantId;
  private String endpointId;
  private String hostname;

  @BeforeEach
  void resetComposers() {
    endpointComposer.reset();
    findingComposer.reset();
  }

  private void seed(Set<Capability> capabilities) throws Exception {
    tenantId = tenantHelper.createTenantWithCapabilities("xtm-objects-grant", capabilities).getId();
    Tenant tenant = new Tenant(tenantId);
    hostname = "grant-" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setHostname(hostname);
    endpoint.setTenant(tenant);
    Finding finding = FindingFixture.createDefaultCveFindingWithRandomTitle();
    finding.setTenant(tenant);
    findingComposer
        .forFinding(finding)
        .withEndpoint(endpointComposer.forEndpoint(endpoint))
        .persist();
    endpointId = endpoint.getId();
    entityManager.flush();
    entityManager.clear();
  }

  private ResultActions lookup(String kind, List<String> keys) throws Exception {
    return mvc.perform(
        post(OBJECTS, tenantId, kind)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(new CounterpartLookupInput(keys))));
  }

  @Nested
  @DisplayName("assets by hostname")
  class Assets {

    @Test
    @DisplayName("given asset search without findings read, should answer the asset without facts")
    void given_assetSearchWithoutFindingsRead_should_answerTheAssetWithoutFindingFacts()
        throws Exception {
      // -- ARRANGE --
      seed(Set.of(Capability.ACCESS_ASSETS));

      // -- ACT & ASSERT --
      lookup("assets", List.of(hostname))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].object_id").value(endpointId))
          .andExpect(jsonPath("$[0].object_facts").isEmpty());
    }

    @Test
    @DisplayName("given asset search and findings read, should answer the asset with its findings")
    void given_assetSearchAndFindingsRead_should_answerTheFindingCount() throws Exception {
      // -- ARRANGE --
      seed(Set.of(Capability.ACCESS_ASSETS, Capability.ACCESS_FINDINGS));

      // -- ACT & ASSERT --
      lookup("assets", List.of(hostname))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].object_id").value(endpointId))
          .andExpect(
              jsonPath("$[0].object_facts[?(@.fact_key == 'findings')].fact_value").value("1"));
    }

    @Test
    @DisplayName("given no asset search, should refuse the asset lookup")
    void given_noAssetSearch_should_refuseTheAssetLookup() throws Exception {
      // -- ARRANGE --
      seed(Set.of(Capability.ACCESS_FINDINGS));

      // -- ACT & ASSERT --
      lookup("assets", List.of(hostname)).andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("scenarios and injects (grant-managed)")
  class ScenariosAndInjects {

    // Scenarios and injects are grant-managed: the search right opens the route to every caller,
    // and each object is then kept only when the caller holds a grant on it.

    @Test
    @DisplayName("given no scenario grant, should answer no threat")
    void given_noScenarioGrant_should_answerNoThreat() throws Exception {
      // -- ARRANGE --
      seed(Set.of(Capability.ACCESS_ASSETS));

      // -- ACT & ASSERT --
      lookup("threats", List.of("intrusion-set--11111111-1111-4111-8111-111111111111"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("given no inject grant, should answer no alert")
    void given_noInjectGrant_should_answerNoAlert() throws Exception {
      // -- ARRANGE --
      seed(Set.of(Capability.ACCESS_ASSETS));

      // -- ACT & ASSERT --
      lookup("alerts", List.of("https://siem.example/alert/1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isEmpty());
    }
  }
}
