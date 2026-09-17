package io.openaev.rest.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The document create endpoints attribute the new row AND store its object under the request's
 * write tenant, independently of the ambient {@link TenantContext}. When {@code documents} is
 * activated the {@code TenantBaseListener} that stamps the row from the ambient tenant is removed,
 * so this pins that no create path relies on it: on the non-prefixed route ({@code X-Tenant-Ids})
 * the ambient tenant is the default, but the row and its bytes must land under the header tenant.
 *
 * <p>The row tenant is read with a native query (no active table here, so the statement inspector
 * is inert) and the object placement with a raw MinIO prefix count, so both are ground truth. The
 * test transaction rolls back its rows; uploaded objects are a real side effect and are removed in
 * teardown.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Document create paths attribute the row and its object to the write tenant")
class DocumentCreateAttributionTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  private String tenantB;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTenantB() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("t22-doc-b").getId();
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

  @Nested
  @DisplayName("POST /api/documents and /upsert (new-document branch)")
  class UploadAndUpsert {

    @Test
    @DisplayName("given_uploadOnHeaderRoute_should_attributeRowAndObjectToHeaderTenant")
    void given_uploadOnHeaderRoute_should_attributeRowAndObjectToHeaderTenant() throws Exception {
      // Arrange
      byte[] content = ("t22-upload-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String target = DigestUtils.md5Hex(content) + ".txt";

      // Act
      String id = uploadDocumentWithContent(multipart("/api/documents"), tenantB, content);

      // Assert
      assertEquals(
          tenantB,
          rowTenant(id),
          "the document uploaded with X-Tenant-Ids: B must belong to B, not the default tenant");
      assertEquals(
          1,
          minioService.countObjects(tenantB + "/" + target),
          "the bytes must be stored under tenant B's prefix");
      assertEquals(
          0,
          minioService.countObjects(DEFAULT_TENANT + "/" + target),
          "the bytes must not be stored under the default tenant's prefix");
    }

    @Test
    @DisplayName("given_upsertOnHeaderRoute_should_attributeRowAndObjectToHeaderTenant")
    void given_upsertOnHeaderRoute_should_attributeRowAndObjectToHeaderTenant() throws Exception {
      // Arrange
      byte[] content = ("t22-upsert-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String target = DigestUtils.md5Hex(content) + ".txt";

      // Act
      String id = uploadDocumentWithContent(multipart("/api/documents/upsert"), tenantB, content);

      // Assert
      assertEquals(
          tenantB,
          rowTenant(id),
          "the new document upserted with X-Tenant-Ids: B must belong to B, not the default tenant");
      assertEquals(
          1,
          minioService.countObjects(tenantB + "/" + target),
          "the upserted bytes must be stored under tenant B's prefix");
      assertEquals(
          0,
          minioService.countObjects(DEFAULT_TENANT + "/" + target),
          "the upserted bytes must not be stored under the default tenant's prefix");
    }

    @Test
    @DisplayName("given_uploadOnPrefixedRoute_should_attributeRowAndObjectToPathTenant")
    void given_uploadOnPrefixedRoute_should_attributeRowAndObjectToPathTenant() throws Exception {
      // Arrange
      byte[] content =
          ("t22-upload-prefixed-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String target = DigestUtils.md5Hex(content) + ".txt";

      // Act
      String id =
          uploadDocumentWithContent(
              multipart("/api/tenants/{t}/documents", tenantB), null, content);

      // Assert
      assertEquals(tenantB, rowTenant(id), "the document uploaded under B's path must belong to B");
      assertEquals(
          1,
          minioService.countObjects(tenantB + "/" + target),
          "the bytes must be stored under tenant B's prefix");
    }
  }

  private String uploadDocumentWithContent(
      MockMultipartHttpServletRequestBuilder request, String tenantHeader, byte[] content)
      throws Exception {
    if (tenantHeader != null) {
      request.header("X-Tenant-Ids", tenantHeader);
    }
    MockPart inputPart = new MockPart("input", "{}".getBytes(StandardCharsets.UTF_8));
    inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    MockMultipartFile filePart =
        new MockMultipartFile(
            "file", "t22-" + UUID.randomUUID() + ".txt", MediaType.TEXT_PLAIN_VALUE, content);
    String response =
        mvc.perform(request.part(inputPart).file(filePart).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(response, "$.document_id");
    uploadedObjects.add(new String[] {rowTenant(id), documentTarget(id)});
    return id;
  }

  private String rowTenant(String documentId) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM documents WHERE document_id = ?1")
            .setParameter(1, documentId)
            .getSingleResult();
  }

  private String documentTarget(String documentId) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT document_target FROM documents WHERE document_id = ?1")
            .setParameter(1, documentId)
            .getSingleResult();
  }
}
