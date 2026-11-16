package io.openaev.rest.asset.endpoint;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.raw.RawGrant;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.engine.EngineContext;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.ListRuntime;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.EsBase;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.Pagination;
import io.openaev.utilstest.RabbitMQTestListener;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The endpoint delete publishes its index delete event only when its scoped DELETE removed a row.
 *
 * <p>Not {@code @Transactional}: the rows are committed and the engine flush runs after commit
 * inside the request, against the real search engine of the test stack. Documents are looked up by
 * the endpoint id, never counted, because the engine is shared with other test classes.
 */
@DisplayName("Endpoint delete and its index delete event")
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class EndpointDeleteIndexEventTest extends ProductionTablesIntegrationTest {

  private static final String TENANT_ENDPOINT_URI = TENANT_PREFIX + "/endpoints";

  private static final RawUserAuth INDEX_READER =
      new RawUserAuth() {
        @Override
        public String getUser_id() {
          return "index-reader-" + UUID.randomUUID();
        }

        @Override
        public boolean getUser_admin() {
          return true;
        }

        @Override
        public Set<RawGrant> getUser_grants() {
          return Set.of();
        }
      };

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;
  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private TenantScopedTransaction tenantTx;

  private String tenantA;
  private String tenantB;
  private String endpointB;

  enum Route {
    TENANT_PREFIXED,
    HEADER
  }

  @AfterEach
  void removeWhatTheTestCreated() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    if (endpointB != null) {
      jdbc.update("DELETE FROM assets WHERE asset_id = ?", endpointB);
      engineService.bulkDelete(List.of(endpointB));
      jdbc.update("DELETE FROM engine_deletions WHERE deletion_id = ?", endpointB);
    }
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  private void givenTwoTenantsAndIndexedEndpointOfB(boolean callerIsMemberOfB) throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("endpoint-del-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("endpoint-del-b").getId();
    if (!callerIsMemberOfB) {
      tenantHelper.detachCurrentUserFromTenant(tenantB);
    }
    endpointB = seedEndpoint(tenantB);
    tenantTx.execute(
        TxCtx.allTenants(), () -> engineService.bulkProcessing(engineContext.getModels().stream()));
    await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(isIndexed(TxCtx.allTenants())).as("endpoint indexed").isTrue();
              assertThat(isIndexed(TxCtx.forTenant(tenantB))).as("visible to B").isTrue();
            });
  }

  @Nested
  @DisplayName("Delete of an id that matches no row of the request scope")
  class UnmatchedId {

    @ParameterizedTest
    @EnumSource(Route.class)
    @WithMockUser(isAdmin = true)
    @DisplayName("given an admin of A, should leave the row and the document in place")
    void given_adminOfA_should_leaveRowAndDocumentInPlace(Route route) throws Exception {
      // Arrange
      givenTwoTenantsAndIndexedEndpointOfB(false);

      // Act
      int status = deleteAsCallerOfA(route);

      // Assert
      assertThat(isIndexed(TxCtx.allTenants())).as("document").isTrue();
      assertThat(isIndexed(TxCtx.forTenant(tenantB))).as("document seen by a member of B").isTrue();
      assertThat(rowExists()).as("row").isTrue();
      assertThat(status).as("HTTP status").isEqualTo(404);
    }

    @Test
    @WithMockUser
    @DisplayName(
        "given a holder of the asset delete right in A, should leave the row and the document in"
            + " place")
    void given_holderOfDeleteRightInA_should_leaveRowAndDocumentInPlace() throws Exception {
      // Arrange
      givenTwoTenantsAndIndexedEndpointOfB(false);
      tenantHelper.grantCapabilitiesInTenant(
          tenantA,
          Set.of(Capability.ACCESS_ASSETS, Capability.MANAGE_ASSETS, Capability.DELETE_ASSETS));

      // Act
      int status = deleteAsCallerOfA(Route.TENANT_PREFIXED);

      // Assert
      assertThat(isIndexed(TxCtx.allTenants())).as("document").isTrue();
      assertThat(isIndexed(TxCtx.forTenant(tenantB))).as("document seen by a member of B").isTrue();
      assertThat(rowExists()).as("row").isTrue();
      assertThat(status).as("HTTP status").isEqualTo(404);
    }
  }

  @Nested
  @DisplayName("Delete of an id that matches a row of the request scope")
  class MatchedId {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "given a member of B deleting an endpoint of B, should remove the row and the document")
    void given_memberOfB_should_removeRowAndDocument() throws Exception {
      // Arrange
      givenTwoTenantsAndIndexedEndpointOfB(true);

      // Act
      TenantContext.clearCurrentTenant();
      int status =
          mvc.perform(
                  delete(TENANT_ENDPOINT_URI.replace("{tenantId}", tenantB) + "/" + endpointB)
                      .with(csrf())
                      .accept(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(status).as("HTTP status").isEqualTo(200);
      assertThat(rowExists()).as("row").isFalse();
      assertThat(isIndexed(TxCtx.allTenants())).as("document").isFalse();
    }
  }

  private int deleteAsCallerOfA(Route route) throws Exception {
    TenantContext.clearCurrentTenant();
    MockHttpServletRequestBuilder request =
        switch (route) {
          case TENANT_PREFIXED ->
              delete(TENANT_ENDPOINT_URI.replace("{tenantId}", tenantA) + "/" + endpointB);
          case HEADER -> delete(ENDPOINT_URI + "/" + endpointB).header("X-Tenant-Ids", tenantA);
        };
    return mvc.perform(request.with(csrf()).accept(MediaType.APPLICATION_JSON))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  /** Plain JDBC: committed and aged, so the indexer's grace window does not re-fetch it. */
  private String seedEndpoint(String tenantId) {
    String assetId = UUID.randomUUID().toString();
    new JdbcTemplate(dataSource)
        .update(
            "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at,"
                + " asset_updated_at, tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
                + " VALUES (?, ?, 'Endpoint', now() - interval '1 day', now() - interval '1 day',"
                + " ?, ?, 'Linux', 'x86_64')",
            assetId,
            "endpoint-del-" + assetId,
            tenantId,
            "endpoint-del-" + assetId);
    return assetId;
  }

  private boolean isIndexed(TxCtx ctx) {
    ListConfiguration config = engineService.createListConfiguration("asset", Map.of());
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    ListRuntime runtime = new ListRuntime(config, Map.of(), Map.of(), new Pagination(0, 5000));
    return engineService.entities(ctx, INDEX_READER, runtime).getEsDatas().stream()
        .map(EsBase::getBase_id)
        .anyMatch(endpointB::equals);
  }

  private boolean rowExists() {
    Integer count =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM assets WHERE asset_id = ?", Integer.class, endpointB);
    return count != null && count == 1;
  }
}
