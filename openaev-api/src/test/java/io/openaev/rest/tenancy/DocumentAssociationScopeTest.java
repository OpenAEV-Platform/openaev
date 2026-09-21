package io.openaev.rest.tenancy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Channel;
import io.openaev.database.model.Document;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ChannelFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A logo reached through an entity's id-addressable image endpoint is served only under that
 * entity's tenant. A logo document owned by tenant B, bound to a parent owned by tenant A, is
 * refused with the same 404 as a missing file to a caller of tenant A; a same-tenant logo still
 * renders. Both the prefixed and the {@code X-Tenant-Ids} routes are covered.
 *
 * <p>The logo association is seeded directly (out of band): forming a cross-tenant binding through
 * the API is a separate concern, and this pins that the read stays closed however the row was
 * formed. The MinIO objects are removed on teardown; the test transaction rolls back.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A logo reached through an image endpoint holds the parent's tenant on both routes")
class DocumentAssociationScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  private String tenantB;

  // (tenantId, objectTarget) of every object written to real MinIO, removed on teardown: objects
  // are a real side effect and are not rolled back with the test transaction.
  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTenantB() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("assoc-scope-b").getId();
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
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Security platform image")
  class SecurityPlatformImage {

    @Test
    @DisplayName(
        "given a security platform of tenant A carrying a tenant B logo when its image is fetched"
            + " then no bytes are served on either route")
    void
        given_a_security_platform_carrying_a_cross_tenant_logo_when_its_image_is_fetched_then_no_bytes_are_served()
            throws Exception {
      // -- Arrange --
      String tenantA = tenantHelper.createTenantWithCurrentUser("assoc-sp-a").getId();
      byte[] bytes = ("assoc-sp-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String crossLogoId = seedDocumentInTenant(tenantB, UUID.randomUUID() + ".png", bytes);
      String securityPlatformId = seedSecurityPlatformInTenant(tenantA, crossLogoId);

      // -- Act & Assert --
      mvc.perform(
              get(
                  "/api/tenants/{t}/images/security_platforms/id/{id}/dark",
                  tenantA,
                  securityPlatformId))
          .andExpect(status().isNotFound());
      mvc.perform(
              get("/api/images/security_platforms/id/{id}/dark", securityPlatformId)
                  .header("X-Tenant-Ids", tenantA))
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
      String tenantA = tenantHelper.createTenantWithCurrentUser("assoc-sp-a").getId();
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
                      .header("X-Tenant-Ids", tenantA))
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
        "given a channel of tenant A carrying a tenant B logo when its image is fetched then no"
            + " bytes are served on either route")
    void
        given_a_channel_carrying_a_cross_tenant_logo_when_its_image_is_fetched_then_no_bytes_are_served()
            throws Exception {
      // -- Arrange --
      String tenantA = tenantHelper.createTenantWithCurrentUser("assoc-channel-a").getId();
      byte[] bytes = ("assoc-channel-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String crossLogoId = seedDocumentInTenant(tenantB, UUID.randomUUID() + ".png", bytes);
      String channelId = seedChannelInTenant(tenantA, crossLogoId);

      // -- Act & Assert --
      mvc.perform(get("/api/tenants/{t}/images/channels/id/{id}/dark", tenantA, channelId))
          .andExpect(status().isNotFound());
      mvc.perform(
              get("/api/images/channels/id/{id}/dark", channelId).header("X-Tenant-Ids", tenantA))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a channel and logo both of tenant A when its image is fetched then the logo bytes"
            + " are served on either route")
    void given_a_same_tenant_channel_logo_when_its_image_is_fetched_then_the_bytes_are_served()
        throws Exception {
      // -- Arrange --
      String tenantA = tenantHelper.createTenantWithCurrentUser("assoc-channel-a").getId();
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
                      .header("X-Tenant-Ids", tenantA))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray(),
          "the owning tenant must still render its own channel logo on the header route");
    }
  }

  // region helpers

  /**
   * Seeds a document owned by {@code tenantId} without a controller call: the object is written
   * under the tenant's own prefix and the row carries that tenant explicitly, matching what an
   * upload attributed to that tenant produces.
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
    Document document = new Document();
    document.setName("assoc-seed-" + UUID.randomUUID());
    document.setTarget(target);
    document.setType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    document.setTenant(new Tenant(tenantId));
    entityManager.persist(document);
    entityManager.flush();
    return document.getId();
  }

  private String seedChannelInTenant(String tenantId, String logoDarkDocumentId) {
    Channel channel = ChannelFixture.getDefaultChannel();
    channel.setTenant(new Tenant(tenantId));
    if (logoDarkDocumentId != null) {
      channel.setLogoDark(entityManager.getReference(Document.class, logoDarkDocumentId));
    }
    entityManager.persist(channel);
    entityManager.flush();
    return channel.getId();
  }

  private String seedSecurityPlatformInTenant(String tenantId, String logoDarkDocumentId) {
    SecurityPlatform securityPlatform =
        SecurityPlatformFixture.createDefault("assoc-sp-" + UUID.randomUUID(), "EDR");
    securityPlatform.setTenant(new Tenant(tenantId));
    if (logoDarkDocumentId != null) {
      securityPlatform.setLogoDark(entityManager.getReference(Document.class, logoDarkDocumentId));
    }
    entityManager.persist(securityPlatform);
    entityManager.flush();
    return securityPlatform.getId();
  }

  // endregion
}
