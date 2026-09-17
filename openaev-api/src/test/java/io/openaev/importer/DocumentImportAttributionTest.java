package io.openaev.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.ImportEntry;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The legacy V1 import attributes a newly created document to the request's write tenant and stores
 * its object under that tenant, independently of the ambient {@link TenantContext}. Once {@code
 * documents} is activated the {@code TenantBaseListener} no longer stamps the row, so the import
 * must attribute it explicitly. This pins that: with the ambient tenant set to another tenant than
 * the import's write scope, the row and its object still land under the write scope.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("The V1 import attributes created documents to the write tenant")
class DocumentImportAttributionTest extends IntegrationTest {

  @Autowired private V1_DataImporter importer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String tenantB;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("t22-import-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("t22-import-b").getId();
    Mockito.when(enterpriseEditionService.isEnterpriseLicenseInactive(Mockito.any()))
        .thenReturn(false);
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
  @DisplayName("given_ambientTenantDiffersFromWriteScope_should_attributeRowAndObjectToWriteScope")
  void given_ambientTenantDiffersFromWriteScope_should_attributeRowAndObjectToWriteScope()
      throws Exception {
    // Arrange: the import writes to tenant B (its TxCtx scope) while the ambient context is A, so a
    // row stamped from the ambient tenant would land in A rather than B.
    byte[] bytes = ("t22-import-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(bytes) + ".txt";
    String documentName = "t22-import-doc-" + UUID.randomUUID() + ".txt";
    ObjectNode importNode = importNodeWithOneDocument(documentName, target);
    Map<String, ImportEntry> docReferences =
        Map.of(
            target,
            new ImportEntry(
                new ZipEntry(documentName), new ByteArrayInputStream(bytes), bytes.length));
    TenantContext.setCurrentTenant(tenantA);

    // Act
    importer.importData(
        TxCtx.forTenant(tenantB),
        importNode,
        docReferences,
        null,
        null,
        null,
        null,
        Constants.IMPORTED_OBJECT_NAME_SUFFIX);
    uploadedObjects.add(new String[] {tenantB, target});

    // Assert
    assertEquals(
        tenantB,
        rowTenant(documentName),
        "the imported document must belong to the write scope (B), not the ambient tenant (A)");
    assertEquals(
        1,
        minioService.countObjects(tenantB + "/" + target),
        "the imported bytes must be stored under the write scope's prefix");
    assertEquals(
        0,
        minioService.countObjects(tenantA + "/" + target),
        "the imported bytes must not be stored under the ambient tenant's prefix");
  }

  private ObjectNode importNodeWithOneDocument(String documentName, String target) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();
    ObjectNode doc = mapper.createObjectNode();
    doc.put("document_id", UUID.randomUUID().toString());
    doc.put("document_name", documentName);
    doc.put("document_description", "t22 import attribution");
    doc.put("document_target", target);
    doc.putArray("document_tags");
    root.putArray("inject_documents").add(doc);
    return root;
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
