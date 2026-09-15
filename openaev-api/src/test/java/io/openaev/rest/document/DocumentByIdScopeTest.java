package io.openaev.rest.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.service.FileService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ReportingFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The document endpoints that take a document id load the row with a primary-key {@code findById},
 * which is exempt from the Hibernate tenant filter, and {@code @AccessControl(DOCUMENT, ...)}
 * checks capabilities, not tenants. Without a request-scope check a caller on {@code
 * /api/tenants/A/documents/{id}} reaches the document, tags, relations, bytes, update or deletion
 * of a document owned by tenant B. This class pins the tenant-prefixed route for the seven by-id
 * endpoints: an out-of-scope caller is refused with the same 404 as a missing document, a
 * same-tenant caller keeps today's behaviour, and a refused write leaves the target row unchanged.
 *
 * <p>Ground truth is read with native queries so the assertions are independent of the tenant
 * filter in effect. Documents are seeded out of band with an explicit tenant, so the request under
 * test is the first to set the scope. The test transaction rolls back; objects written to real
 * object storage are a side effect and are removed in teardown.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Document by-id endpoints hold the request tenant scope on the prefixed route")
class DocumentByIdScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private FileService fileService;

  private String tenantB;

  // (tenantId, target) of every object seeded to real object storage, removed in teardown: uploads
  // are a real side effect and are not rolled back with the test transaction.
  private final List<String[]> seededObjects = new ArrayList<>();

  @BeforeEach
  void seedTenantB() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("doc-scope-b").getId();
  }

  @AfterEach
  void clearContext() {
    for (String[] object : seededObjects) {
      try {
        TenantContext.setCurrentTenant(object[0]);
        fileService.deleteFile(object[1]);
      } catch (Exception e) {
        // best-effort cleanup: a missing object must not fail teardown
      }
    }
    seededObjects.clear();
    TenantContext.clearCurrentTenant();
  }

  // region reads

  @Test
  @DisplayName("GET by id: a caller scoped to another tenant is refused a B document")
  void getByIdIsRefusedForACallerScopedToAnotherTenant() throws Exception {
    String id = seedBDocument();
    String tenantA = tenantHelper.createTenantWithCurrentUser("doc-scope-get-a").getId();

    mvc.perform(get("/api/tenants/{t}/documents/{id}", tenantA, id))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET by id: the owning tenant still receives the document")
  void getByIdSucceedsForTheOwningTenant() throws Exception {
    String id = seedBDocument();

    mvc.perform(get("/api/tenants/{t}/documents/{id}", tenantB, id)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET tags by id: a caller scoped to another tenant is refused a B document")
  void getTagsByIdIsRefusedForACallerScopedToAnotherTenant() throws Exception {
    String id = seedBDocument();
    String tenantA = tenantHelper.createTenantWithCurrentUser("doc-scope-tags-a").getId();

    mvc.perform(get("/api/tenants/{t}/documents/{id}/tags", tenantA, id))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET tags by id: the owning tenant still receives the tags")
  void getTagsByIdSucceedsForTheOwningTenant() throws Exception {
    String id = seedBDocument();

    mvc.perform(get("/api/tenants/{t}/documents/{id}/tags", tenantB, id))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET relations by id: a caller scoped to another tenant is refused a B document")
  void getRelationsByIdIsRefusedForACallerScopedToAnotherTenant() throws Exception {
    String id = seedBDocument();
    String tenantA = tenantHelper.createTenantWithCurrentUser("doc-scope-rel-a").getId();

    mvc.perform(get("/api/tenants/{t}/documents/{id}/relations", tenantA, id))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET relations by id: the owning tenant still receives the relations")
  void getRelationsByIdSucceedsForTheOwningTenant() throws Exception {
    String id = seedBDocument();

    mvc.perform(get("/api/tenants/{t}/documents/{id}/relations", tenantB, id))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("download by id: a caller scoped to another tenant is refused a B document's bytes")
  void downloadByIdIsRefusedForACallerScopedToAnotherTenant() throws Exception {
    byte[] content = ("owner-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String id = seedBDocumentWithObject(content);
    String tenantA = tenantHelper.createTenantWithCurrentUser("doc-scope-dl-a").getId();

    mvc.perform(get("/api/tenants/{t}/documents/{id}/file", tenantA, id))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("download by id: the owning tenant still receives its bytes")
  void downloadByIdSucceedsForTheOwningTenant() throws Exception {
    byte[] content = ("owner-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String id = seedBDocumentWithObject(content);

    assertArrayEquals(
        content,
        mvc.perform(get("/api/tenants/{t}/documents/{id}/file", tenantB, id))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray(),
        "the owning tenant must download its own document");
  }

  // endregion

  // region writes

  @Test
  @DisplayName(
      "PUT tags by id: a caller scoped to another tenant is refused and the B document is"
          + " unchanged")
  void putTagsByIdIsRefusedForACallerScopedToAnotherTenantAndLeavesDocumentUnchanged()
      throws Exception {
    String id = seedBDocument();
    String tenantA = tenantHelper.createTenantWithCurrentUser("doc-scope-puttags-a").getId();
    String tagId = seedTag(tenantA);

    mvc.perform(
            put("/api/tenants/{t}/documents/{id}/tags", tenantA, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tagsBody(tagId))
                .with(csrf()))
        .andExpect(status().isNotFound());

    assertEquals(0, documentTagCount(id), "the refused tag write must not attach a tag to B");
  }

  @Test
  @DisplayName("PUT tags by id: the owning tenant still updates the tags")
  void putTagsByIdSucceedsForTheOwningTenant() throws Exception {
    String id = seedBDocument();
    String tagId = seedTag(tenantB);

    mvc.perform(
            put("/api/tenants/{t}/documents/{id}/tags", tenantB, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tagsBody(tagId))
                .with(csrf()))
        .andExpect(status().isOk());

    assertEquals(1, documentTagCount(id), "the owning tenant must be able to set the tags");
  }

  @Test
  @DisplayName(
      "PUT by id: a caller scoped to another tenant is refused and the B document is unchanged")
  void putByIdIsRefusedForACallerScopedToAnotherTenantAndLeavesDocumentUnchanged()
      throws Exception {
    String id = seedBDocument();
    String tenantA = tenantHelper.createTenantWithCurrentUser("doc-scope-put-a").getId();
    String tagId = seedTag(tenantA);

    mvc.perform(
            put("/api/tenants/{t}/documents/{id}", tenantA, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(tagId))
                .with(csrf()))
        .andExpect(status().isNotFound());

    assertEquals(0, documentTagCount(id), "the refused update must not attach a tag to B");
  }

  @Test
  @DisplayName("PUT by id: the owning tenant still updates the document")
  void putByIdSucceedsForTheOwningTenant() throws Exception {
    String id = seedBDocument();
    String tagId = seedTag(tenantB);

    mvc.perform(
            put("/api/tenants/{t}/documents/{id}", tenantB, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(tagId))
                .with(csrf()))
        .andExpect(status().isOk());

    assertEquals(1, documentTagCount(id), "the owning tenant must be able to update the document");
  }

  @Test
  @DisplayName("delete by id: a caller scoped to another tenant leaves the B row intact")
  void deleteByIdIsRefusedForACallerScopedToAnotherTenant() throws Exception {
    String id = seedBDocument();
    String tenantA = tenantHelper.createTenantWithCurrentUser("doc-scope-del-a").getId();

    mvc.perform(delete("/api/tenants/{t}/documents/{id}", tenantA, id).with(csrf()))
        .andExpect(status().isNotFound());

    assertEquals(1, documentRowCount(id), "the B document row must survive a cross-tenant delete");
  }

  @Test
  @DisplayName("delete by id: the owning tenant still removes its document")
  void deleteByIdSucceedsForTheOwningTenant() throws Exception {
    String id = seedBDocument();

    mvc.perform(delete("/api/tenants/{t}/documents/{id}", tenantB, id).with(csrf()))
        .andExpect(status().isOk());

    assertEquals(0, documentRowCount(id), "the owning tenant must be able to delete its document");
  }

  // endregion

  // region ordering: the scope check runs before the report-output rule on the two PUTs

  @Test
  @DisplayName(
      "PUT tags by id on a report output: an out-of-scope caller gets 404, not the 400 that"
          + " discloses the id is a report output")
  void putTagsByIdOnReportOutputIsRefusedWithNotFoundForAnOutOfScopeCaller() throws Exception {
    // Document owned by B, report generation owned by the caller's path tenant A. On path A the
    // report-output check (existsByDocumentId, filtered by the ambient tenant A) sees the A
    // generation and would answer 400; the scope check must run first and answer 404, the same as
    // for any out-of-scope document.
    String id = seedBDocument();
    String tenantA = tenantHelper.createTenantWithCurrentUser("doc-scope-ro-tags-a").getId();
    seedReportGenerationInTenant(tenantA, id);
    String tagId = seedTag(tenantA);

    mvc.perform(
            put("/api/tenants/{t}/documents/{id}/tags", tenantA, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tagsBody(tagId))
                .with(csrf()))
        .andExpect(status().isNotFound());

    assertEquals(0, documentTagCount(id), "the refused tag write must not attach a tag to B");
  }

  @Test
  @DisplayName(
      "PUT by id on a report output: an out-of-scope caller gets 404, not the 400 that discloses"
          + " the id is a report output")
  void putByIdOnReportOutputIsRefusedWithNotFoundForAnOutOfScopeCaller() throws Exception {
    String id = seedBDocument();
    String tenantA = tenantHelper.createTenantWithCurrentUser("doc-scope-ro-put-a").getId();
    seedReportGenerationInTenant(tenantA, id);
    String tagId = seedTag(tenantA);

    mvc.perform(
            put("/api/tenants/{t}/documents/{id}", tenantA, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(tagId))
                .with(csrf()))
        .andExpect(status().isNotFound());

    assertEquals(0, documentTagCount(id), "the refused update must not attach a tag to B");
  }

  @Test
  @DisplayName(
      "PUT tags by id on a report output: the owning tenant still gets 400 (read-only preserved)")
  void putTagsByIdOnReportOutputStillRefusedForTheOwningTenant() throws Exception {
    String id = seedBDocument();
    seedReportGenerationInTenant(tenantB, id);
    String tagId = seedTag(tenantB);

    mvc.perform(
            put("/api/tenants/{t}/documents/{id}/tags", tenantB, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tagsBody(tagId))
                .with(csrf()))
        .andExpect(status().isBadRequest());

    assertEquals(0, documentTagCount(id), "a report output stays read-only for its owning tenant");
  }

  @Test
  @DisplayName(
      "PUT by id on a report output: the owning tenant still gets 400 (read-only preserved)")
  void putByIdOnReportOutputStillRefusedForTheOwningTenant() throws Exception {
    String id = seedBDocument();
    seedReportGenerationInTenant(tenantB, id);
    String tagId = seedTag(tenantB);

    mvc.perform(
            put("/api/tenants/{t}/documents/{id}", tenantB, id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(tagId))
                .with(csrf()))
        .andExpect(status().isBadRequest());

    assertEquals(0, documentTagCount(id), "a report output stays read-only for its owning tenant");
  }

  // endregion

  // region helpers

  /** Seeds a metadata-only document owned by tenant B, without an object in storage. */
  private String seedBDocument() {
    Document document = new Document();
    document.setName("doc-scope-" + UUID.randomUUID());
    document.setTarget(UUID.randomUUID() + ".txt");
    document.setType(MediaType.TEXT_PLAIN_VALUE);
    document.setTenant(new Tenant(tenantB));
    entityManager.persist(document);
    entityManager.flush();
    return document.getId();
  }

  /** Seeds a document owned by tenant B whose bytes are written under B's storage prefix. */
  private String seedBDocumentWithObject(byte[] content) throws Exception {
    String target = UUID.randomUUID() + ".txt";
    TenantContext.setCurrentTenant(tenantB);
    try {
      fileService.uploadFile(
          target, new ByteArrayInputStream(content), content.length, MediaType.TEXT_PLAIN_VALUE);
    } finally {
      TenantContext.clearCurrentTenant();
    }
    seededObjects.add(new String[] {tenantB, target});
    Document document = new Document();
    document.setName("doc-scope-" + UUID.randomUUID());
    document.setTarget(target);
    document.setType(MediaType.TEXT_PLAIN_VALUE);
    document.setTenant(new Tenant(tenantB));
    entityManager.persist(document);
    entityManager.flush();
    return document.getId();
  }

  /**
   * Seeds a reporting generation owned by {@code ownerTenant} whose output is {@code documentId},
   * so {@code assertNotReportingGenerationOutput} treats the id as a report output when the ambient
   * tenant is {@code ownerTenant}.
   */
  private void seedReportGenerationInTenant(String ownerTenant, String documentId) {
    Reporting reporting = ReportingFixture.createDefaultReporting();
    reporting.setTenant(new Tenant(ownerTenant));
    entityManager.persist(reporting);
    ReportingGeneration generation = new ReportingGeneration();
    generation.setReporting(reporting);
    generation.setFormat(ReportingFormat.PDF);
    generation.setDocument(entityManager.getReference(Document.class, documentId));
    generation.setTenant(new Tenant(ownerTenant));
    entityManager.persist(generation);
    entityManager.flush();
  }

  private String seedTag(String tenantId) {
    Tag tag = new Tag();
    tag.setName("doc-scope-tag-" + UUID.randomUUID());
    tag.setColor("#123456");
    tag.setTenant(new Tenant(tenantId));
    entityManager.persist(tag);
    entityManager.flush();
    return tag.getId();
  }

  private String tagsBody(String tagId) {
    return "{\"tags\":[\"" + tagId + "\"]}";
  }

  private String updateBody(String tagId) {
    return "{\"document_tags\":[\"" + tagId + "\"]}";
  }

  private long documentRowCount(String documentId) {
    entityManager.flush();
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM documents WHERE document_id = ?1")
                .setParameter(1, documentId)
                .getSingleResult())
        .longValue();
  }

  private long documentTagCount(String documentId) {
    entityManager.flush();
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM documents_tags WHERE document_id = ?1")
                .setParameter(1, documentId)
                .getSingleResult())
        .longValue();
  }

  // endregion
}
