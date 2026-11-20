package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.service.catalog.CatalogFacet.AUTHOR_KEY;
import static io.openaev.service.catalog.CatalogFacet.CATEGORY_KEY;
import static io.openaev.service.catalog.CatalogFacet.DOMAIN_KEY;
import static io.openaev.service.catalog.CatalogFacet.PLATFORMS_KEY;
import static io.openaev.service.catalog.CatalogFacet.SCENARIO_TYPE_KEY;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createCatalogInjectorWithContract;
import static io.openaev.utils.fixtures.CatalogItemFixture.item;
import static io.openaev.utils.fixtures.CatalogItemFixture.release;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.catalog.dto.action.AvailableActionOutput;
import io.openaev.database.model.Filters;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogItemFixture.Item;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=CATALOG")
@DisplayName("Searching the Available tabs of the catalogue")
class CatalogAvailableSearchApiTest extends IntegrationTest {

  private static final String SCENARIOS_URI = TENANT_CATALOG_URI + "/scenarios";
  private static final String ACTIONS_URI = TENANT_CATALOG_URI + "/actions";
  private static final String NAME = "catalog_item_name";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;

  @BeforeEach
  void attachToDefaultTenant() {
    catalogConnectorComposer.reset();
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
  }

  // -- HELPERS --

  private void seed(Item... items) {
    String releaseId = release(entityManager, DEFAULT_TENANT_UUID, "1.0.0");
    for (Item seeded : items) {
      item(entityManager, DEFAULT_TENANT_UUID, releaseId, seeded);
    }
  }

  private static SearchPaginationInput byName(int page, int size) {
    SearchPaginationInput input = new SearchPaginationInput();
    input.setPage(page);
    input.setSize(size);
    input.setSorts(List.of(new SortField(NAME, "asc", null)));
    return input;
  }

  private static SearchPaginationInput filtered(String key, String value) {
    SearchPaginationInput input = byName(0, 20);
    input.setFilterGroup(
        Filters.FilterGroup.builder()
            .filters(
                List.of(Filters.Filter.builder().id(key).key(key).values(List.of(value)).build()))
            .build());
    return input;
  }

  private ResultActions search(String uri, SearchPaginationInput input) throws Exception {
    return mvc.perform(
        post(uri, DEFAULT_TENANT_UUID)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(input)));
  }

  // -- SCENARIOS --

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Scenarios")
  class Scenarios {

    @BeforeEach
    void seedTwoScenarios() {
      seed(
          Item.scenario("phishing")
              .withPlatforms("Linux")
              .withCategory("attack-scenario")
              .withAuthor("Filigran"),
          Item.scenario("ransomware")
              .withScenarioType("CHAINED")
              .withPlatforms("Windows,Linux")
              .withCategory("incident-response")
              .withAuthor("Community"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
      SCENARIO_TYPE_KEY + ", CHAINED, ransomware",
      PLATFORMS_KEY + ", Windows, ransomware",
      CATEGORY_KEY + ", attack-scenario, phishing",
      AUTHOR_KEY + ", Filigran, phishing"
    })
    @DisplayName("lists only the scenarios a filter matches")
    void given_filter_should_listMatchingScenarios(String key, String value, String expected)
        throws Exception {
      // Act
      ResultActions result = search(SCENARIOS_URI + "/search", filtered(key, value));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[*].catalog_scenario_external_id", contains(expected)));
    }

    @Test
    @DisplayName("finds a scenario by a word of its name")
    void given_textSearch_should_listMatchingScenarios() throws Exception {
      // Arrange
      SearchPaginationInput input = byName(0, 20);
      input.setTextSearch("RANSOM");

      // Act
      ResultActions result = search(SCENARIOS_URI + "/search", input);

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[*].catalog_scenario_external_id", contains("ransomware")));
    }

    @Test
    @DisplayName("pages the scenarios in the order asked")
    void given_secondPage_should_listTheNextScenario() throws Exception {
      // Act
      ResultActions result = search(SCENARIOS_URI + "/search", byName(1, 1));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").value(2))
          .andExpect(jsonPath("$.content[*].catalog_scenario_external_id", contains("ransomware")));
    }

    @Test
    @DisplayName("counts each facet under the other filters only")
    void given_filter_should_countOwnFacetWithoutIt() throws Exception {
      // Act
      ResultActions result =
          search(SCENARIOS_URI + "/facet-counts", filtered(SCENARIO_TYPE_KEY, "CHAINED"));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$." + SCENARIO_TYPE_KEY + ".TIME_BASED").value(1))
          .andExpect(jsonPath("$." + SCENARIO_TYPE_KEY + ".CHAINED").value(1))
          .andExpect(jsonPath("$." + PLATFORMS_KEY + ".Windows").value(1))
          .andExpect(jsonPath("$." + PLATFORMS_KEY + ".MacOS").value(0))
          .andExpect(jsonPath("$." + AUTHOR_KEY + ".Community").value(1));
    }
  }

  // -- ACTIONS --

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Actions")
  class Actions {

    private static final String INTEGRATION_ACTION = "nmap-scan";

    @BeforeEach
    void seedTwoPayloadsAndAnIntegration() {
      seed(
          Item.payload("dump").withDomain("Endpoint").withPlatforms("Windows"),
          Item.payload("scan").withDomain("Network").withPlatforms("Linux"));
      catalogConnectorComposer
          .forCatalogConnector(createCatalogInjectorWithContract("Nmap", INTEGRATION_ACTION))
          .persist();
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
      DOMAIN_KEY + ", Endpoint, dump",
      DOMAIN_KEY + ", Network, scan|" + INTEGRATION_ACTION,
      PLATFORMS_KEY + ", Linux, scan"
    })
    @DisplayName("lists only the payloads and integration actions a filter matches")
    void given_filter_should_listMatchingActions(String key, String value, String expected)
        throws Exception {
      // Act
      ResultActions result = search(ACTIONS_URI + "/search", filtered(key, value));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[*].action_id", contains(expected.split("\\|"))));
    }

    @Test
    @DisplayName("pages the integration actions after the payloads")
    void given_lastPage_should_listTheIntegrationAction() throws Exception {
      // Act
      ResultActions result = search(ACTIONS_URI + "/search", byName(2, 1));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").value(3))
          .andExpect(jsonPath("$.content[*].action_id", contains(INTEGRATION_ACTION)))
          .andExpect(jsonPath("$.content[0].action_kind").value(AvailableActionOutput.INTEGRATION));
    }

    @Test
    @DisplayName("adds the integration actions to the facet counts")
    void given_integrationAction_should_countItWithThePayloads() throws Exception {
      // Act
      ResultActions result = search(ACTIONS_URI + "/facet-counts", byName(0, 20));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$." + DOMAIN_KEY + ".Network").value(2))
          .andExpect(jsonPath("$." + DOMAIN_KEY + ".Endpoint").value(1))
          .andExpect(jsonPath("$." + PLATFORMS_KEY + ".Linux").value(1));
    }
  }
}
