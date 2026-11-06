package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Injector;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.migration.V6_20261103120000000__Add_attackpath_execution_asset_id;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code attackpath_execution_asset_id} is the one asset an execution row is about, the column the
 * marking filter links to {@code assets} (#8555). A database trigger fills it from the source kind,
 * the source asset and the target asset, but only with an asset that exists; the foreign key clears
 * it when the asset is deleted, so the row outlives its asset and stays visible. These tests
 * persist a row of every shape the ingestion writes and read the column back.
 *
 * <p>The rows are built with the entity's own setters, the ones {@code
 * AttackPathExecutionIngestionService} calls, so a shape cannot drift from what the ingestion
 * writes without these tests noticing.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path execution: the asset the row is about")
class AttackPathExecutionAssetIdTest extends IntegrationTest {

  private static final String TEAM_ID = "team-1";

  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private V6_20261103120000000__Add_attackpath_execution_asset_id migration;

  private Tenant tenant;

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  private void ensureTenant() {
    if (tenant == null) {
      tenant = tenantRepository.save(TenantFixture.getTenant("ap-asset-id"));
      TenantContext.setCurrentTenant(tenant.getId());
    }
  }

  /** A real asset: the trigger stores an asset id only when the asset exists. */
  private Endpoint persistedEndpoint(String name) {
    ensureTenant();
    Endpoint endpoint = EndpointFixture.createEndpoint(name);
    endpoint.setTenant(tenant);
    endpointComposer.forEndpoint(endpoint).persist();
    entityManager.flush();
    return endpoint;
  }

  private AttackPathExecution newRow(String id) {
    ensureTenant();
    AttackPathExecution row = new AttackPathExecution();
    row.setId(id);
    row.setTenant(tenant);
    row.setSimulationId("SIM-ASSET-ID");
    row.setExecutedAt(Instant.now());
    return row;
  }

  private static Endpoint unsavedEndpoint(String id) {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(id);
    return endpoint;
  }

  private static Agent agentOn(Endpoint endpoint) {
    Agent agent = new Agent();
    agent.setId("agent-on-" + endpoint.getId());
    agent.setAsset(endpoint);
    agent.setPrivilege(Agent.PRIVILEGE.admin);
    return agent;
  }

  private static Injector injector() {
    Injector injector = new Injector();
    injector.setName("nmap");
    return injector;
  }

  /** Persists the row, then reads back what the database computed for the column. */
  private String assetIdOf(AttackPathExecution row) {
    executionRepository.save(row);
    entityManager.flush();
    return storedAssetId(row);
  }

