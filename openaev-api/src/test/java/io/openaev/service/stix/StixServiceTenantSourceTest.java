package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant a security coverage row is attributed to is the tenant its transaction is scoped to,
 * and a scope that names anything but one tenant is refused before any row is written.
 *
 * <p>These call the service directly rather than through {@code StixApi}, which resolves the tenant
 * from the scope itself and would refuse first. That is the point: the guard has to hold for the
 * next caller, not only for the one endpoint that happens to check today.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=security_coverages")
@WithMockUser(isAdmin = true)
@DisplayName("STIX bundle processing takes its tenant from the transaction scope")
class StixServiceTenantSourceTest extends IntegrationTest {

  @Autowired private StixService stixService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ObjectMapper mapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  // Network boundary only: the real bean binds connectors at context startup, so a tenant seeded
  // with a random id can never have one. Not part of what is asserted here.
  @MockitoBean private OpenCTIConnectorService openCTIConnectorService;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("stix-tenant-source-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("stix-tenant-source-b").getId();
    TenantContext.setCurrentTenant(tenantA);

    ConnectorBase connectorBase = mock(ConnectorBase.class);
    when(connectorBase.getUrl()).thenReturn("http://localhost/opencti");
    when(openCTIConnectorService.getConnectorBase(anyString()))
        .thenReturn(Optional.of(connectorBase));
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("a bundle processed under tenant A's scope writes the coverage row in tenant A")
  void given_scopeOnTenantA_should_writeCoverageRowInTenantA() throws Exception {
    String externalId = "security-coverage--" + UUID.randomUUID();
    // Deliberately point the ambient thread-local at B while the transaction is scoped to A. In
    // production the two always agree, so a test where they do cannot tell which one the row's
    // tenant was read from. Splitting them makes the scope the only possible source of a pass.
    TenantContext.setCurrentTenant(tenantB);

    stixService.processBundle(TxCtx.forTenant(tenantA), bundleWithExternalId(externalId));

    assertThat(tenantsOwning(externalId))
        .as(
            "the coverage must belong to the tenant the transaction was scoped to, not the ambient one")
        .containsExactly(tenantA);
    // The scenario is a still-v1 row, stamped by TenantBaseListener from TenantContext. Without the
    // bridge it took the ambient tenant and the bundle split across two tenants: a v2 coverage in A
    // owning a v1 scenario in B.
    assertThat(tenantsOwningScenarioOf(externalId))
        .as("everything derived from the coverage must land in the same tenant as the coverage")
        .containsExactly(tenantA);
  }

  @Test
  @DisplayName("a bundle processed with no scope is refused instead of writing an unscoped row")
  void given_missingScope_should_refuse() throws Exception {
    String externalId = "security-coverage--" + UUID.randomUUID();

    assertThatThrownBy(
            () -> stixService.processBundle(TxCtx.missing(), bundleWithExternalId(externalId)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("security coverage")
        .hasMessageContaining("single-tenant scope");
  }

  @Test
  @DisplayName("a bundle processed under a multi-tenant scope is refused: the tenant is ambiguous")
  void given_multiTenantScope_should_refuse() throws Exception {
    String externalId = "security-coverage--" + UUID.randomUUID();

    assertThatThrownBy(
            () ->
                stixService.processBundle(
                    TxCtx.forTenants(List.of(tenantA, tenantB)), bundleWithExternalId(externalId)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("security coverage")
        .hasMessageContaining("single-tenant scope");
  }

  /** Raw SQL: the inspector must not hide a row written for the wrong tenant. */
  private List<String> tenantsOwningScenarioOf(String externalId) {
    return jdbcTemplate.queryForList(
        """
        SELECT s.tenant_id FROM scenarios s
        JOIN security_coverages c ON c.security_coverage_scenario = s.scenario_id
        WHERE c.security_coverage_external_id = ?
        """,
        String.class,
        externalId);
  }

  private List<String> tenantsOwning(String externalId) {
    return jdbcTemplate.queryForList(
        "SELECT tenant_id FROM security_coverages WHERE security_coverage_external_id = ?",
        String.class,
        externalId);
  }

  private String bundleWithExternalId(String externalId) throws Exception {
    String rawJson =
        IOUtils.toString(
            new FileInputStream(
                "src/test/resources/stix-bundles/security-coverage-only-vulns.json"),
            StandardCharsets.UTF_8);
    JsonNode stixObjects = mapper.readTree(rawJson).get("event").get("stix_objects");
    for (JsonNode object : stixObjects.get("objects")) {
      if ("security-coverage".equals(object.get("type").asText())) {
        ((ObjectNode) object).put("id", externalId);
      }
    }
    return mapper.writeValueAsString(stixObjects);
  }
}
