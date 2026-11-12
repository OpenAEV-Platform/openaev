package io.openaev.api.snapshot;

import static io.openaev.api.snapshot.SnapshotObservationApi.TENANT_SNAPSHOT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.snapshot.form.SnapshotSearchInput;
import io.openaev.config.EngineConfig;
import io.openaev.database.model.Capability;
import io.openaev.database.model.IndexingStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The vulnerability pending-indexing probe under the production activation of {@code findings} and
 * {@code assets}: the tenant statement inspector scopes the probe to the requested tenant, so a row
 * pending in another tenant must not hold this tenant's {@code indexed_through} back, while a row
 * pending in this tenant must.
 *
 * <p>Dedicated class because {@link TestPropertySource} forks the Spring context. One tenant path
 * per test method: the first request sets the transaction tenant scope. The fixture only uses
 * single native INSERTs on the activated tables: an UPDATE there would go through the inspector
 * before any scope is set and silently match nothing.
 */
@Transactional
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=BULK_SNAPSHOT_EXPORT",
      "openaev.tenant.active-tables=findings,assets"
    })
@DisplayName("Snapshot observation API — probe under tenant-active findings and assets")
class SnapshotObservationTenantScopeTest extends IntegrationTest {

  private static final String VULNERABILITY_INDEXING_TYPE = "snapshot-vulnerability-observation";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;
  @Autowired private IndexingStatusRepository indexingStatusRepository;
  @Autowired private EngineConfig engineConfig;

  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
  @DisplayName("given_rowPendingInAnotherTenantOnly_should_notHoldTheHorizonBack")
  void given_rowPendingInAnotherTenantOnly_should_notHoldTheHorizonBack() throws Exception {
    // -- ARRANGE --
    Tenant requested =
        tenantHelper.createTenantWithCapabilities(
            "snapshot-scope-a-" + UUID.randomUUID(),
            Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));
    Tenant other = tenantHelper.createTenant("snapshot-scope-b-" + UUID.randomUUID());
    Instant now = Instant.now();
    setCursor(now.minus(2, ChronoUnit.HOURS));
    seedCveFinding(other, pendingTimestamp(now));

    // -- ACT --
    String response = searchVulnerabilities(requested.getId());

    // -- ASSERT: nothing of the requested tenant is pending, the horizon is the fallback --
    Instant serverTime = Instant.parse(JsonPath.read(response, "$.server_time"));
    assertThat(Instant.parse((String) JsonPath.read(response, "$.indexed_through")))
        .isEqualTo(serverTime.minusSeconds(engineConfig.getIndexingGraceWindowSeconds()));
  }

  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
  @DisplayName("given_rowPendingInTheRequestedTenant_should_holdTheHorizonAtTheCursor")
  void given_rowPendingInTheRequestedTenant_should_holdTheHorizonAtTheCursor() throws Exception {
    // -- ARRANGE --
    Tenant requested =
        tenantHelper.createTenantWithCapabilities(
            "snapshot-scope-a-" + UUID.randomUUID(),
            Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));
    Instant now = Instant.now();
    Instant cursor = now.minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
    setCursor(cursor);
    seedCveFinding(requested, pendingTimestamp(now));

    // -- ACT --
    String response = searchVulnerabilities(requested.getId());

    // -- ASSERT: the scoped probe sees the tenant's own pending row (not a fail-closed zero) --
    assertThat(Instant.parse((String) JsonPath.read(response, "$.indexed_through")))
        .isEqualTo(cursor);
    assertThat((Boolean) JsonPath.read(response, "$.snapshot_ready")).isFalse();
  }

  /** After the cursor, and before now - grace: the only range a pending row is looked for in. */
  private Instant pendingTimestamp(Instant now) {
    return now.minusSeconds(engineConfig.getIndexingGraceWindowSeconds() + 30)
        .truncatedTo(ChronoUnit.MICROS);
  }

  private void setCursor(Instant cursor) {
    IndexingStatus status = new IndexingStatus();
    status.setType(VULNERABILITY_INDEXING_TYPE);
    status.setLastIndexing(cursor);
    indexingStatusRepository.save(status);
  }

  /** One CVE finding on one endpoint of {@code tenant}, updated at {@code updatedAt}. */
  private void seedCveFinding(Tenant tenant, Instant updatedAt) {
    // injects is not an activated table: the entity path is fine for it.
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTenant(tenant);
    entityManager.persist(inject);
    entityManager.flush();

    String assetId = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at,"
                + " asset_updated_at, tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
                + " VALUES (:id, :name, 'Endpoint', now(), now(), :tenantId, :name, 'Linux',"
                + " 'x86_64')")
        .setParameter("id", assetId)
        .setParameter("name", "ep-scope-" + assetId)
        .setParameter("tenantId", tenant.getId())
        .executeUpdate();

    String findingId = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
                + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
                + " VALUES (:id, 'cve', 'CVE', :value, :injectId, :ts, :ts, :tenantId)")
        .setParameter("id", findingId)
        .setParameter("value", "CVE-" + UUID.randomUUID())
        .setParameter("injectId", inject.getId())
        .setParameter("ts", updatedAt)
        .setParameter("tenantId", tenant.getId())
        .executeUpdate();
    entityManager
        .createNativeQuery(
            "INSERT INTO findings_assets (finding_id, asset_id) VALUES (:findingId, :assetId)")
        .setParameter("findingId", findingId)
        .setParameter("assetId", assetId)
        .executeUpdate();
  }

  private String searchVulnerabilities(String tenantId) throws Exception {
    return mvc.perform(
            post(TENANT_SNAPSHOT_URI.replace("{tenantId}", tenantId)
                    + "/vulnerability-observations/search")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(new SnapshotSearchInput(null, null, null, null)))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
