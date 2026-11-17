package io.openaev.rest.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Communication;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CommunicationRepository;
import io.openaev.service.FileService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A communication attachment is read from the tenant of the inject it belongs to, restricted to the
 * caller's own authorized scope: this endpoint has no tenant-prefixed route, so the ambient {@link
 * TenantContext} always falls back to the default tenant, whatever tenant actually owns the file
 * and whatever tenant the caller is authorized for.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Downloading a communication attachment follows its own tenant")
class ExerciseAttachmentDownloadTenantTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private InjectComposer injectComposer;
  @Autowired private CommunicationRepository communicationRepository;
  @Autowired private FileService fileService;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    injectComposer.reset();
  }

  private String createTenant(String label) {
    String tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        label + "-" + tenantId);
    return tenantId;
  }

  /**
   * Persists an inject and its communication under {@code tenantId}, uploads {@code content} under
   * {@code storageTenantId}'s prefix at a path embedding the communication's own id (mirroring
   * {@code ImapService}), and records that path as the communication's attachment.
   *
   * @return the attachment path, as the download endpoint's {@code file} parameter expects it
   */
  private String createAttachment(String tenantId, String storageTenantId, byte[] content)
      throws Exception {
    TenantContext.setCurrentTenant(tenantId);
    Inject inject = injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
    entityManager.flush();

    Communication communication = new Communication();
    communication.setIdentifier(UUID.randomUUID().toString());
    communication.setInject(inject);
    communication.setFrom("participant@acme.test");
    communication.setTo("inject@acme.test");
    communicationRepository.save(communication);
    entityManager.flush();

    String uploadedPath =
        fileService.uploadStream(
            storageTenantId,
            "/communications/" + communication.getId(),
            "report.txt",
            new ByteArrayInputStream(content));
    communication.setAttachments(new String[] {uploadedPath});
    communicationRepository.save(communication);
    entityManager.flush();
    return uploadedPath;
  }

  @Test
  @DisplayName(
      "given_attachmentOfAnotherTenant_when_callerIsAuthorizedForIt_should_downloadFromItsOwnTenant")
  void
      given_attachmentOfAnotherTenant_when_callerIsAuthorizedForIt_should_downloadFromItsOwnTenant()
          throws Exception {
    // Arrange - an inject (and its communication) in a tenant of its own, not the default one, the
    // caller authorized for it by membership rather than by the ambient tenant
    String tenantId = createTenant("attachment-download");
    tenantHelper.attachCurrentUserToTenant(tenantId);
    byte[] content = "attachment-body".getBytes(StandardCharsets.UTF_8);
    String uploadedPath = createAttachment(tenantId, tenantId, content);

    // The non-prefixed route never sets the ambient TenantContext: it falls back to the default
    // tenant, whatever tenant actually owns the file.
    TenantContext.clearCurrentTenant();

    // Act
    MockHttpServletResponse response =
        mvc.perform(get("/api/communications/attachment").param("file", uploadedPath))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

    // Assert
    assertThat(response.getContentAsByteArray()).isEqualTo(content);
  }

  @Test
  @DisplayName(
      "given_attachmentOfAnotherTenant_when_callerIsNotAuthorizedForIt_should_returnNotFound")
  @WithMockUser(
      withCapabilities = {Capability.ACCESS_ASSESSMENT},
      autoJoinDefaultTenant = true)
  void given_attachmentOfAnotherTenant_when_callerIsNotAuthorizedForIt_should_returnNotFound()
      throws Exception {
    // Arrange - a non-admin caller authorized only in the default tenant; the attachment belongs
    // to another tenant's inject, whose path the caller knows without ever being granted it
    String tenantId = createTenant("attachment-download-forbidden");
    byte[] content = "attachment-body".getBytes(StandardCharsets.UTF_8);
    String uploadedPath = createAttachment(tenantId, tenantId, content);

    TenantContext.clearCurrentTenant();

    // Act / Assert
    mvc.perform(get("/api/communications/attachment").param("file", uploadedPath))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "given_attachmentStoredUnderTheDefaultTenantPrefix_when_itsOwnerIsAnotherTenant_should_fallBackToIt")
  void
      given_attachmentStoredUnderTheDefaultTenantPrefix_when_itsOwnerIsAnotherTenant_should_fallBackToIt()
          throws Exception {
    // Arrange - an attachment uploaded before the storage path fix (#7961), under the default
    // tenant's prefix, for an inject that belongs to another tenant
    String tenantId = createTenant("attachment-download-legacy");
    tenantHelper.attachCurrentUserToTenant(tenantId);
    byte[] content = "legacy-attachment-body".getBytes(StandardCharsets.UTF_8);
    String uploadedPath = createAttachment(tenantId, Tenant.DEFAULT_TENANT_UUID, content);

    TenantContext.clearCurrentTenant();

    // Act
    MockHttpServletResponse response =
        mvc.perform(get("/api/communications/attachment").param("file", uploadedPath))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

    // Assert
    assertThat(response.getContentAsByteArray()).isEqualTo(content);
  }
}
