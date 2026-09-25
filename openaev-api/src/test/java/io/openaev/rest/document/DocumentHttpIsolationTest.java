package io.openaev.rest.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code documents} activated, the tenant scope isolates the table
 * through the real {@link DocumentApi} endpoints, on both routes: the tenant path ({@code
 * /api/tenants/{tenantId}/documents}) and the {@code X-Tenant-Ids} header. A user who belongs to
 * two tenants reads, lists, creates, updates and deletes a document only under its owning tenant,
 * never another tenant's. This is what replaces the v1 {@code @Filter} and the by-id request-scope
 * guard removed at go-live: reads (primary-key loads included) are scoped by {@code
 * TenantStatementInspector}, and writes are attributed by {@code TenantWriteScopeResolver}.
 *
 * <p>Each test stays on a single tenant path (or a single header) so the per-request scope is set
 * once: re-applying the same scope inside the test transaction is tolerated, changing it inside one
 * transaction hits the aspect's nesting guard. Ground truth (a row that must NOT be touched, or the
 * tenant a create landed in) is read with raw JDBC on the test's own connection, which the
 * statement inspector never rewrites, so a leaked request scope cannot mask the assertion. Objects
 * written to real storage are a side effect and are removed in teardown.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("documents read and write isolation through the real HTTP endpoints, both routes")
class DocumentHttpIsolationTest extends IntegrationTest {

  private static final String DOCUMENTS = "/api/documents";
  private static final String TENANT_DOCUMENTS = "/api/tenants/{tenantId}/documents";
  private static final String DOCUMENT_BY_ID = "/api/tenants/{tenantId}/documents/{documentId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  private String tenantA;
  private String tenantB;
  private String documentA;
  private String documentB;

  // (tenantId, target) of every object written to real storage, removed in teardown.
  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTwoTenantsWithOneDocumentEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("doc-http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("doc-http-iso-b").getId();
    documentA = seedDocument(tenantA, "doc-a");
    documentB = seedDocument(tenantB, "doc-b");
  }

