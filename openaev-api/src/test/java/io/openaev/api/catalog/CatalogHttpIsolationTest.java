package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.REPOSITORY;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.archive;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioItem;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.syncRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolation of {@code catalog_items} and {@code catalog_releases} on their own rows, through the
 * real {@link CatalogApi} and {@link CatalogSyncApi} endpoints on the tenant path.
 *
 * <p>The catalogue has no parent row that a tenant could be refused first. The payload list reads
 * every catalogue payload with no tenant predicate and no join, and the sync reads the last release
 * of a repository by the repository's name alone, so only the scope of the table each one reads
 * stands between the caller and the other tenant's row. The other tenant's rows are seeded with raw
 * JDBC and an explicit tenant, which the statement inspector never rewrites.
 *
 * <p>Each test stays on tenant A's path, so the per-request scope is the same for every request.
 */
@CoversTenantIsolation({"catalog_items", "catalog_releases"})
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,CATALOG")
@DisplayName("catalog_items and catalog_releases refuse another tenant's row")
class CatalogHttpIsolationTest extends IntegrationTest {

  private static final String OWN_PAYLOAD = "catalog-iso-own-payload";
  private static final String FOREIGN_PAYLOAD = "catalog-iso-foreign-payload";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("catalog-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("catalog-iso-b").getId();
  }

  @Nested
  @DisplayName("catalog_items")
  class Items {

    @Test
    @DisplayName("given the payload list, should list its own payload and not the other's")
    void given_payloadList_should_listOwnAndNotOtherTenants() throws Exception {
      // Arrange
      seedPayload(tenantA, seedRelease(tenantA, "1.0.0"), OWN_PAYLOAD);
      seedPayload(tenantB, seedRelease(tenantB, "1.0.0"), FOREIGN_PAYLOAD);

      // Act
      String response =
          mvc.perform(get(TENANT_CATALOG_URI + "/payloads", tenantA))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertTrue(response.contains(OWN_PAYLOAD), "A's payload must be listed");
      assertFalse(response.contains(FOREIGN_PAYLOAD), "B's payload must not be listed");
    }
  }

  @Nested
  @DisplayName("catalog_releases")
  class Releases {

    @Test
    @DisplayName("given its own later release, should refuse an older one")
    void given_ownLaterRelease_should_refuseOlderRelease() throws Exception {
      // Arrange
      seedRelease(tenantA, "2.0.0");

      // Act
      ResultActions result = syncScenarioRelease("1.0.0");

      // Assert
      result.andExpect(status().isBadRequest());
      assertEquals(0L, rawReleaseCount(tenantA, "1.0.0"), "A's older release is not recorded");
    }

    @Test
    @DisplayName("given the other tenant's later release, should apply an older one")
    void given_otherTenantsLaterRelease_should_applyOlderRelease() throws Exception {
      // Arrange
      seedRelease(tenantB, "2.0.0");

      // Act
      ResultActions result = syncScenarioRelease("1.0.0");

      // Assert
      result.andExpect(status().isOk());
      assertEquals(1L, rawReleaseCount(tenantA, "1.0.0"), "A's release is recorded");
    }
  }

  // -- FIXTURES --

  // A release of one scenario version, sent by the collector on tenant A's path.
  private ResultActions syncScenarioRelease(String release) throws Exception {
    byte[] scenario = archive("{\"release\":\"" + release + "\"}");
    byte[] manifest =
        mapper.writeValueAsBytes(
            manifest(release, List.of(scenarioItem(release, "VERIFIED", scenario)), List.of()));
    return mvc.perform(
        syncRequest(
                manifest,
                Map.of(scenarioFileName(release), scenario),
                TENANT_CATALOG_URI + "/sync",
                tenantA)
            .with(csrf()));
  }

  private String seedRelease(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO catalog_releases (catalog_release_id, catalog_release_repository,"
            + " catalog_release_name, tenant_id) VALUES (?, ?, ?, ?)",
        id,
        REPOSITORY,
        name,
        tenantId);
    return id;
  }

  private void seedPayload(String tenantId, String releaseId, String externalId) {
    execute(
        "INSERT INTO catalog_items (catalog_item_id, catalog_item_type, catalog_item_external_id,"
            + " catalog_item_version, catalog_item_status, catalog_item_name,"
            + " catalog_item_archive_name, catalog_item_archive_hash, catalog_item_release_id,"
            + " tenant_id) VALUES (?, 'PAYLOAD', ?, '1.0.0', 'VERIFIED', ?, ?, ?, ?, ?)",
        UUID.randomUUID().toString(),
        externalId,
        externalId,
        externalId + ".json",
        "a".repeat(64),
        releaseId,
        tenantId);
  }

  // Ground truth, scope-free: raw JDBC on the test's own connection sees the uncommitted rows and
  // the inspector never rewrites a statement it did not generate.
  private long rawReleaseCount(String tenantId, String name) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM catalog_releases WHERE tenant_id = ?"
                          + " AND catalog_release_repository = ? AND catalog_release_name = ?")) {
                statement.setString(1, tenantId);
                statement.setString(2, REPOSITORY);
                statement.setString(3, name);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private void execute(String sql, String... parameters) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                statement.executeUpdate();
              }
            });
  }
}
