package io.openaev.rest.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Communication;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.CommunicationRepository;
import io.openaev.service.FileService;
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
 * A communication attachment is read from the tenant of the inject it belongs to, not the ambient
 * {@link TenantContext}: this endpoint has no tenant-prefixed route, so the ambient tenant is
 * always the default one, whatever tenant actually owns the file.
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

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    injectComposer.reset();
  }

  @Test
  @DisplayName(
      "given_attachmentOfAnotherTenant_when_ambientIsDefault_should_downloadFromItsOwnTenant")
  void given_attachmentOfAnotherTenant_when_ambientIsDefault_should_downloadFromItsOwnTenant()
      throws Exception {
    // Arrange - an inject (and its communication) in a tenant of its own, not the default one
    String tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "attachment-download-" + tenantId);
    TenantContext.setCurrentTenant(tenantId);
    Inject inject = injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
    entityManager.flush();

    byte[] content = "attachment-body".getBytes(StandardCharsets.UTF_8);
    String uploadedPath =
        fileService.uploadStream(
            tenantId,
            "/communications/" + UUID.randomUUID(),
            "report.txt",
            new ByteArrayInputStream(content));

    Communication communication = new Communication();
    communication.setIdentifier(UUID.randomUUID().toString());
    communication.setInject(inject);
    communication.setFrom("participant@acme.test");
    communication.setTo("inject@acme.test");
    communication.setAttachments(new String[] {uploadedPath});
    communicationRepository.save(communication);
    entityManager.flush();

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
}
