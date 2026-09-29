package io.openaev.rest.payload.service;

import static io.openaev.service.stix.SecurityCoverageInjectService.ALL_PLATFORMS;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.DnsResolution;
import io.openaev.database.model.Document;
import io.openaev.database.model.FileDrop;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The Dynamic DNS Resolution and File Drop built-in payloads are lazily created the first time a
 * tenant needs them (STIX security-coverage ingestion). With {@code payloads} v2-active, both
 * creation paths must attribute the row to the write tenant explicitly: relying on a shared global
 * primary key, or on the ambient {@code TenantContext} fallback, breaks under more than one tenant.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=payloads,documents,injectors")
@WithMockUser(isAdmin = true)
@DisplayName("payloads v2 attribution of the lazily-created built-in payloads")
class PayloadServiceTenantAttributionTest extends IntegrationTest {

  private static final String DYNAMIC_DNS_RESOLUTION_LEGACY_UUID =
      "ff16dc60-ea6f-4925-8509-20557e09c676";

  @Autowired private PayloadService payloadService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantA;
  private String tenantB;

  @AfterEach
  void cleanUp() {
    if (tenantA != null || tenantB != null) {
      jdbcTemplate.update("DELETE FROM payloads WHERE tenant_id IN (?, ?)", tenantA, tenantB);
      jdbcTemplate.update("DELETE FROM documents WHERE tenant_id IN (?, ?)", tenantA, tenantB);
      tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    }
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Dynamic DNS Resolution payload")
  class DynamicDnsResolution {

    @Test
    @DisplayName(
        "given two tenants should each get their own row instead of colliding on the shared"
            + " primary key")
    void given_twoTenants_should_eachGetOwnRowWithoutPkCollision() throws Exception {
      // Arrange
      tenantA = tenantHelper.createTenantWithCurrentUser("dns-payload-a").getId();
      tenantB = tenantHelper.createTenantWithCurrentUser("dns-payload-b").getId();

      // Act: tenant A creates it first, then tenant B - unfixed code makes B's create collide on
      // the same hardcoded primary key that A already owns (a row A's scope hides from B). The
      // ambient tenant is set to match the write scope here (like the prefixed HTTP route would):
      // this test is about the primary key and the lock, not about attribution from the ambient
      // context, which the File Drop payload test below covers on its own.
      DnsResolution createdForA =
          inTenantWithAmbient(
              tenantA,
              () -> payloadService.getDynamicDnsResolutionPayload(TxCtx.forTenant(tenantA)));
      DnsResolution createdForB =
          inTenantWithAmbient(
              tenantB,
              () -> payloadService.getDynamicDnsResolutionPayload(TxCtx.forTenant(tenantB)));

      // Assert: two non-default tenants get their own derived ids, distinct from each other and
      // from the legacy id that only the default tenant keeps.
      assertThat(createdForA.getId()).isNotEqualTo(createdForB.getId());
      assertThat(createdForA.getId()).isNotEqualTo(DYNAMIC_DNS_RESOLUTION_LEGACY_UUID);
      assertThat(createdForB.getId()).isNotEqualTo(DYNAMIC_DNS_RESOLUTION_LEGACY_UUID);
      assertThat(rawPayloadTenant(createdForA.getId())).isEqualTo(tenantA);
      assertThat(rawPayloadTenant(createdForB.getId())).isEqualTo(tenantB);
    }

    @Test
    @DisplayName(
        "given the same tenant asking twice should reuse its own row, not create a second one")
    void given_sameTenantTwice_should_reuseOwnRow() throws Exception {
      // Arrange
      tenantA = tenantHelper.createTenantWithCurrentUser("dns-payload-reuse").getId();

      // Act
      DnsResolution first =
          inTenantWithAmbient(
              tenantA,
              () -> payloadService.getDynamicDnsResolutionPayload(TxCtx.forTenant(tenantA)));
      DnsResolution second =
          inTenantWithAmbient(
              tenantA,
              () -> payloadService.getDynamicDnsResolutionPayload(TxCtx.forTenant(tenantA)));

      // Assert
      assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName(
        "given tenant B's write scope should stamp the created DNS resolution payload to B, not"
            + " the ambient default tenant")
    void given_tenantBWriteScope_should_stampCreatedDnsResolutionToB() throws Exception {
      // Arrange: a raw tenant row (no onboarding-provisioned injector), same reason as the File
      // Drop test below - isolates the payload's own attribution from the unrelated (still-v1)
      // injector contract side effect.
      tenantB = insertRawTenant("dns-payload-ambient-b");
      // Simulate the non-prefixed (X-Tenant-Ids) route: TenantInterceptor never sets the v1
      // ambient tenant there, so it is left cleared while the resolved write scope is B.
      TenantContext.clearCurrentTenant();

      // Act
      DnsResolution created =
          inTenant(
              tenantB,
              () -> payloadService.getDynamicDnsResolutionPayload(TxCtx.forTenant(tenantB)));

      // Assert
      assertThat(rawPayloadTenant(created.getId())).isEqualTo(tenantB);
    }

    @Test
    @DisplayName(
        "given the default tenant already owns the row at the legacy shared id should reuse it"
            + " instead of creating a duplicate at the newly-derived id")
    void given_defaultTenantHasLegacyRow_should_reuseItInsteadOfDuplicating() throws Exception {
      // Arrange: any platform that ingested DNS-resolution STIX data before this fix already
      // holds a row at the legacy hardcoded primary key, owned by the default tenant (the only
      // tenant that existed before v2 attribution). Clean up whatever another test in this run
      // may have already lazily created for the default tenant, so this test is deterministic.
      deleteDefaultTenantDnsResolutionRows();
      seedLegacyDnsResolutionRow();

      try {
        // Act
        DnsResolution resolved =
            inTenantWithAmbient(
                Tenant.DEFAULT_TENANT_UUID,
                () ->
                    payloadService.getDynamicDnsResolutionPayload(
                        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID)));

        // Assert: the pre-existing legacy row is found and reused, not duplicated.
        assertThat(resolved.getId()).isEqualTo(DYNAMIC_DNS_RESOLUTION_LEGACY_UUID);
        assertThat(countDefaultTenantDnsResolutionRows()).isEqualTo(1);
      } finally {
        deleteDefaultTenantDnsResolutionRows();
      }
    }
  }

  @Nested
  @DisplayName("File Drop payload")
  class FileDropPayload {

    @Test
    @DisplayName(
        "given tenant B's write scope should stamp the created file drop to B, not the ambient"
            + " default tenant")
    void given_tenantBWriteScope_should_stampCreatedFileDropToB() throws Exception {
      // Arrange: a raw tenant row, not tenantHelper.createTenantWithCurrentUser - onboarding
      // registers a built-in payload-supporting injector for the tenant, which would make
      // synchroniseInjectorContractBasedOnPayload's own (out-of-scope, still-v1) injector contract
      // side effect reachable here; this test is only about the file drop payload's own
      // attribution.
      tenantB = insertRawTenant("filedrop-payload-b");
      String documentId =
          inTenant(
              tenantB,
              () -> {
                Document document = DocumentFixture.getDocumentJpeg();
                document.setTenant(new Tenant(tenantB));
                return documentRepository.save(document).getId();
              });
      // Simulate the non-prefixed (X-Tenant-Ids) route: TenantInterceptor never sets the v1
      // ambient tenant there, so it is left cleared while the resolved write scope is B.
      TenantContext.clearCurrentTenant();

      // Act
      FileDrop created =
          inTenant(
              tenantB,
              () -> payloadService.createFileDropPayload(TxCtx.forTenant(tenantB), documentId));

      // Assert
      assertThat(rawPayloadTenant(created.getId())).isEqualTo(tenantB);
    }
  }

  private void seedLegacyDnsResolutionRow() {
    DnsResolution legacy = new DnsResolution();
    legacy.setId(DYNAMIC_DNS_RESOLUTION_LEGACY_UUID);
    legacy.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    legacy.setName("Dynamic DNS Resolution");
    legacy.setHostname("filigran.io");
    legacy.setSource(Payload.PAYLOAD_SOURCE.FILIGRAN);
    legacy.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    legacy.setPlatforms(ALL_PLATFORMS);
    payloadRepository.save(legacy);
  }

  private void deleteDefaultTenantDnsResolutionRows() {
    jdbcTemplate.update(
        "DELETE FROM payloads WHERE payload_name = 'Dynamic DNS Resolution' AND tenant_id = ?",
        Tenant.DEFAULT_TENANT_UUID);
  }

  private int countDefaultTenantDnsResolutionRows() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM payloads WHERE payload_name = 'Dynamic DNS Resolution' AND"
            + " tenant_id = ?",
        Integer.class,
        Tenant.DEFAULT_TENANT_UUID);
  }

  private String rawPayloadTenant(String payloadId) {
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM payloads WHERE payload_id = ?", String.class, payloadId);
  }

  private String insertRawTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenantId), work);
  }

  private <T> T inTenantWithAmbient(String tenantId, Supplier<T> work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }
}
