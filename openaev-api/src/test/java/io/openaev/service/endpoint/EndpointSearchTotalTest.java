package io.openaev.service.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.TenantRepository;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-regression tests for the endpoint search totals.
 *
 * <p>The endpoint specifications used to join on agents and deduplicate with a {@code GROUP BY}.
 * Spring Data applies the specification to the count query too and cannot handle a grouped count:
 * it sums the returned rows, so the total counted agents instead of endpoints. The bug was only
 * visible on pages that are full (Spring short-circuits the count with {@code offset + size} on a
 * partial page), hence a total that changed from one page to another.
 */
@Transactional
@WithMockUser(isAdmin = true)
class EndpointSearchTotalTest extends IntegrationTest {

  private static final String NAME_PREFIX = "search-total-";

  @Autowired private EndpointService endpointService;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private ExecutorComposer executorComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    endpointComposer.reset();
    agentComposer.reset();
    assetGroupComposer.reset();
    executorComposer.reset();

    executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
    if (testUserHolder.get() != null) {
      tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    }
  }

  /** Persists an endpoint carrying {@code agentCount} injectable agents (no parent, no inject). */
  private EndpointComposer.Composer persistEndpointWithAgents(String name, int agentCount) {
    Endpoint endpoint = EndpointFixture.createEndpoint(NAME_PREFIX + name);
    EndpointComposer.Composer composer = endpointComposer.forEndpoint(endpoint);
    for (int i = 0; i < agentCount; i++) {
      Agent agent = AgentFixture.createDefaultAgentService();
      // Distinct executedByUser keeps each agent a separate row on the same endpoint
      agent.setExecutedByUser(name + "-user-" + i);
      composer.withAgent(
          agentComposer
              .forAgent(agent)
              .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor())));
    }
    return composer.persist();
  }

  private SearchPaginationInput searchPage(int page, int size) {
    return PaginationFixture.getDefault().textSearch(NAME_PREFIX).page(page).size(size).build();
  }

  @Nested
  @DisplayName("Endpoint search total")
  class EndpointSearchTotal {

    @Test
    @DisplayName("Given endpoints with several agents, should count endpoints and not agents")
    void given_endpointsWithSeveralAgents_should_countEndpointsAndNotAgents() {
      // -- PREPARE --
      persistEndpointWithAgents("multi", 3); // would have counted 3 before the fix
      persistEndpointWithAgents("single", 1);
      persistEndpointWithAgents("agentless", 0);
      entityManager.flush();
      entityManager.clear();

      // -- EXECUTE --
      Page<Endpoint> page = endpointService.searchEndpoints(searchPage(0, 100));

      // -- ASSERT --
      assertThat(page.getTotalElements()).isEqualTo(3);
      assertThat(page.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("Given a full first page, should advertise the same total on every page")
    void given_fullFirstPage_should_advertiseSameTotalOnEveryPage() {
      // -- PREPARE --
      // A full page forces Spring Data to run the real count query (it does not short-circuit
      // with offset + content size), which is where the inflated total used to show up.
      persistEndpointWithAgents("multi", 3);
      persistEndpointWithAgents("single", 1);
      persistEndpointWithAgents("agentless", 0);
      entityManager.flush();
      entityManager.clear();

      // -- EXECUTE --
      Page<Endpoint> firstPage = endpointService.searchEndpoints(searchPage(0, 2));
      Page<Endpoint> secondPage = endpointService.searchEndpoints(searchPage(1, 2));

      // -- ASSERT --
      assertThat(firstPage.getTotalElements()).isEqualTo(3);
      assertThat(secondPage.getTotalElements()).isEqualTo(firstPage.getTotalElements());
      assertThat(firstPage.getContent()).hasSize(2);
      assertThat(secondPage.getContent()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Endpoint search total by asset group")
  class EndpointSearchTotalByAssetGroup {

    @Test
    @DisplayName("Given an endpoint both static and dynamic member, should not count it twice")
    void given_endpointBothStaticAndDynamicMember_should_notCountItTwice() {
      // -- PREPARE --
      EndpointComposer.Composer staticMember = persistEndpointWithAgents("static-member", 2);
      EndpointComposer.Composer dynamicMember = persistEndpointWithAgents("dynamic-member", 1);

      // The dynamic filter matches BOTH endpoints, one of which is also a static member
      Filters.Filter filter = new Filters.Filter();
      filter.setKey("asset_name");
      filter.setOperator(Filters.FilterOperator.contains);
      filter.setValues(List.of(NAME_PREFIX));
      Filters.FilterGroup dynamicFilter = new Filters.FilterGroup();
      dynamicFilter.setMode(Filters.FilterMode.and);
      dynamicFilter.setFilters(new ArrayList<>(List.of(filter)));

      AssetGroup assetGroup =
          AssetGroupFixture.createAssetGroupWithDynamicFilter(NAME_PREFIX + "group", dynamicFilter);
      assetGroupComposer.forAssetGroup(assetGroup).withAsset(staticMember).persist();
      entityManager.flush();
      entityManager.clear();

      // -- EXECUTE --
      Page<Endpoint> page =
          endpointService.searchManagedEndpointsByAssetGroup(
              assetGroup.getId(), searchPage(0, 100));

      // -- ASSERT --
      assertThat(page.getTotalElements()).isEqualTo(2);
      assertThat(page.getContent())
          .extracting(Endpoint::getId)
          .containsExactlyInAnyOrder(staticMember.get().getId(), dynamicMember.get().getId());
    }
  }
}
