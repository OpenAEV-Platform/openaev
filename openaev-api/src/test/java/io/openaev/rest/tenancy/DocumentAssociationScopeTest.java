package io.openaev.rest.tenancy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A logo reached through an entity's id-addressable image endpoint is served only under that
 * entity's tenant. A logo document owned by tenant B, bound to a parent owned by tenant A, is
 * refused with the same 404 as a missing file; a same-tenant logo still renders. Both the prefixed
 * and the {@code X-Tenant-Ids} routes are covered.
 *
 * <p>Two nets refuse the cross-tenant logo, and each is pinned by its own case. A caller scoped to
 * tenant A alone never sees B's row: the lazy logo reference cannot initialize inside the scoped
 * transaction and the endpoint answers 404 before any ownership check runs. A caller scoped to both
 * tenants through the header does see the row, and the owning-tenant check in {@code
 * FileService.getFile(Document, owningTenantId)} refuses the bytes because the parent belongs to A.
 * The second case is the one that proves the check, since the first never reaches it.
 *
 * <p>The logo association is seeded directly (out of band): forming a cross-tenant binding through
 * the API is a separate concern, and this pins that the read stays closed however the row was
 * formed. The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so every read of the request is a statement the inspector
 * rewrites, and removed on teardown with the MinIO objects.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("A logo reached through an image endpoint holds the parent's tenant on both routes")
class DocumentAssociationScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;

  // (tenantId, objectTarget) of every object written to real MinIO, removed on teardown.
  private final List<String[]> uploadedObjects = new ArrayList<>();
  private final List<String[]> seededRows = new ArrayList<>();

  @BeforeEach
  void seedTenants() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = tenantHelper.createTenantWithCurrentUser("assoc-scope-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("assoc-scope-b").getId();
    // The tenant fixtures leave a tenant on the test thread; the request thread of the header
    // route carries none, so the ambient tenant falls back to the default one there.
    TenantContext.clearCurrentTenant();
    assertEquals(Tenant.DEFAULT_TENANT_UUID, TenantContext.getCurrentTenant());
  }

  @AfterEach
  void cleanup() {
    for (String[] object : uploadedObjects) {
      try {
        minioService.deleteFileForTenant(object[0], object[1]);
      } catch (Exception e) {
        // best-effort cleanup
      }
    }
    uploadedObjects.clear();
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Security platform image")
  class SecurityPlatformImage {

    @Test
    @DisplayName(
        "given a security platform of tenant A carrying a tenant B logo when its image is fetched"
            + " under A then no bytes are served on either route")
    void
        given_a_security_platform_carrying_a_cross_tenant_logo_when_its_image_is_fetched_then_no_bytes_are_served()
            throws Exception {
      // -- Arrange --
      byte[] bytes = ("assoc-sp-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String crossLogoId = seedDocumentInTenant(tenantB, UUID.randomUUID() + ".png", bytes);
      String securityPlatformId = seedSecurityPlatformInTenant(tenantA, crossLogoId);

      // -- Act & Assert -- the scope holds A alone: B's row is invisible to the request
      mvc.perform(
              get(
                  "/api/tenants/{t}/images/security_platforms/id/{id}/dark",
                  tenantA,
                  securityPlatformId))
          .andExpect(status().isNotFound());
      mvc.perform(
              get("/api/images/security_platforms/id/{id}/dark", securityPlatformId)
                  .header(TENANT_HEADER, tenantA))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a caller scoped to both tenants when the image of A's platform carrying B's logo is"
            + " fetched then the owning-tenant check still refuses the bytes")
    void
        given_a_caller_scoped_to_both_tenants_when_a_cross_tenant_logo_is_fetched_then_the_owning_tenant_check_refuses_it()
            throws Exception {
      // -- Arrange --
      byte[] bytes = ("assoc-sp-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String crossLogoId = seedDocumentInTenant(tenantB, UUID.randomUUID() + ".png", bytes);
      String securityPlatformId = seedSecurityPlatformInTenant(tenantA, crossLogoId);

      // -- Act & Assert -- the scope holds both tenants, so B's row loads; the parent is A's
      mvc.perform(
              get("/api/images/security_platforms/id/{id}/dark", securityPlatformId)
                  .header(TENANT_HEADER, tenantA + "," + tenantB))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a security platform and logo both of tenant A when its image is fetched then the"
            + " logo bytes are served on either route")
    void
        given_a_same_tenant_security_platform_logo_when_its_image_is_fetched_then_the_bytes_are_served()
            throws Exception {
      // -- Arrange --
      byte[] bytes = ("assoc-sp-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String logoId = seedDocumentInTenant(tenantA, UUID.randomUUID() + ".png", bytes);
      String securityPlatformId = seedSecurityPlatformInTenant(tenantA, logoId);

      // -- Act & Assert --
      assertArrayEquals(
          bytes,
          mvc.perform(
                  get(
                      "/api/tenants/{t}/images/security_platforms/id/{id}/dark",
                      tenantA,
                      securityPlatformId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray(),
          "the owning tenant must still render its own security platform logo on the prefixed route");
      assertArrayEquals(
          bytes,
          mvc.perform(
                  get("/api/images/security_platforms/id/{id}/dark", securityPlatformId)
                      .header(TENANT_HEADER, tenantA))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray(),
          "the owning tenant must still render its own security platform logo on the header route");
    }
  }

  @Nested
  @DisplayName("Channel image")
  class ChannelImage {

    @Test
    @DisplayName(
        "given a channel of tenant A carrying a tenant B logo when its image is fetched under A"
            + " then no bytes are served on either route")
    void
        given_a_channel_carrying_a_cross_tenant_logo_when_its_image_is_fetched_then_no_bytes_are_served()
            throws Exception {
      // -- Arrange --
      byte[] bytes = ("assoc-channel-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String crossLogoId = seedDocumentInTenant(tenantB, UUID.randomUUID() + ".png", bytes);
      String channelId = seedChannelInTenant(tenantA, crossLogoId);

      // -- Act & Assert -- the scope holds A alone: B's row is invisible to the request
      mvc.perform(get("/api/tenants/{t}/images/channels/id/{id}/dark", tenantA, channelId))
          .andExpect(status().isNotFound());
      mvc.perform(
              get("/api/images/channels/id/{id}/dark", channelId).header(TENANT_HEADER, tenantA))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a caller scoped to both tenants when the image of A's channel carrying B's logo is"
            + " fetched then the owning-tenant check still refuses the bytes")
    void
        given_a_caller_scoped_to_both_tenants_when_a_cross_tenant_channel_logo_is_fetched_then_the_owning_tenant_check_refuses_it()
            throws Exception {
      // -- Arrange --
      byte[] bytes = ("assoc-channel-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String crossLogoId = seedDocumentInTenant(tenantB, UUID.randomUUID() + ".png", bytes);
      String channelId = seedChannelInTenant(tenantA, crossLogoId);

      // -- Act & Assert -- the scope holds both tenants, so B's row loads; the parent is A's
      mvc.perform(
              get("/api/images/channels/id/{id}/dark", channelId)
                  .header(TENANT_HEADER, tenantA + "," + tenantB))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a channel and logo both of tenant A when its image is fetched then the logo bytes"
            + " are served on either route")
    void given_a_same_tenant_channel_logo_when_its_image_is_fetched_then_the_bytes_are_served()
        throws Exception {
      // -- Arrange --
      byte[] bytes = ("assoc-channel-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String logoId = seedDocumentInTenant(tenantA, UUID.randomUUID() + ".png", bytes);
      String channelId = seedChannelInTenant(tenantA, logoId);

      // -- Act & Assert --
      assertArrayEquals(
          bytes,
          mvc.perform(get("/api/tenants/{t}/images/channels/id/{id}/dark", tenantA, channelId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray(),
          "the owning tenant must still render its own channel logo on the prefixed route");
      assertArrayEquals(
          bytes,
          mvc.perform(
                  get("/api/images/channels/id/{id}/dark", channelId)
                      .header(TENANT_HEADER, tenantA))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray(),
          "the owning tenant must still render its own channel logo on the header route");
    }
  }

  // region helpers

  /**
   * Seeds a committed document owned by {@code tenantId} without a controller call: the object is
   * written under the tenant's own prefix and the row carries that tenant explicitly, matching what
   * an upload attributed to that tenant produces.
   */
  private String seedDocumentInTenant(String tenantId, String target, byte[] content)
      throws Exception {
    minioService.uploadFileForTenant(
        tenantId,
        target,
        new ByteArrayInputStream(content),
        content.length,
        MediaType.APPLICATION_OCTET_STREAM_VALUE);
    uploadedObjects.add(new String[] {tenantId, target});
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_target, document_type,"
            + " tenant_id) VALUES (?, ?, ?, ?, ?)",
        id,
        "assoc-seed-" + id,
        target,
        MediaType.APPLICATION_OCTET_STREAM_VALUE,
        tenantId);
    seededRows.add(new String[] {"documents", "document_id", id});
    return id;
  }

  private String seedChannelInTenant(String tenantId, String logoDarkDocumentId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO channels (channel_id, channel_type, channel_name, channel_logo_dark,"
            + " tenant_id) VALUES (?, 'newsletter', ?, ?, ?)",
        id,
        "assoc-channel-" + id,
        logoDarkDocumentId,
        tenantId);
    seededRows.add(new String[] {"channels", "channel_id", id});
    return id;
  }

  private String seedSecurityPlatformInTenant(String tenantId, String logoDarkDocumentId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_type, asset_name, security_platform_type,"
            + " security_platform_logo_dark, tenant_id) VALUES (?, 'SecurityPlatform', ?, 'EDR',"
            + " ?, ?)",
        id,
        "assoc-sp-" + id,
        logoDarkDocumentId,
        tenantId);
    seededRows.add(new String[] {"assets", "asset_id", id});
    return id;
  }

  // endregion
}
