package io.openaev.rest.asset.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.EndpointRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Executes {@code EndpointRepository.findByHostnameAndAtleastOneMacAddress} against a real database
 * with {@code assets} active.
 *
 * <p>This is the query that agent registration uses to recognise a host it has already seen. Its
 * two {@code unnest(...)} FROM items needed {@code LATERAL} to survive the tenant rewrite; without
 * it the inspector refuses the statement fail-closed and registration breaks.
 *
 * <p>{@code TenantStatementInspectorTest} pins that the inspector ACCEPTS the query. That is not
 * the same as the rewritten SQL being valid PostgreSQL and returning the right rows, and nothing
 * proved the latter: the only other test touching this method is a Mockito unit test that stubs the
 * repository, so the SQL never reaches a database. This closes that gap.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction,
 * so seeding and cleanup go through an auto-committing {@link JdbcTemplate}, and the test removes
 * only the rows it created.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@DisplayName("endpoint lookup by hostname and MAC under an active assets table")
class EndpointMacLookupTenantScopeTest extends IntegrationTest {

  private static final String HOSTNAME = "mac-lookup-host";
  private static final String MAC_A = "aabbccddee01";
  private static final String MAC_B = "aabbccddee02";

  @Autowired private EndpointRepository endpointRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private String tenantA;
  private String tenantB;
  private String endpointA;

  @BeforeEach
  void seedOneEndpointPerTenantSharingAHostname() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("mac-a-" + UUID.randomUUID());
    tenantB = seedTenant("mac-b-" + UUID.randomUUID());
    // Same hostname and same MAC in both tenants on purpose: only the scope can tell them apart.
    endpointA = seedEndpoint(tenantA, MAC_A);
    seedEndpoint(tenantB, MAC_A);
  }

  @AfterEach
  void cleanup() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("under tenant A's scope the query runs and returns A's endpoint, never B's")
  void lookupUnderTenantAReturnsOnlyA() {
    List<String> found = lookup(tenantA, MAC_A);

    // Non-empty on purpose: an empty result is exactly what a fail-closed refusal or a lost scope
    // would produce, and asserting emptiness would pass through both.
    assertEquals(1, found.size(), "tenant A's endpoint must be found: " + found);
    assertEquals(endpointA, found.getFirst());
  }

  @Test
  @DisplayName("a MAC that matches nothing returns empty rather than throwing")
  void lookupWithAnUnknownMacIsEmptyNotRefused() {
    List<String> found = lookup(tenantA, MAC_B);

    assertTrue(found.isEmpty(), "an unmatched MAC must return empty: " + found);
  }

  @Test
  @DisplayName("under tenant B's scope the same hostname and MAC resolve to B's own endpoint")
  void lookupIsSymmetricUnderTenantB() {
    List<String> found = lookup(tenantB, MAC_A);

    assertEquals(1, found.size(), "tenant B's endpoint must be found: " + found);
    assertFalse(endpointA.equals(found.getFirst()), "B must not resolve to A's endpoint row");
  }

  /**
   * Runs the lookup under {@code tenantId} and returns ids only.
   *
   * <p>Ids, not entities: an Endpoint carried out of the scoped transaction is detached, and merely
   * putting it in an assertion message calls toString(), which touches the lazy {@code injects}
   * collection and throws LazyInitializationException. Mapping inside the block keeps the failure
   * message about the query, not about the session.
   */
  private List<String> lookup(String tenantId, String mac) {
    return tenantTx.<List<String>>execute(
        TxCtx.forTenant(tenantId),
        () ->
            endpointRepository
                .findByHostnameAndAtleastOneMacAddress(HOSTNAME, new String[] {mac}, tenantId)
                .stream()
                .map(Endpoint::getId)
                .toList());
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    seededTenants.add(id);
    return id;
  }

  private String seedEndpoint(String tenantId, String mac) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_type, asset_name, asset_hostname,"
            + " asset_mac_addresses, endpoint_arch, asset_created_at, asset_updated_at, tenant_id)"
            + " VALUES (?, 'Endpoint', ?, ?, ARRAY[?], 'x86_64', now(), now(), ?)",
        id,
        "mac-lookup-endpoint",
        HOSTNAME,
        mac,
        tenantId);
    return id;
  }
}
