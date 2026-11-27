package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Dynamic asset group membership")
class AssetGroupDynamicMembershipTest extends ProductionTablesIntegrationTest {

  @Autowired private AssetGroupService assetGroupService;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutorComposer executorComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EntityManager entityManager;

  private Endpoint windowsEndpoint;
  private AssetGroup windowsGroup;
  private AssetGroup linuxGroup;

  @BeforeEach
  void setUp() {
    endpointComposer.reset();
    agentComposer.reset();
    executorComposer.reset();
    assetGroupComposer.reset();
    windowsEndpoint =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(
                agentComposer
                    .forAgent(AgentFixture.createDefaultAgentService())
                    .withExecutor(
                        executorComposer.forExecutor(executorFixture.getDefaultExecutor())))
            .persist()
            .get();
    windowsGroup = persistGroupOnPlatform("Windows hosts", Endpoint.PLATFORM_TYPE.Windows);
    linuxGroup = persistGroupOnPlatform("Linux hosts", Endpoint.PLATFORM_TYPE.Linux);
    entityManager.flush();
    entityManager.clear();
  }

  private AssetGroup persistGroupOnPlatform(String name, Endpoint.PLATFORM_TYPE platform) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("endpoint_platform");
    filter.setMode(Filters.FilterMode.and);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(List.of(platform.name()));
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(List.of(filter));
    return assetGroupComposer
        .forAssetGroup(AssetGroupFixture.createAssetGroupWithDynamicFilter(name, filterGroup))
        .persist()
        .get();
  }

  @Nested
  @DisplayName("Memberships of a set of assets")
  class MembershipsOfAssets {

    @Test
    @DisplayName("Resolves the matching dynamic groups without loading the endpoints")
    void given_endpointInADynamicGroup_should_resolveItsGroupsWithoutLoadingEndpoints() {
      // Arrange
      Statistics stats =
          entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
      // The statistics belong to the session factory shared by every test: put them back as found
      boolean statisticsWereEnabled = stats.isStatisticsEnabled();
      stats.setStatisticsEnabled(true);
      Map<String, Set<String>> memberships;
      long endpointLoads;
      long agentLoads;
      try {
        stats.clear();

        // Act
        memberships = assetGroupService.assetGroupIdsByAssetId(List.of(windowsEndpoint.getId()));
        endpointLoads = stats.getEntityStatistics(Endpoint.class.getName()).getLoadCount();
        agentLoads = stats.getEntityStatistics(Agent.class.getName()).getLoadCount();
      } finally {
        stats.setStatisticsEnabled(statisticsWereEnabled);
      }

      // Assert
      assertThat(memberships)
          .containsEntry(windowsEndpoint.getId(), Set.of(windowsGroup.getId()))
          .hasSize(1);
      assertThat(endpointLoads).isZero();
      assertThat(agentLoads).isZero();
    }
  }

  @Nested
  @DisplayName("Groups of one asset")
  class GroupsOfOneAsset {

    @Test
    @DisplayName("Lists the dynamic groups whose filter matches the endpoint, and only those")
    void given_endpointMatchingOneOfTwoDynamicGroups_should_listOnlyTheMatchingGroup() {
      // Arrange
      Endpoint endpoint = entityManager.find(Endpoint.class, windowsEndpoint.getId());

      // Act
      List<AssetGroup> groups = assetGroupService.assetGroupsOfAsset(endpoint);

      // Assert
      assertThat(groups)
          .extracting(AssetGroup::getId)
          .contains(windowsGroup.getId())
          .doesNotContain(linuxGroup.getId());
    }
  }
}
