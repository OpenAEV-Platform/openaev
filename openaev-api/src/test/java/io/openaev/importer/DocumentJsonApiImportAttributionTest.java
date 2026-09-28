package io.openaev.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.jsonapi.GenericJsonApiImporter;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The generic JSON:API importer persists documents reflectively and uploads their objects. With
 * {@code documents} activated the {@code TenantBaseListener} no longer stamps the row (a
 * non-nullable {@code tenant_id}), so the importer must attribute the document to the resolved
 * write tenant and store its object under the same tenant, independently of the ambient {@link
 * TenantContext} of the import thread. This pins both: with the ambient tenant set to a different
 * tenant than the write scope, the row and the object land under the write scope.
 *
 * <p>It does not arm {@code documents}: the attribution is the explicit write tenant (the listener
 * is gone), read back with a native query the statement inspector never rewrites, so arming would
 * not change the outcome.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("The JSON:API importer attributes imported documents to the write tenant")
class DocumentJsonApiImportAttributionTest extends IntegrationTest {

  @Autowired private GenericJsonApiImporter importer;
  @Autowired private MinioService minioService;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("jsonapi-import-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("jsonapi-import-b").getId();
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
  @DisplayName("given_ambientTenantDiffersFromWriteScope_should_attributeReflectiveRowToWriteScope")
  @SuppressWarnings("unchecked")
  void given_ambientTenantDiffersFromWriteScope_should_attributeReflectiveRowToWriteScope() {
    // Arrange: a bundle whose root is a document, imported to tenant B while the ambient context is
    // A. A reflectively persisted document with no tenant would violate documents.tenant_id NOT
    // NULL.
    String documentName = "jsonapi-import-doc-" + UUID.randomUUID();
    String target = DigestUtils.md5Hex(documentName) + ".txt";
    ResourceObject data =
        new ResourceObject(
            UUID.randomUUID().toString(),
            "documents",
            Map.of(
                "document_name",
                documentName,
                "document_type",
                "text/plain",
                "document_target",
                target),
            null);
    JsonApiDocument<ResourceObject> doc = new JsonApiDocument<>(data, null);
    TenantContext.setCurrentTenant(tenantA);

    // Act
    importer.handleImportEntity(doc, null, null, tenantB);

    // Assert
    assertEquals(
        tenantB,
        rowTenant(documentName),
        "the reflectively imported document must belong to the write scope (B), not the ambient"
            + " tenant (A)");
  }

  @Test
  @DisplayName("given_ambientTenantDiffersFromWriteScope_should_storeImportedObjectUnderWriteScope")
  @SuppressWarnings("unchecked")
  void given_ambientTenantDiffersFromWriteScope_should_storeImportedObjectUnderWriteScope()
      throws Exception {
    // Arrange: an included document resource carrying its file bytes, imported to tenant B while
    // the
    // ambient context is A.
    byte[] bytes = ("jsonapi-import-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(new ByteArrayInputStream(bytes)) + ".txt";
    ResourceObject includedDocument =
        new ResourceObject(
            UUID.randomUUID().toString(), "document", Map.of("document_name", target), null);
    JsonApiDocument<ResourceObject> doc =
        new JsonApiDocument<>(null, List.of((Object) includedDocument));
    Map<String, byte[]> extras = Map.of(target, bytes);
    TenantContext.setCurrentTenant(tenantA);

    // Act
    importer.handleImportDocument(doc, extras, tenantB);
    uploadedObjects.add(new String[] {tenantB, target});

    // Assert
    assertEquals(
        1,
        minioService.countObjects(tenantB + "/" + target),
        "the imported object must be stored under the write scope's prefix (B)");
    assertEquals(
        0,
        minioService.countObjects(tenantA + "/" + target),
        "the imported object must not be stored under the ambient tenant's prefix (A)");
  }

  private String rowTenant(String documentName) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM documents WHERE document_name = ?1")
            .setParameter(1, documentName)
            .getSingleResult();
  }
}