  @AfterEach
  void cleanUploadedObjects() {
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

  // -- READS BY ID, both routes --

  @Test
  @DisplayName("under tenant A's path: A's document is visible, B's is not found")
  void given_documentsUnderTenantAPath_should_exposeOnlyAsDocument() throws Exception {
    mvc.perform(get(DOCUMENT_BY_ID, tenantA, documentA)).andExpect(status().isOk());
    mvc.perform(get(DOCUMENT_BY_ID, tenantA, documentB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: A's document is visible, B's is not found")
  void given_documentsViaHeaderA_should_exposeOnlyAsDocument() throws Exception {
    mvc.perform(get(DOCUMENTS + "/{documentId}", documentA).header("X-Tenant-Ids", tenantA))
        .andExpect(status().isOk());
    mvc.perform(get(DOCUMENTS + "/{documentId}", documentB).header("X-Tenant-Ids", tenantA))
        .andExpect(status().isNotFound());
  }

  // -- LISTS, both routes --

  @Test
  @DisplayName("under tenant A's path: the document list returns A's document and not B's")
  void given_listUnderTenantAPath_should_returnOnlyAsDocument() throws Exception {
    String response =
        mvc.perform(get(TENANT_DOCUMENTS, tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(documentA), "A's document must appear in A's list");
    assertFalse(response.contains(documentB), "B's document must not appear in A's list");
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: the document list returns A's document and not B's")
  void given_listViaHeaderA_should_returnOnlyAsDocument() throws Exception {
    String response =
        mvc.perform(get(DOCUMENTS).header("X-Tenant-Ids", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(documentA), "A's document must appear when A is selected via header");
    assertFalse(response.contains(documentB), "B's document must not appear");
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's document and not B's")
  void given_searchUnderTenantAPath_should_returnOnlyAsDocument() throws Exception {
    String response =
        mvc.perform(
                post(TENANT_DOCUMENTS + "/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(documentA), "A's document must appear in A's search results");
    assertFalse(response.contains(documentB), "B's document must not appear in A's search results");
  }

  // -- CREATE ATTRIBUTION, both routes, and the no-selector refusal --

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void given_createUnderTenantAPath_should_attributeRowToA() throws Exception {
    String id = uploadDocument(multipart(TENANT_DOCUMENTS, tenantA), null);
    assertEquals(tenantA, rowTenant(id), "the document created under A's path must belong to A");
  }

  @Test
  @DisplayName("a create via the X-Tenant-Ids header is attributed to the header tenant")
  void given_createViaHeaderA_should_attributeRowToA() throws Exception {
    String id = uploadDocument(multipart(DOCUMENTS), tenantA);
    assertEquals(
        tenantA, rowTenant(id), "the document created with X-Tenant-Ids: A must belong to A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void given_createWithoutSelector_should_return400() throws Exception {
    MockPart inputPart = new MockPart("input", "{}".getBytes(StandardCharsets.UTF_8));
    inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    MockMultipartFile filePart =
        new MockMultipartFile(
            "file",
            "no-selector-" + UUID.randomUUID() + ".txt",
            MediaType.TEXT_PLAIN_VALUE,
            "no-selector".getBytes(StandardCharsets.UTF_8));
    mvc.perform(multipart(DOCUMENTS).part(inputPart).file(filePart).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  // -- UPDATE / DELETE cross-tenant, both routes, with ground-truth checks --

  @Test
  @DisplayName("under tenant A's path: updating B's document is not found and leaves it untouched")
  void given_updateUnderTenantAPathOfBDocument_should_return404AndKeepName() throws Exception {
    mvc.perform(
            put(DOCUMENT_BY_ID, tenantA, documentB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document_description\":\"hijacked\"}")
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("doc-b", rawName(documentB), "B's document must be untouched by tenant A");
  }

  @Test
  @DisplayName(
      "via the X-Tenant-Ids header: updating B's document is not found and leaves it untouched")
  void given_updateViaHeaderAOfBDocument_should_return404AndKeepName() throws Exception {
    mvc.perform(
            put(DOCUMENTS + "/{documentId}", documentB)
                .header("X-Tenant-Ids", tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document_description\":\"hijacked\"}")
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(
        "doc-b", rawName(documentB), "B's document must be untouched by the header tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: A deletes its own document")
  void given_deleteUnderTenantAPathOfOwnDocument_should_removeRow() throws Exception {
    mvc.perform(delete(DOCUMENT_BY_ID, tenantA, documentA).with(csrf())).andExpect(status().isOk());
    assertEquals(0L, rawCount(documentA), "A's own document must be deleted");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's document is not found and leaves it in place")
  void given_deleteUnderTenantAPathOfBDocument_should_return404AndKeepRow() throws Exception {
    mvc.perform(delete(DOCUMENT_BY_ID, tenantA, documentB).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount(documentB), "B's document must survive tenant A's delete attempt");
  }

  @Test
  @DisplayName(
      "via the X-Tenant-Ids header: deleting B's document is not found and leaves it in place")
  void given_deleteViaHeaderAOfBDocument_should_return404AndKeepRow() throws Exception {
    mvc.perform(
            delete(DOCUMENTS + "/{documentId}", documentB)
                .header("X-Tenant-Ids", tenantA)
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount(documentB), "B's document must survive the header tenant A's delete");
  }

  // -- helpers --

  /**
   * Seeds a metadata-only document owned by {@code tenantId} with a native {@code INSERT ...
   * VALUES} (the inspector does not block VALUES inserts). Seeding through raw JDBC rather than
   * {@code entityManager.persist} is deliberate: a persisted entity stays in the first-level cache,
   * so the request's {@code findById} would be a cache hit that never issues SQL and never reaches
   * the inspector, and the by-id isolation assertions would test nothing.
   */
  private String seedDocument(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    String target = UUID.randomUUID() + ".txt";
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "INSERT INTO documents (document_id, document_name, document_target,"
                          + " document_type, tenant_id) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, name);
                statement.setString(3, target);
                statement.setString(4, MediaType.TEXT_PLAIN_VALUE);
                statement.setString(5, tenantId);
                statement.executeUpdate();
              }
            });
    return id;
  }

  private String uploadDocument(MockMultipartHttpServletRequestBuilder request, String tenantHeader)
      throws Exception {
    if (tenantHeader != null) {
      request.header("X-Tenant-Ids", tenantHeader);
    }
    MockPart inputPart = new MockPart("input", "{}".getBytes(StandardCharsets.UTF_8));
    inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    MockMultipartFile filePart =
        new MockMultipartFile(
            "file",
            "doc-http-iso-" + UUID.randomUUID() + ".txt",
            MediaType.TEXT_PLAIN_VALUE,
            ("doc-http-iso-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    String response =
        mvc.perform(request.part(inputPart).file(filePart).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(response, "$.document_id");
    uploadedObjects.add(new String[] {rowTenant(id), rawTarget(id)});
    return id;
  }

  /**
   * Raw-JDBC read of a document column on the test's own connection. It bypasses the statement
   * inspector, so a request that leaked its tenant scope into the test transaction cannot make a
   * cross-tenant ground-truth read come back empty.
   */
  private String rawColumn(String documentId, String column) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT " + column + " FROM documents WHERE document_id = ?")) {
                statement.setString(1, documentId);
                try (ResultSet resultSet = statement.executeQuery()) {
                  return resultSet.next() ? resultSet.getString(1) : null;
                }
              }
            });
  }

  private String rawName(String documentId) {
    return rawColumn(documentId, "document_name");
  }

  private String rawTarget(String documentId) {
    return rawColumn(documentId, "document_target");
  }

  private String rowTenant(String documentId) {
    return rawColumn(documentId, "tenant_id");
  }

  private long rawCount(String documentId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM documents WHERE document_id = ?")) {
                statement.setString(1, documentId);
                try (ResultSet resultSet = statement.executeQuery()) {
                  resultSet.next();
                  return resultSet.getLong(1);
                }
              }
            });
  }
}
