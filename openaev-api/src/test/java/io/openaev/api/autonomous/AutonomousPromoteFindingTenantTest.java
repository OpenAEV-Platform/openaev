package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.security.token.XtmJwksExtractor;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Promoting a finding to an asset reads the finding in the tenant of the run. A finding of another
 * tenant is an unknown id (404 "Finding not found"), so it creates no endpoint and no link, on the
 * tenant path and on the non-prefixed route with {@code X-Tenant-Ids}.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("promoting a finding to an asset reads the finding in the run's tenant")
class AutonomousPromoteFindingTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String PATH = "/{runId}/findings/{findingId}/promote-to-asset";
  private static final String SCOPED = TENANT_PREFIX + "/autonomous-runs" + PATH;
  private static final String PLAIN = AutonomousRunApi.AUTONOMOUS_URI + PATH;
  private static final String VALUE_OF_A = "10.20.30.40";
  private static final String VALUE_OF_B = "10.20.30.41";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String tenantB;
  private String runOfA;
  private String findingOfA;
  private String findingOfB;

  @BeforeEach
  void seedTwoTenantsARunOfAAndOneFindingEach() throws Exception {
    // Arrange
    tenantA = tenantHelper.createTenantWithCurrentUser("promote-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("promote-b").getId();
    assertThat(tenantA).isNotEqualTo(Tenant.DEFAULT_TENANT_UUID);
    assertThat(tenantB).isNotEqualTo(Tenant.DEFAULT_TENANT_UUID);
    runOfA = seedActiveRun(tenantA);
    findingOfA = seedFinding(tenantA, VALUE_OF_A);
    findingOfB = seedFinding(tenantB, VALUE_OF_B);
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    assertThat(rawTenantOf("autonomous_runs", "autonomous_run_id", runOfA)).isEqualTo(tenantA);
    assertThat(rawTenantOf("findings", "finding_id", findingOfA)).isEqualTo(tenantA);
    assertThat(rawTenantOf("findings", "finding_id", findingOfB)).isEqualTo(tenantB);
  }

  private static MockHttpServletRequestBuilder asVerifiedServiceIdentity(
      MockHttpServletRequestBuilder request) {
    return request.requestAttr(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE);
  }

  @Nested
  @DisplayName("X-Tenant-Ids header route")
  class HeaderRoute {

    @Test
    @DisplayName(
        "given_headerNamingTwoTenantsAndAFindingOfTheOther_should_answerFindingNotFoundAndWriteNothing")
    void
        given_headerNamingTwoTenantsAndAFindingOfTheOther_should_answerFindingNotFoundAndWriteNothing()
            throws Exception {
      // Arrange
      assertThat(endpointsNamed(tenantA, VALUE_OF_B)).isZero();

      // Act
      Exception resolved =
          mvc.perform(
                  post(PLAIN, runOfA, findingOfB)
                      .header("X-Tenant-Ids", tenantA + "," + tenantB)
                      .with(csrf()))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResolvedException();

      // Assert
      assertThat(resolved).isInstanceOf(ResponseStatusException.class);
      ResponseStatusException refusal = (ResponseStatusException) resolved;
      assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(refusal.getReason()).isEqualTo("Finding not found");
      assertThat(linksOf(findingOfB)).isZero();
      assertThat(endpointsNamed(tenantA, VALUE_OF_B)).isZero();
    }

    @Test
    @DisplayName("given_headerNamingTwoTenantsAndAnUnknownFindingId_should_answerFindingNotFound")
    void given_headerNamingTwoTenantsAndAnUnknownFindingId_should_answerFindingNotFound()
        throws Exception {
      // Arrange
      String unknownFinding = UUID.randomUUID().toString();
      long linksBefore = allLinks();

      // Act
      Exception resolved =
          mvc.perform(
                  post(PLAIN, runOfA, unknownFinding)
                      .header("X-Tenant-Ids", tenantA + "," + tenantB)
                      .with(csrf()))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResolvedException();

      // Assert
      assertThat(resolved).isInstanceOf(ResponseStatusException.class);
      assertThat(((ResponseStatusException) resolved).getReason()).isEqualTo("Finding not found");
      assertThat(endpointsNamed(tenantA, VALUE_OF_A)).isZero();
      assertThat(endpointsNamed(tenantA, VALUE_OF_B)).isZero();
      assertThat(allLinks()).isEqualTo(linksBefore);
    }

    @Test
    @DisplayName(
        "given_headerNamingTwoTenantsAndAFindingOfTheRun_should_linkItToAnEndpointOfThatTenant")
    void given_headerNamingTwoTenantsAndAFindingOfTheRun_should_linkItToAnEndpointOfThatTenant()
        throws Exception {
      // Act
      mvc.perform(
              post(PLAIN, runOfA, findingOfA)
                  .header("X-Tenant-Ids", tenantA + "," + tenantB)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.finding_id").value(findingOfA));

      // Assert
      assertThat(linksOf(findingOfA)).isEqualTo(1L);
      assertThat(endpointsNamed(tenantA, VALUE_OF_A)).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("verified service identity")
  class ServiceIdentity {

    @Test
    @DisplayName("given_serviceIdentityAndAFindingOfTheRun_should_linkItToAnEndpointOfThatTenant")
    void given_serviceIdentityAndAFindingOfTheRun_should_linkItToAnEndpointOfThatTenant()
        throws Exception {
      // Act
      mvc.perform(asVerifiedServiceIdentity(post(PLAIN, runOfA, findingOfA)).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.finding_id").value(findingOfA));

      // Assert
      assertThat(linksOf(findingOfA)).isEqualTo(1L);
      assertThat(endpointsNamed(tenantA, VALUE_OF_A)).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("tenant path")
  class TenantPath {

    @Test
    @DisplayName("given_tenantPathAndAFindingOfTheRun_should_linkItToAnEndpointOfThatTenant")
    void given_tenantPathAndAFindingOfTheRun_should_linkItToAnEndpointOfThatTenant()
        throws Exception {
      // Act
      mvc.perform(post(SCOPED, tenantA, runOfA, findingOfA).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.finding_id").value(findingOfA));

      // Assert
      assertThat(linksOf(findingOfA)).isEqualTo(1L);
      assertThat(endpointsNamed(tenantA, VALUE_OF_A)).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "given_tenantPathAndAFindingOfAnotherTenant_should_answerFindingNotFoundAndWriteNothing")
    void given_tenantPathAndAFindingOfAnotherTenant_should_answerFindingNotFoundAndWriteNothing()
        throws Exception {
      // Act
      Exception resolved =
          mvc.perform(post(SCOPED, tenantA, runOfA, findingOfB).with(csrf()))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResolvedException();

      // Assert
      assertThat(resolved).isInstanceOf(ResponseStatusException.class);
      assertThat(((ResponseStatusException) resolved).getReason()).isEqualTo("Finding not found");
      assertThat(linksOf(findingOfB)).isZero();
      assertThat(endpointsNamed(tenantA, VALUE_OF_B)).isZero();
    }
  }

  // Native seed: an explicit tenant_id lands the rows without a request scope, and the run is
  // RUNNING so the event the promotion appends is accepted.
  private String seedActiveRun(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                + " autonomous_run_objective, autonomous_run_status)"
                + " VALUES (:id, :tenant, 'Own the domain', 'RUNNING')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedFinding(String tenantId, String value) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO findings (finding_id, tenant_id, finding_field, finding_type,"
                + " finding_value, finding_value_display)"
                + " VALUES (:id, :tenant, 'host', 'IPv4', :value, :value)")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("value", value)
        .executeUpdate();
    return id;
  }

  // Ground truth over plain JDBC on the test's own connection: sees the uncommitted rows and the
  // inspector does not rewrite a statement it never generated.
  private String rawTenantOf(String table, String idColumn, String id) {
    return rawQuery(
        "SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?", id, String.class);
  }

  private long linksOf(String findingId) {
    return rawQuery(
        "SELECT count(*) FROM findings_assets WHERE finding_id = ?", findingId, Long.class);
  }

  private long allLinks() {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                      connection.prepareStatement("SELECT count(*) FROM findings_assets");
                  ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
              }
            });
  }

  private long endpointsNamed(String tenantId, String name) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM assets WHERE tenant_id = ? AND asset_name = ?")) {
                statement.setString(1, tenantId);
                statement.setString(2, name);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private <T> T rawQuery(String sql, String parameter, Class<T> type) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getObject(1, type) : null;
                }
              }
            });
  }
}
