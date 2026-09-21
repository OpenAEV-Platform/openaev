package io.openaev.rest.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A document delete removes the stored object under the tenant that owns the deleted row, not the
 * ambient path. On the header route ({@code X-Tenant-Ids}) the ambient {@link TenantContext} is the
 * default tenant, so deleting through the ambient path would leave the real object behind and,
 * worse, could remove a same-hash object of another tenant. Two tenants hold a document with
 * identical bytes (hence the same object target); deleting tenant B's document must remove only B's
 * object and leave tenant A's untouched.
 *
 * <p>Objects are a real side effect (MinIO deletion is not transactional), so they are seeded
 * through the tenant-explicit MinIO API and asserted with raw prefix counts. Rows are seeded with
 * raw JDBC so the scoped {@code findById}/delete goes through the statement inspector, and removed
 * in teardown.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("A document delete removes the object under the owning tenant, not the ambient path")
class DocumentDeleteFileScopeTest extends IntegrationTest {

  private static final String DOCUMENTS = "/api/documents";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  private String tenantA;
  private String tenantB;
  private String target;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTwoTenantsSharingObject() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("doc-del-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("doc-del-b").getId();
    byte[] content = ("doc-del-shared-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    target = DigestUtils.md5Hex(content) + ".txt";
    // The same bytes stored under each tenant's own prefix: the delete must only touch B's copy.
    uploadObject(tenantA, content);
    uploadObject(tenantB, content);
  }

  @AfterEach
  void clearContext() {
    for (String[] object : uploadedObjects) {
      try {
        minioService.deleteFileForTenant(object[0], object[1]);
      } catch (Exception e) {
        // best-effort cleanup: a missing object must not fail teardown
      }
    }
    uploadedObjects.clear();
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given_deleteViaHeaderTenantB_should_removeBObjectAndKeepASameHashObject")
  void given_deleteViaHeaderTenantB_should_removeBObjectAndKeepASameHashObject() throws Exception {
    // Arrange
    String documentB = seedDocument(tenantB, "doc-del-b");
    seedDocument(tenantA, "doc-del-a");

    // Act: delete B's document on the header route (ambient tenant is the default, not B).
    mvc.perform(
            delete(DOCUMENTS + "/{documentId}", documentB)
                .header("X-Tenant-Ids", tenantB)
                .with(csrf()))
        .andExpect(status().isOk());

    // Assert
    assertEquals(
        0,
        minioService.countObjects(tenantB + "/" + target),
        "B's object must be removed under B's prefix");
    assertEquals(
        1,
        minioService.countObjects(tenantA + "/" + target),
        "A's same-hash object must survive B's delete");
  }

  private void uploadObject(String tenantId, byte[] content) throws Exception {
    minioService.uploadFileForTenant(
        tenantId,
        target,
        new ByteArrayInputStream(content),
        content.length,
        MediaType.TEXT_PLAIN_VALUE);
    uploadedObjects.add(new String[] {tenantId, target});
  }

  private String seedDocument(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "INSERT INTO documents (document_id, document_name, document_target,"
                          + " document_type, tenant_id) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, name + "-" + UUID.randomUUID());
                statement.setString(3, target);
                statement.setString(4, MediaType.TEXT_PLAIN_VALUE);
                statement.setString(5, tenantId);
                statement.executeUpdate();
              }
            });
    return id;
  }
}