  private String storedAssetId(AttackPathExecution row) {
    String[] holder = new String[1];
    boolean[] found = new boolean[1];
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT attackpath_execution_asset_id FROM attackpath_execution "
                          + "WHERE attackpath_execution_id = ?")) {
                statement.setString(1, row.getId());
                try (ResultSet result = statement.executeQuery()) {
                  found[0] = result.next();
                  if (found[0]) {
                    holder[0] = result.getString(1);
                  }
                }
              }
            });
    assertThat(found[0]).as("the row was persisted").isTrue();
    return holder[0];
  }

  @Nested
  @DisplayName("agent rows")
  class AgentRows {

    @Test
    @DisplayName("an agent running on an asset is about that asset")
    void given_agentOnItsOwnAsset_should_holdThatAsset() {
      // Arrange
      Endpoint endpoint = persistedEndpoint("agent-own");
      AttackPathExecution row = newRow("ap-asset-id-agent-own");
      row.setSourceAgentInformation(agentOn(endpoint), endpoint);
      row.setTargetAssetInformation(endpoint);

      // Act
      String assetId = assetIdOf(row);

      // Assert
      assertThat(assetId).isEqualTo(endpoint.getId());
    }

    @Test
    @DisplayName("an agent reaching a discovered host is about the asset it runs on")
    void given_agentToDiscoveredHost_should_holdTheSourceAsset() {
      // Arrange
      Endpoint endpoint = persistedEndpoint("agent-discovered");
      AttackPathExecution row = newRow("ap-asset-id-agent-discovered");
      row.setSourceAgentInformation(agentOn(endpoint), endpoint);
      row.setTargetDiscoveredInformation("10.0.0.9");

      // Act
      String assetId = assetIdOf(row);

      // Assert
      assertThat(assetId)
          .as("the discovered host has no asset, the source one hides the row")
          .isEqualTo(endpoint.getId());
    }
  }

  @Nested
  @DisplayName("injector rows")
  class InjectorRows {

    @Test
    @DisplayName("an injector hitting an asset is about the target asset")
    void given_injectorToAsset_should_holdTheTargetAsset() {
      // Arrange
      Endpoint target = persistedEndpoint("injector-target");
      AttackPathExecution row = newRow("ap-asset-id-injector-asset");
      row.setSourceInjectorInformation(injector());
      row.setTargetAssetInformation(target);

      // Act
      String assetId = assetIdOf(row);

      // Assert
      assertThat(assetId).isEqualTo(target.getId());
    }

    @Test
    @DisplayName("an injector reaching a discovered host is about no asset")
    void given_injectorToDiscoveredHost_should_holdNoAsset() {
      // Arrange
      AttackPathExecution row = newRow("ap-asset-id-injector-discovered");
      row.setSourceInjectorInformation(injector());
      row.setTargetDiscoveredInformation("10.0.0.9");

      // Act
      String assetId = assetIdOf(row);

      // Assert
      assertThat(assetId).isNull();
    }
  }

  @Nested
  @DisplayName("team and player rows")
  class TeamRows {

    @Test
    @DisplayName("an injector targeting a team is about no asset")
    void given_injectorToTeam_should_holdNoAsset() {
      // Arrange
      AttackPathExecution row = newRow("ap-asset-id-injector-team");
      row.setSourceInjectorInformation(injector());
      row.setTargetTeamInformation(TEAM_ID, "Blue team");

      // Act
      String assetId = assetIdOf(row);

      // Assert
      assertThat(assetId).isNull();
    }

    @Test
    @DisplayName(
        "a team row keeps its team id out of the column although it sits in source_asset_id")
    void given_teamToPlayer_should_holdNoAsset() {
      // Arrange
      AttackPathExecution row = newRow("ap-asset-id-team-player");
      row.setSourceTeamInformation(TEAM_ID, "Blue team");
      row.setTargetPersonInformation("user-1", "player@filigran.io");

      // Act
      String assetId = assetIdOf(row);

      // Assert
      assertThat(row.getSourceAssetId())
          .as("the team id rides the source asset slot")
          .isEqualTo(TEAM_ID);
      assertThat(assetId)
          .as("a team id matches no asset: a link on it would hide the row from every reader")
          .isNull();
    }
  }

  @Nested
  @DisplayName("rows with two different assets")
  class TwoAssets {

    @Test
    @DisplayName("the source asset wins, a shape only the demo seeder writes")
    void given_agentPivotBetweenTwoAssets_should_holdTheSourceAsset() {
      // Arrange
      Endpoint source = persistedEndpoint("pivot-source");
      Endpoint target = persistedEndpoint("pivot-target");
      AttackPathExecution row = newRow("ap-asset-id-pivot");
      row.setSourceAgentInformation(agentOn(source), source);
      row.setTargetAssetInformation(target);

      // Act
      String assetId = assetIdOf(row);

      // Assert
      assertThat(assetId).isEqualTo(source.getId());
    }
  }

  @Nested
  @DisplayName("an asset that does not exist or no longer exists")
  class MissingAsset {

    @Test
    @DisplayName("an id with no asset behind it (a seeded demo host) is about no asset")
    void given_unknownAssetId_should_holdNoAsset() {
      // Arrange
      AttackPathExecution row = newRow("ap-asset-id-unknown");
      row.setSourceInjectorInformation(injector());
      row.setTargetAssetInformation(unsavedEndpoint("ep-dc-01"));

      // Act
      String assetId = assetIdOf(row);

      // Assert
      assertThat(row.getTargetAssetId()).isEqualTo("ep-dc-01");
      assertThat(assetId)
          .as("a missing asset must not break the foreign key, the row stays visible")
          .isNull();
    }

    @Test
    @DisplayName("deleting the asset keeps the row and clears the link, so the row stays visible")
    void given_deletedAsset_should_keepTheRowAndClearTheLink() {
      // Arrange
      Endpoint endpoint = persistedEndpoint("to-delete");
      AttackPathExecution row = newRow("ap-asset-id-deleted");
      row.setSourceInjectorInformation(injector());
      row.setTargetAssetInformation(endpoint);
      assertThat(assetIdOf(row)).isEqualTo(endpoint.getId());

      // Act
      entityManager
          .createNativeQuery("DELETE FROM assets WHERE asset_id = :id")
          .setParameter("id", endpoint.getId())
          .executeUpdate();

      // Assert
      assertThat(storedAssetId(row))
          .as("the foreign key clears the link and the trigger does not put it back")
          .isNull();
      assertThat(row.getTargetAssetId())
          .as("the frozen copy keeps the id it was written with")
          .isEqualTo(endpoint.getId());
    }
  }

  @Nested
  @DisplayName("migration")
  class Migration {

    @Test
    @DisplayName("the column is a plain one, whose foreign key clears it on asset delete")
    void given_migratedSchema_should_haveAForeignKeyThatSetsNull() {
      // Arrange
      String[] found = new String[2];

      // Act
      entityManager
          .unwrap(Session.class)
          .doWork(
              connection -> {
                try (PreparedStatement statement =
                        connection.prepareStatement(
                            "SELECT is_generated FROM information_schema.columns "
                                + "WHERE table_name = 'attackpath_execution' "
                                + "AND column_name = 'attackpath_execution_asset_id'");
                    ResultSet result = statement.executeQuery()) {
                  found[0] = result.next() ? result.getString(1) : null;
                }
                try (PreparedStatement statement =
                        connection.prepareStatement(
                            "SELECT delete_rule FROM information_schema.referential_constraints "
                                + "WHERE constraint_name = 'fk_attackpath_execution_asset'");
                    ResultSet result = statement.executeQuery()) {
                  found[1] = result.next() ? result.getString(1) : null;
                }
              });

      // Assert
      assertThat(found[0]).isEqualTo("NEVER");
      assertThat(found[1]).isEqualTo("SET NULL");
    }

    @Test
    @DisplayName("re-running the migration is a no-op")
    void given_appliedMigration_should_beIdempotent() {
      // Act / Assert
      assertThatCode(
              () ->
                  entityManager
                      .unwrap(Session.class)
                      .doWork(
                          connection -> {
                            try {
                              migration.migrate(
                                  new Context() {
                                    @Override
                                    public Configuration getConfiguration() {
                                      return null;
                                    }

                                    @Override
                                    public java.sql.Connection getConnection() {
                                      return connection;
                                    }
                                  });
                            } catch (Exception e) {
                              throw new RuntimeException(e);
                            }
                          }))
          .doesNotThrowAnyException();
    }
  }
}
