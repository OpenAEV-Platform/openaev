package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;
import static io.openaev.utils.fixtures.CatalogItemFixture.item;
import static io.openaev.utils.fixtures.CatalogItemFixture.release;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogItemFixture.Item;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
 * <p>The catalogue has no parent row that a tenant could be refused first. The Available tab
 * searches read the offered items of the tenant on the path, and the sync reads the last release of
 * a repository by the repository's name alone, so only the scope of the table stands between the
 * caller and the other tenant's release. The other tenant's rows are seeded with raw JDBC and an
 * explicit tenant, which the statement inspector never rewrites.
 *
 * <p>Each test stays on tenant A's path, so the per-request scope is the same for every request.
 */
@CoversTenantIsolation({"catalog_items", "catalog_releases"})
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,CATALOG")
@DisplayName("catalog_items and catalog_releases refuse another tenant's row")
class CatalogHttpIsolationTest extends IntegrationTest {

  private static final String OWN_ITEM = "catalog-iso-own-item";
  private static final String FOREIGN_ITEM = "catalog-iso-foreign-item";

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

    @ParameterizedTest(name = "{0}")
    @CsvSource({"PAYLOAD, /actions/search", "SCENARIO, /scenarios/search"})
    @DisplayName("given an Available tab search, should list its own item and not the other's")
    void given_availableSearch_should_listOwnAndNotOtherTenants(String type, String uri)
        throws Exception {
      // Arrange
      item(entityManager, tenantA, release(entityManager, tenantA, "1.0.0"), of(type, OWN_ITEM));
      item(
          entityManager, tenantB, release(entityManager, tenantB, "1.0.0"), of(type, FOREIGN_ITEM));

      // Act
      String response =
          mvc.perform(
                  post(TENANT_CATALOG_URI + uri, tenantA)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"page\":0,\"size\":20}"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertTrue(response.contains(OWN_ITEM), "A's item must be listed");
      assertFalse(response.contains(FOREIGN_ITEM), "B's item must not be listed");
    }
  }

  @Nested
  @DisplayName("catalog_releases")
  class Releases {

    @Test
    @DisplayName("given its own later release, should refuse an older one")
    void given_ownLaterRelease_should_refuseOlderRelease() throws Exception {
      // Arrange
      release(entityManager, tenantA, "2.0.0");

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
      release(entityManager, tenantB, "2.0.0");

      // Act
      ResultActions result = syncScenarioRelease("1.0.0");

      // Assert
      result.andExpect(status().isOk());
      assertEquals(1L, rawReleaseCount(tenantA, "1.0.0"), "A's release is recorded");
    }
  }

  // -- FIXTURES --

  private static Item of(String type, String externalId) {
    return "PAYLOAD".equals(type) ? Item.payload(externalId) : Item.scenario(externalId);
  }

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
}
