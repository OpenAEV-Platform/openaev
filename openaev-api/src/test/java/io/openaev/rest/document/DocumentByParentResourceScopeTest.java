package io.openaev.rest.document;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.Channel;
import io.openaev.database.model.Document;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.FileDrop;
import io.openaev.database.model.Payload;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ChallengeFixture;
import io.openaev.utils.fixtures.ChannelFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The four {@code documentsFor*} native queries in {@code DocumentRepository} (channel, security
 * platform, challenge, payload) join through a parent-resource id supplied by the caller and are
 * not covered by the Hibernate {@code tenantFilter} (native SQL is never filtered).
 * {@code @AccessControl} on these endpoints checks the caller's capability, not tenant ownership of
 * the parent id, so without a tenant predicate a caller scoped to tenant A who supplies a
 * cross-tenant parent id (a security platform, channel, challenge or payload owned by tenant B)
 * reads tenant B's document metadata (id, name, description, storage target).
 *
 * <p>This class pins the tenant-prefixed route for all four list-by-parent-resource endpoints: a
 * caller scoped to tenant A gets an empty list for a parent resource owned by tenant B, and the
 * owning tenant B still gets its document back.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Document by-parent-resource endpoints hold the request tenant scope")
class DocumentByParentResourceScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("doc-parent-scope-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("doc-parent-scope-b").getId();
  }

  @Nested
  @DisplayName("GET /security_platforms/{id}/documents")
  class SecurityPlatformDocuments {

    @Test
    @DisplayName(
        "a caller scoped to tenant A gets an empty list for a security platform owned by tenant B")
    void given_securityPlatformOwnedByAnotherTenant_should_returnEmptyList() throws Exception {
      // Arrange
      SecurityPlatform securityPlatform = seedSecurityPlatformWithLogo(tenantB);

      // Act & Assert
      mvc.perform(
              get(
                  TENANT_PREFIX + "/security_platforms/{securityPlatformId}/documents",
                  tenantA,
                  securityPlatform.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("the owning tenant still receives its security platform's documents")
    void given_securityPlatformOwnedByCurrentTenant_should_returnDocument() throws Exception {
      // Arrange
      SecurityPlatform securityPlatform = seedSecurityPlatformWithLogo(tenantB);

      // Act & Assert
      mvc.perform(
              get(
                  TENANT_PREFIX + "/security_platforms/{securityPlatformId}/documents",
                  tenantB,
                  securityPlatform.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].document_id").value(securityPlatform.getLogoLight().getId()));
    }
  }

  @Nested
  @DisplayName("GET /channels/{id}/documents")
  class ChannelDocuments {

    @Test
    @DisplayName("a caller scoped to tenant A gets an empty list for a channel owned by tenant B")
    void given_channelOwnedByAnotherTenant_should_returnEmptyList() throws Exception {
      // Arrange
      Channel channel = seedChannelWithLogo(tenantB);

      // Act & Assert
      mvc.perform(get(TENANT_PREFIX + "/channels/{channelId}/documents", tenantA, channel.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("the owning tenant still receives its channel's documents")
    void given_channelOwnedByCurrentTenant_should_returnDocument() throws Exception {
      // Arrange
      Channel channel = seedChannelWithLogo(tenantB);

      // Act & Assert
      mvc.perform(get(TENANT_PREFIX + "/channels/{channelId}/documents", tenantB, channel.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].document_id").value(channel.getLogoLight().getId()));
    }
  }

  @Nested
  @DisplayName("GET /challenges/{id}/documents")
  class ChallengeDocuments {

    @Test
    @DisplayName("a caller scoped to tenant A gets an empty list for a challenge owned by tenant B")
    void given_challengeOwnedByAnotherTenant_should_returnEmptyList() throws Exception {
      // Arrange
      Challenge challenge = seedChallengeWithDocument(tenantB);

      // Act & Assert
      mvc.perform(
              get(
                  TENANT_PREFIX + "/challenges/{challengeId}/documents",
                  tenantA,
                  challenge.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("the owning tenant still receives its challenge's documents")
    void given_challengeOwnedByCurrentTenant_should_returnDocument() throws Exception {
      // Arrange
      Challenge challenge = seedChallengeWithDocument(tenantB);

      // Act & Assert
      mvc.perform(
              get(
                  TENANT_PREFIX + "/challenges/{challengeId}/documents",
                  tenantB,
                  challenge.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].document_id").value(challenge.getDocuments().get(0).getId()));
    }
  }

  @Nested
  @DisplayName("GET /payloads/{id}/documents")
  class PayloadDocuments {

    @Test
    @DisplayName("a caller scoped to tenant A gets an empty list for a payload owned by tenant B")
    void given_payloadOwnedByAnotherTenant_should_returnEmptyList() throws Exception {
      // Arrange
      Payload payload = seedFileDropPayload(tenantB);

      // Act & Assert
      mvc.perform(get(TENANT_PREFIX + "/payloads/{payloadId}/documents", tenantA, payload.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("the owning tenant still receives its payload's documents")
    void given_payloadOwnedByCurrentTenant_should_returnDocument() throws Exception {
      // Arrange
      Payload payload = seedFileDropPayload(tenantB);
      Document fileDropFile = ((FileDrop) payload).getFileDropFile();

      // Act & Assert
      mvc.perform(get(TENANT_PREFIX + "/payloads/{payloadId}/documents", tenantB, payload.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].document_id").value(fileDropFile.getId()));
    }
  }

  /** Seeds a metadata-only document owned by {@code tenantId}, without an object in storage. */
  private Document seedDocument(String tenantId) {
    Document document = new Document();
    document.setName("doc-parent-scope-" + UUID.randomUUID());
    document.setTarget(UUID.randomUUID() + ".txt");
    document.setType(MediaType.TEXT_PLAIN_VALUE);
    document.setTenant(new Tenant(tenantId));
    entityManager.persist(document);
    return document;
  }

  private SecurityPlatform seedSecurityPlatformWithLogo(String tenantId) {
    Document logo = seedDocument(tenantId);
    SecurityPlatform securityPlatform =
        SecurityPlatformFixture.createDefault("doc-parent-scope-sp-" + UUID.randomUUID(), "EDR");
    securityPlatform.setTenant(new Tenant(tenantId));
    securityPlatform.setLogoLight(logo);
    entityManager.persist(securityPlatform);
    entityManager.flush();
    return securityPlatform;
  }

  private Channel seedChannelWithLogo(String tenantId) {
    Document logo = seedDocument(tenantId);
    Channel channel = ChannelFixture.getDefaultChannel();
    channel.setTenant(new Tenant(tenantId));
    channel.setLogoLight(logo);
    entityManager.persist(channel);
    entityManager.flush();
    return channel;
  }

  private Challenge seedChallengeWithDocument(String tenantId) {
    Document document = seedDocument(tenantId);
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    challenge.setTenant(new Tenant(tenantId));
    challenge.setDocuments(List.of(document));
    entityManager.persist(challenge);
    entityManager.flush();
    return challenge;
  }

  private Payload seedFileDropPayload(String tenantId) {
    Document document = seedDocument(tenantId);
    FileDrop payload = new FileDrop();
    payload.setName("doc-parent-scope-payload-" + UUID.randomUUID());
    payload.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux});
    payload.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
    payload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    payload.setFileDropFile(document);
    payload.setTenant(new Tenant(tenantId));
    entityManager.persist(payload);
    entityManager.flush();
    return payload;
  }
}
