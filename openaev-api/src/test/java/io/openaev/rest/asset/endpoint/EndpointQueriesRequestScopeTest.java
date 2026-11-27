package io.openaev.rest.asset.endpoint;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The endpoint option lists and the endpoint delete read the tenants of the request's scope, on the
 * tenant-prefixed route and on the plain route with {@code X-Tenant-Ids}. On the plain route the
 * ambient tenant is the default one while the scope is tenant A, so a query that filters on the
 * ambient tenant answers the default tenant's rows, or none, and not A's.
 *
 * <p>One request per test: the first request sets the transaction's tenant scope. The fixture uses
 * single native INSERTs on the active tables, as in {@code SnapshotObservationTenantScopeTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Endpoint queries read the request's tenant scope")
class EndpointQueriesRequestScopeTest extends IntegrationTest {

  private static final String TENANT_ENDPOINT_URI = TENANT_PREFIX + "/endpoints";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;

  private String tenantA;
  private String tenantB;

  /** The two routes a caller reaches tenant A's endpoints by. */
  enum Route {
    TENANT_PREFIXED,
    TENANT_HEADER
  }

  @BeforeEach
  void createTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("endpoint-scope-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("endpoint-scope-b").getId();
  }

  @Nested
  @DisplayName("Option lists")
  class OptionLists {

    @ParameterizedTest
    @EnumSource(Route.class)
    @DisplayName(
        "given an endpoint targeted by an inject in each tenant, should list tenant A's endpoint"
            + " only")
    void given_targetedEndpointInEachTenant_should_listTenantAEndpointOnly(Route route)
        throws Exception {
      // Arrange
      String endpointA = seedEndpoint(tenantA);
      String endpointB = seedEndpoint(tenantB);
      targetWithInject(tenantA, endpointA);
      targetWithInject(tenantB, endpointB);

      // Act
      String response =
          perform(route, get(uri(route, "/options")).param("inputFilterOption", "ALL_INJECTS"));

      // Assert
      assertThat(response).contains(endpointA).doesNotContain(endpointB);
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    @DisplayName(
        "given an endpoint linked to a finding in each tenant, should list tenant A's endpoint"
            + " only")
    void given_endpointWithFindingInEachTenant_should_listTenantAEndpointOnly(Route route)
        throws Exception {
      // Arrange
      String endpointA = seedEndpoint(tenantA);
      String endpointB = seedEndpoint(tenantB);
      linkToFinding(tenantA, endpointA);
      linkToFinding(tenantB, endpointB);

      // Act
      String response = perform(route, get(uri(route, "/findings/options")));

      // Assert
      assertThat(response).contains(endpointA).doesNotContain(endpointB);
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    @DisplayName(
        "given two endpoints of tenant A sharing a finding, should list the other one from the"
            + " first")
    void given_twoEndpointsSharingAFinding_should_listTheOtherEndpointFromTheFirst(Route route)
        throws Exception {
      // Arrange
      String source = seedEndpoint(tenantA);
      String sibling = seedEndpoint(tenantA);
      String findingId = linkToFinding(tenantA, source);
      linkAsset(findingId, sibling);

      // Act
      String response =
          perform(route, get(uri(route, "/findings/options")).param("sourceId", source));

      // Assert
      assertThat(response).contains(sibling).doesNotContain(source);
    }
  }

  @Nested
  @DisplayName("Delete")
  class Delete {

    @ParameterizedTest
    @EnumSource(Route.class)
    @DisplayName("given an endpoint of tenant A, should delete it")
    void given_endpointOfTenantA_should_deleteIt(Route route) throws Exception {
      // Arrange
      String endpointA = seedEndpoint(tenantA);

      // Act
      perform(route, delete(uri(route, "/" + endpointA)));

      // Assert
      assertThat(assetExists(endpointA)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    @DisplayName(
        "given an endpoint of tenant B under tenant A's scope, should answer 404 and leave it in"
            + " place")
    void given_endpointOfTenantBUnderTenantAScope_should_answerNotFoundAndLeaveItInPlace(
        Route route) throws Exception {
      // Arrange
      String endpointB = seedEndpoint(tenantB);

      // Act
      int status = requestUnderTenantA(route, delete(uri(route, "/" + endpointB)));

      // Assert
      assertThat(status).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(assetExists(endpointB)).isTrue();
    }
  }

  // -- Requests --

  private String uri(Route route, String suffix) {
    return switch (route) {
      case TENANT_PREFIXED -> TENANT_ENDPOINT_URI.replace("{tenantId}", tenantA) + suffix;
      case TENANT_HEADER -> ENDPOINT_URI + suffix;
    };
  }

  private String perform(Route route, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(withTenantA(route, request))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private int requestUnderTenantA(Route route, MockHttpServletRequestBuilder request)
      throws Exception {
    return mvc.perform(withTenantA(route, request)).andReturn().getResponse().getStatus();
  }

  /**
   * The fixtures leave an ambient tenant on the test thread; production has none before a request,
   * so the header route starts from the default tenant's fallback, as it does in production.
   */
  private MockHttpServletRequestBuilder withTenantA(
      Route route, MockHttpServletRequestBuilder request) {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    if (route == Route.TENANT_HEADER) {
      request.header("X-Tenant-Ids", tenantA);
    }
    return request.with(csrf()).accept(MediaType.APPLICATION_JSON);
  }

  // -- Fixture --

  private String seedEndpoint(String tenantId) {
    String assetId = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at,"
                + " asset_updated_at, tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
                + " VALUES (:id, :name, 'Endpoint', now(), now(), :tenantId, :name, 'Linux',"
                + " 'x86_64')")
        .setParameter("id", assetId)
        .setParameter("name", "endpoint-scope-" + assetId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    return assetId;
  }

  private String seedInject(String tenantId) {
    // injects is not an active table: the entity path is fine for it.
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTenant(entityManager.getReference(Tenant.class, tenantId));
    entityManager.persist(inject);
    entityManager.flush();
    return inject.getId();
  }

  private void targetWithInject(String tenantId, String assetId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO injects_assets (inject_id, asset_id) VALUES (:injectId, :assetId)")
        .setParameter("injectId", seedInject(tenantId))
        .setParameter("assetId", assetId)
        .executeUpdate();
  }

  /** A finding of {@code tenantId} on {@code assetId}, with an occurrence on an inject. */
  private String linkToFinding(String tenantId, String assetId) {
    String injectId = seedInject(tenantId);
    String findingId = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
                + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
                + " VALUES (:id, 'cve', 'CVE', :value, :injectId, now(), now(), :tenantId)")
        .setParameter("id", findingId)
        .setParameter("value", "CVE-" + UUID.randomUUID())
        .setParameter("injectId", injectId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    entityManager
        .createNativeQuery(
            "INSERT INTO finding_occurrences (finding_occurrence_id, finding_occurrence_finding_id,"
                + " finding_occurrence_inject_id, finding_occurrence_date)"
                + " VALUES (:id, :findingId, :injectId, now())")
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("findingId", findingId)
        .setParameter("injectId", injectId)
        .executeUpdate();
    linkAsset(findingId, assetId);
    return findingId;
  }

  private void linkAsset(String findingId, String assetId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO findings_assets (finding_id, asset_id) VALUES (:findingId, :assetId)")
        .setParameter("findingId", findingId)
        .setParameter("assetId", assetId)
        .executeUpdate();
  }

  /**
   * Plain JDBC on the test transaction's connection: the statement inspector does not rewrite it,
   * so it sees the row whatever the request's scope.
   */
  private boolean assetExists(String assetId) {
    Integer count =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM assets WHERE asset_id = ?", Integer.class, assetId);
    return count != null && count == 1;
  }
}
