package io.openaev.rest.kill_chain_phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.importer.V1_DataImporter;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.exception.TenantWriteScopeException;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The importer creates kill chain phases on the scenario/simulation import paths. It must attribute
 * them from the request scope: {@code TenantContext} is unset off the {@code /api/tenants} route
 * and falls back to the default tenant, which would write the phase outside the scope its own reads
 * are filtered by.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=kill_chain_phases")
@WithMockUser(isAdmin = true)
@DisplayName("kill_chain_phases write attribution on the import path")
class KillChainPhaseImportAttributionTest extends IntegrationTest {

  private static final String IMPORT_FILE =
      "src/test/resources/importer-v1/import-scenario-with-attack-pattern.json";
  private static final String PHASE_EXTERNAL_ID = "KILLCHAIN_EXTERNAL_ID";

  @Autowired private V1_DataImporter importer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  private String contextTenant;
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenants() throws Exception {
    contextTenant = TenantContext.getCurrentTenant();
    tenantA = tenantHelper.createTenantWithCurrentUser("kcp-import-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("kcp-import-b").getId();
    openaevInjectorIntegrationFactory.registerConnectorForTenant(contextTenant);
  }

  @Test
  @DisplayName("the phase is attributed to the scoped tenant, not to the TenantContext one")
  void importAttributesThePhaseToTheScopedTenant() throws Exception {
    importer.importData(
        TxCtx.forTenant(tenantB),
        importNode(),
        Map.of(),
        null,
        null,
        null,
        null,
        Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    entityManager.flush();

    assertEquals(
        1,
        countByExternalIdAndTenant(PHASE_EXTERNAL_ID, tenantB),
        "the phase must belong to the tenant the request is scoped to");
    assertEquals(
        0,
        countByExternalIdAndTenant(PHASE_EXTERNAL_ID, contextTenant),
        "the phase must not follow TenantContext into the default tenant");
  }

  @Test
  @DisplayName("a multi-tenant scope cannot attribute the phase: refused, never guessed")
  void multiTenantScopeIsRefused() throws Exception {
    JsonNode importNode = importNode();
    TxCtx ambiguous = TxCtx.forTenants(List.of(tenantA, tenantB));

    assertThrows(
        TenantWriteScopeException.class,
        () ->
            importer.importData(
                ambiguous,
                importNode,
                Map.of(),
                null,
                null,
                null,
                null,
                Constants.IMPORTED_OBJECT_NAME_SUFFIX));
  }

  private JsonNode importNode() throws Exception {
    return new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get(IMPORT_FILE))));
  }

  // Ground truth through JdbcTemplate: raw JDBC never reaches the statement inspector, so the count
  // sees every tenant's rows regardless of the scope in effect.
  private int countByExternalIdAndTenant(String externalId, String tenantId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM kill_chain_phases WHERE phase_external_id = ? AND tenant_id = ?",
            Integer.class,
            externalId,
            tenantId);
    return count == null ? 0 : count;
  }
}
