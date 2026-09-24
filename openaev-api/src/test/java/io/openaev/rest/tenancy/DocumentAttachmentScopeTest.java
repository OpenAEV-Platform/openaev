package io.openaev.rest.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Article;
import io.openaev.database.model.Channel;
import io.openaev.database.model.DataAttachment;
import io.openaev.database.model.Document;
import io.openaev.database.model.Execution;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectDocument;
import io.openaev.database.model.Injection;
import io.openaev.database.model.Tenant;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.email.EmailExecutor;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.ZipUtils;
import io.openaev.utils.fixtures.ChannelFixture;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.DocumentComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.files.PlainTextFile;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A document reached through a parent (an inject attachment, an inject export, a player-visible
 * article) is served only under the tenant of that parent. A document owned by tenant B, made
 * reachable through a parent owned by tenant A, must not have its bytes served. A same-tenant
 * attachment still works on each path.
 *
 * <p>With {@code documents} active, two nets refuse the cross-tenant document, and each path pins
 * the one that ships for it. Under a scope limited to the parent's tenant, B's row is invisible to
 * the request: the injector skips the attachment it cannot find, the inject export fails as a whole
 * on the missing row (a 404, not an archive with one file fewer), and the player download answers
 * 404 before any ownership check runs. Under a scope that also holds B (a job running for several
 * tenants, a caller selecting both tenants through the header), the row loads and the owning-tenant
 * check in {@code FileService.getFile(Document, owningTenantId)} refuses the bytes. The second net
 * is what keeps a wider scope from serving the document.
 *
 * <p>Rows are seeded directly with explicit tenants (the binding hole that forms a cross-tenant
 * association is out of scope here; this pins that the read stays closed however the row was
 * formed). The injector cases commit their rows, since the primitive opens its own transaction; the
 * HTTP cases stay transactional and clear the persistence context after seeding, so every read of
 * the request is a statement the inspector rewrites. The MinIO objects are removed on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("Document bytes are served only under the tenant that owns the parent")
class DocumentAttachmentScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;
  @Autowired private InjectorContext injectorContext;
  @Autowired private EmailService emailService;
  @Autowired private InjectExpectationService injectExpectationService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private TenantScopedJobRunner tenantScopedJobRunner;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantB;
  private final List<String> createdTenants = new ArrayList<>();

  // (tenantId, objectTarget) of every object written to real MinIO, removed on teardown: objects
  // are a real side effect and are never rolled back.
  private final List<String[]> uploadedObjects = new ArrayList<>();
  // Committed rows of the injector cases, removed on teardown.
  private final List<String[]> committedRows = new ArrayList<>();

  @BeforeEach
  void seedTenantB() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    injectComposer.reset();
    documentComposer.reset();
    injectorContractComposer.reset();
    tenantB = createTenant("attach-scope-b");
  }

  @AfterEach
  void cleanup() {
    for (String[] object : uploadedObjects) {
      try {
        minioService.deleteFileForTenant(object[0], object[1]);
      } catch (Exception e) {
        // best-effort cleanup
      }
    }
    uploadedObjects.clear();
    for (int i = committedRows.size() - 1; i >= 0; i--) {
      String[] row = committedRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    committedRows.clear();
    tenantHelper.deleteCommittedTenants(createdTenants.toArray(new String[0]));
    createdTenants.clear();
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Injector attachment resolution")
  class InjectorAttachments {

    @Test
    @DisplayName(
        "given a cross-tenant attachment when the injector resolves it under the inject's tenant"
            + " then the cross-tenant document is not found and only the inject-tenant one is"
            + " attached")
    void
        given_a_cross_tenant_attachment_when_the_injector_resolves_it_under_the_inject_tenant_then_only_the_inject_tenant_document_is_attached()
            throws Exception {
      // -- Arrange --
      String tenantA = createTenant("attach-inj-a");
      String inScopeId = seedCommittedDocument(tenantA, "in-scope".getBytes());
      String crossTenantId = seedCommittedDocument(tenantB, "cross-tenant".getBytes());
      EmailExecutor injector = injectorFor();

      // -- Act -- the tenant transaction an inject execution runs under
      List<DataAttachment> resolved =
          tenantScopedJobRunner.supplyInTenant(
              tenantA,
              () ->
                  injector.resolveAttachments(
                      new Execution(true),
                      executableInjectOf(tenantA),
                      List.of(reference(inScopeId), reference(crossTenantId))));

      // -- Assert --
      assertEquals(
          List.of(inScopeId),
          resolved.stream().map(DataAttachment::id).toList(),
          "only the inject-tenant document must be attached; the other is invisible to the scope");
    }

    @Test
    @DisplayName(
        "given a cross-tenant attachment when the injector resolves it under a scope holding both"
            + " tenants then the owning-tenant check still refuses the cross-tenant document")
    void
        given_a_cross_tenant_attachment_when_the_injector_resolves_it_under_a_scope_holding_both_tenants_then_the_owning_tenant_check_refuses_it()
            throws Exception {
      // -- Arrange --
      String tenantA = createTenant("attach-inj-wide-a");
      String inScopeId = seedCommittedDocument(tenantA, "in-scope".getBytes());
      String crossTenantId = seedCommittedDocument(tenantB, "cross-tenant".getBytes());
      EmailExecutor injector = injectorFor();

      // -- Act -- both rows are visible; the inject still belongs to A
      List<DataAttachment> resolved =
          tenantScopedTransaction.execute(
              TxCtx.forTenants(List.of(tenantA, tenantB)),
              () ->
                  injector.resolveAttachments(
                      new Execution(true),
                      executableInjectOf(tenantA),
                      List.of(reference(inScopeId), reference(crossTenantId))));

      // -- Assert --
      assertEquals(
          List.of(inScopeId),
          resolved.stream().map(DataAttachment::id).toList(),
          "a document the scope can see is still refused when it is not the inject's tenant's");
    }

    private EmailExecutor injectorFor() {
      return new EmailExecutor(injectorContext, emailService, injectExpectationService);
    }

    /** The inject the injector runs for belongs to the given tenant. */
    private ExecutableInject executableInjectOf(String tenantId) {
      Inject inject = new Inject();
      inject.setTenant(new Tenant(tenantId));
      Injection injection = mock(Injection.class);
      when(injection.getInject()).thenReturn(inject);
      return new ExecutableInject(
          false, false, injection, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** The injector resolves each attachment by id through the scoped repository. */
    private Document reference(String documentId) {
      Document document = new Document();
      document.setId(documentId);
      return document;
    }
  }

  @Nested
  @Transactional
  @DisplayName("Inject export")
  class InjectExport {

    @Test
    @DisplayName(
        "given a cross-tenant attachment when exporting on the header route then the export"
            + " fails on the document the scope hides")
    void
        given_a_cross_tenant_attachment_when_exporting_on_the_header_route_then_the_export_fails_on_the_hidden_document()
            throws Exception {
      // -- Arrange --
      ExportableInject exportable = buildExportableInject(true);

      // -- Act & Assert --
      mvc.perform(
              post("/api/injects/{id}/inject_export", exportable.injectId)
                  .header(TENANT_HEADER, exportable.injectTenant)
                  .content("{}")
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a cross-tenant attachment when exporting on the prefixed route then the export"
            + " fails on the document the scope hides")
    void
        given_a_cross_tenant_attachment_when_exporting_on_the_prefixed_route_then_the_export_fails_on_the_hidden_document()
            throws Exception {
      // -- Arrange --
      ExportableInject exportable = buildExportableInject(true);

      // -- Act & Assert --
      mvc.perform(
              post(
                      "/api/tenants/{t}/injects/{id}/inject_export",
                      exportable.injectTenant,
                      exportable.injectId)
                  .content("{}")
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given only a same-tenant attachment when exporting on the prefixed route then its bytes"
            + " are bundled")
    void
        given_only_a_same_tenant_attachment_when_exporting_on_the_prefixed_route_then_its_bytes_are_bundled()
            throws Exception {
      // -- Arrange --
      ExportableInject exportable = buildExportableInject(false);

      // -- Act --
      byte[] zip =
          mvc.perform(
                  post(
                          "/api/tenants/{t}/injects/{id}/inject_export",
                          exportable.injectTenant,
                          exportable.injectId)
                      .content("{}")
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      // -- Assert --
      assertTrue(
          zipHasEntry(zip, exportable.inScopeTarget),
          "the export must include the same-tenant attachment's bytes");
    }
  }

  @Nested
  @Transactional
  @DisplayName("Player download")
  class PlayerDownload {

    @Test
    @DisplayName(
        "given an article carrying a cross-tenant document when a player downloads it under the"
            + " exercise's tenant then the same-tenant document is served and the cross-tenant one"
            + " is not found")
    void
        given_an_article_carrying_a_cross_tenant_document_when_a_player_downloads_it_then_only_the_same_tenant_document_is_served()
            throws Exception {
      // -- Arrange --
      // The exercise, its channel and its in-scope document live in a tenant the caller is a member
      // of, so the prefixed player route resolves the exercise; the article also carries a document
      // owned by another tenant, which the scoped read of the article documents never returns.
      String exerciseTenant = createTenant("attach-player-a");
      String inScopeId = seedDocumentInTenant(exerciseTenant, "in-scope".getBytes());
      String crossTenantId = seedDocumentInTenant(tenantB, "cross-tenant".getBytes());
      String exerciseId = seedExerciseWithArticle(exerciseTenant, inScopeId, crossTenantId);

      // -- Act & Assert -- the exercise's own document is served
      assertEquals(
          "in-scope",
          new String(
              mvc.perform(
                      get(
                          "/api/tenants/{t}/player/{ex}/documents/{doc}/file",
                          exerciseTenant,
                          exerciseId,
                          inScopeId))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsByteArray(),
              StandardCharsets.UTF_8),
          "the exercise's own document must still be served by the player download");

      // -- Act & Assert -- the cross-tenant document is not among the documents the scope returns
      mvc.perform(
              get(
                  "/api/tenants/{t}/player/{ex}/documents/{doc}/file",
                  exerciseTenant,
                  exerciseId,
                  crossTenantId))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "given a caller scoped to both tenants when a player downloads the cross-tenant document"
            + " then the owning-tenant check refuses it")
    void
        given_a_caller_scoped_to_both_tenants_when_a_player_downloads_the_cross_tenant_document_then_the_owning_tenant_check_refuses_it()
            throws Exception {
      // -- Arrange --
      // The header route resolves the exercise under the ambient tenant, the default one there, so
      // the parent lives in the default tenant; the caller selects both tenants, so the scoped read
      // of the article documents does return B's row and only the exercise's tenant refuses it.
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String inScopeId = seedDocumentInTenant(DEFAULT_TENANT, "in-scope".getBytes());
      String crossTenantId = seedDocumentInTenant(tenantB, "cross-tenant".getBytes());
      String exerciseId = seedExerciseWithArticle(DEFAULT_TENANT, inScopeId, crossTenantId);
      TenantContext.clearCurrentTenant();
      assertEquals(DEFAULT_TENANT, TenantContext.getCurrentTenant());

      // -- Act & Assert --
      mvc.perform(
              get("/api/player/{ex}/documents/{doc}/file", exerciseId, crossTenantId)
                  .header(TENANT_HEADER, DEFAULT_TENANT + "," + tenantB))
          .andExpect(status().isNotFound());
      mvc.perform(
              get("/api/player/{ex}/documents/{doc}/file", exerciseId, inScopeId)
                  .header(TENANT_HEADER, DEFAULT_TENANT + "," + tenantB))
          .andExpect(status().isOk());
    }

    /** An exercise with one article carrying both documents, flushed and cleared. */
    private String seedExerciseWithArticle(
        String tenantId, String inScopeId, String crossTenantId) {
      Channel channel = ChannelFixture.getDefaultChannel();
      channel.setTenant(new Tenant(tenantId));
      entityManager.persist(channel);
      Exercise exercise = ExerciseFixture.createDefaultExercise();
      exercise.setTenant(new Tenant(tenantId));
      entityManager.persist(exercise);
      Article article = new Article();
      article.setName("attach-article-" + UUID.randomUUID());
      article.setChannel(channel);
      article.setExercise(exercise);
      article.setContent("Lorem");
      article.setDocuments(
          List.of(
              entityManager.getReference(Document.class, inScopeId),
              entityManager.getReference(Document.class, crossTenantId)));
      entityManager.persist(article);
      entityManager.flush();
      entityManager.clear();
      return exercise.getId();
    }
  }

  // region helpers

  private record ExportableInject(String injectId, String injectTenant, String inScopeTarget) {}

  private String createTenant(String name) throws Exception {
    String id = tenantHelper.createTenantWithCurrentUser(name).getId();
    createdTenants.add(id);
    return id;
  }

  /**
   * Builds an inject with a same-tenant attachment (through the composer) and, when asked, a
   * cross-tenant attachment (a document owned by tenant B, linked through a raw {@link
   * InjectDocument} row), then flushes and clears so the export endpoint reloads the full
   * attachment set through statements the inspector rewrites.
   */
  private ExportableInject buildExportableInject(boolean withCrossTenantAttachment)
      throws Exception {
    // Build the inject (and its same-tenant attachment) under a tenant the caller is a member of,
    // so the prefixed export route authorises and the same-tenant attachment resolves under it.
    String tenantA = createTenant("attach-export-a");
    tenantHelper.switchToTenant(tenantA, entityManager);
    PlainTextFile inScopeFile =
        new PlainTextFile("attach-export-" + UUID.randomUUID(), UUID.randomUUID() + ".txt");
    Document inScopeDocument = DocumentFixture.getDocument(inScopeFile);
    // documents is v2-active: the fixture stamps the default tenant, so attribute the in-scope
    // attachment to tenantA explicitly instead of relying on the removed listener + switchToTenant.
    inScopeDocument.setTenant(new Tenant(tenantA));
    DocumentComposer.Composer inScopeDoc =
        documentComposer.forDocument(inScopeDocument).withInMemoryFile(inScopeFile);
    Inject inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(
                injectorContractComposer.forInjectorContract(
                    InjectorContractFixture.createDefaultInjectorContract()))
            .withDocument(inScopeDoc)
            .persist()
            .get();
    String injectTenant = inject.getTenant().getId();
    String inScopeTarget = inScopeDoc.get().getTarget();
    uploadedObjects.add(new String[] {injectTenant, inScopeTarget});

    if (withCrossTenantAttachment) {
      String crossId = seedDocumentInTenant(tenantB, "attach-export-cross".getBytes());
      InjectDocument link = new InjectDocument();
      link.setInject(inject);
      link.setDocument(entityManager.getReference(Document.class, crossId));
      link.setAttached(true);
      entityManager.persist(link);
    }
    entityManager.flush();
    entityManager.clear();

    return new ExportableInject(inject.getId(), injectTenant, inScopeTarget);
  }

  private boolean zipHasEntry(byte[] zip, String entryName) {
    try {
      ZipUtils.getZipEntry(zip, entryName, ZipUtils::streamToBytes);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private String uploadObject(String tenantId, byte[] content) throws Exception {
    String target = UUID.randomUUID() + ".txt";
    minioService.uploadFileForTenant(
        tenantId,
        target,
        new ByteArrayInputStream(content),
        content.length,
        MediaType.APPLICATION_OCTET_STREAM_VALUE);
    uploadedObjects.add(new String[] {tenantId, target});
    return target;
  }

  /**
   * Seeds a document owned by {@code tenantId} in the test transaction, without a controller call:
   * the object is written under the tenant's own prefix and the row carries that tenant explicitly,
   * matching what an upload attributed to that tenant produces.
   */
  private String seedDocumentInTenant(String tenantId, byte[] content) throws Exception {
    String target = uploadObject(tenantId, content);
    Document document = new Document();
    document.setName("attach-seed-" + UUID.randomUUID());
    document.setTarget(target);
    document.setType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    document.setTenant(new Tenant(tenantId));
    entityManager.persist(document);
    entityManager.flush();
    return document.getId();
  }

  /** Same as {@link #seedDocumentInTenant}, committed for a read from another transaction. */
  private String seedCommittedDocument(String tenantId, byte[] content) throws Exception {
    String target = uploadObject(tenantId, content);
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_target, document_type,"
            + " tenant_id) VALUES (?, ?, ?, ?, ?)",
        id,
        "attach-seed-" + id,
        target,
        MediaType.APPLICATION_OCTET_STREAM_VALUE,
        tenantId);
    committedRows.add(new String[] {"documents", "document_id", id});
    return id;
  }

  // endregion
}
