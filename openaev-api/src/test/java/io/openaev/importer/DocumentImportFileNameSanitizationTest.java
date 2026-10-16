package io.openaev.importer;

import static io.openaev.rest.document.DocumentService.DEFAULT_DOWNLOAD_FILE_NAME;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The legacy V1 import stores document names the way an upload does: an archive exported before
 * uploads were sanitized can still carry a name with a path, which must not reach the row. A name
 * with no last component falls back to the default download name instead of failing the import.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("The V1 import stores only the last component of document names")
class DocumentImportFileNameSanitizationTest extends IntegrationTest {

  @Autowired private V1_DataImporter importer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantId;
  private String target;

  // The name rules themselves are covered by DocumentServiceTest: these cases only pin that the
  // import applies them, and falls back rather than failing on a name with no last component.
  private static Stream<Arguments> importedNames() {
    return Stream.of(
        Arguments.of("../../evil.txt", "evil.txt"),
        Arguments.of("folder/", DEFAULT_DOWNLOAD_FILE_NAME));
  }

  @BeforeEach
  void seedTenant() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("import-file-name").getId();
    TenantContext.setCurrentTenant(tenantId);
    Mockito.when(enterpriseEditionService.isEnterpriseLicenseInactive(Mockito.any()))
        .thenReturn(false);
  }

  @AfterEach
  void clearContext() {
    if (target != null) {
      try {
        minioService.deleteFileForTenant(tenantId, target);
      } catch (Exception e) {
        // best-effort cleanup: a missing object must not fail teardown
      }
    }
    target = null;
    TenantContext.clearCurrentTenant();
  }

  @ParameterizedTest(name = "\"{0}\" should be stored as \"{1}\"")
  @MethodSource("importedNames")
  @DisplayName("Given an imported document name should store only its last component")
  void given_an_imported_document_name_should_store_only_its_last_component(
      String importedName, String expected) {
    // Arrange
    byte[] bytes = ("import-file-name-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    target = DigestUtils.md5Hex(bytes) + ".txt";
    ObjectNode importNode = importNodeWithOneDocument(importedName, target);
    Map<String, ImportEntry> docReferences =
        Map.of(
            target,
            new ImportEntry(new ZipEntry(target), new ByteArrayInputStream(bytes), bytes.length));

    // Act
    importer.importData(
        TxCtx.forTenant(tenantId),
        importNode,
        docReferences,
        null,
        null,
        null,
        null,
        Constants.IMPORTED_OBJECT_NAME_SUFFIX);

    // Assert
    assertEquals(expected, storedName(target));
  }

  private ObjectNode importNodeWithOneDocument(String documentName, String target) {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();
    ObjectNode doc = mapper.createObjectNode();
    doc.put("document_id", UUID.randomUUID().toString());
    doc.put("document_name", documentName);
    doc.put("document_description", "import file name sanitization");
    doc.put("document_target", target);
    doc.putArray("document_tags");
    root.putArray("inject_documents").add(doc);
    return root;
  }

  private String storedName(String target) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT document_name FROM documents WHERE document_target = ?1")
            .setParameter(1, target)
            .getSingleResult();
  }
}
