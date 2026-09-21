package io.openaev.rest.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A document reached through a parent (an inject attachment, an exercise or scenario export, a
 * player-visible article) is served only under the tenant of that parent. A document owned by
 * tenant B, made reachable through a parent owned by tenant A, must not have its bytes served: the
 * injector must not attach it, the export must not bundle it, the player download must return the
 * same 404 as a missing file. A same-tenant attachment still works on each path.
 *
 * <p>Rows are seeded directly with explicit tenants (the binding hole that forms a cross-tenant
 * association is out of scope here; this pins that the read stays closed however the row was
 * formed). Ground truth is read from the response; the test transaction rolls back and the MinIO
 * objects are removed on teardown.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Document bytes are served only under the tenant that owns the parent")
class DocumentAttachmentScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;
  @Autowired private InjectorContext injectorContext;
  @Autowired private EmailService emailService;
  @Autowired private InjectExpectationService injectExpectationService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;

  private String tenantB;

  // (tenantId, objectTarget) of every object written to real MinIO, removed on teardown: objects
  // are a real side effect and are not rolled back with the test transaction.
  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTenantB() throws Exception {
    injectComposer.reset();
    documentComposer.reset();
    injectorContractComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("attach-scope-b").getId();
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
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Injector attachment resolution")
  class InjectorAttachments {

    @Test
    @DisplayName(
        "given a cross-tenant attachment when the injector resolves it then only the inject-tenant"
            + " document is attached")
    void
        given_a_cross_tenant_attachment_when_the_injector_resolves_it_then_only_the_inject_tenant_document_is_attached()
            throws Exception {
      // -- Arrange --
      String tenantA = tenantHelper.createTenantWithCurrentUser("attach-inj-a").getId();
      String inScopeId =
          seedDocumentInTenant(tenantA, UUID.randomUUID() + ".txt", "in-scope".getBytes());
      String crossTenantId =
          seedDocumentInTenant(tenantB, UUID.randomUUID() + ".txt", "cross-tenant".getBytes());
      // The inject the injector runs for belongs to tenant A; the attachment resolution must serve
      // documents under A, never under the document's own tenant.
      Inject injectA = new Inject();
      injectA.setTenant(new Tenant(tenantA));
      Injection injection = mock(Injection.class);
      when(injection.getInject()).thenReturn(injectA);
      ExecutableInject executableInject =
          new ExecutableInject(
              false, false, injection, List.of(), List.of(), List.of(), List.of(), List.of());
      EmailExecutor injector =
          new EmailExecutor(injectorContext, emailService, injectExpectationService);
      Document inScope = entityManager.find(Document.class, inScopeId);
      Document crossTenant = entityManager.find(Document.class, crossTenantId);

      // -- Act --
      List<DataAttachment> resolved =
          injector.resolveAttachments(
              new Execution(true), executableInject, List.of(inScope, crossTenant));

      // -- Assert --
      assertEquals(
          1,
          resolved.size(),
          "only the same-tenant document must be attached, not the cross-tenant one");
      assertEquals(
          inScopeId,
          resolved.getFirst().id(),
          "the attached document must be the one owned by the inject's tenant");
    }
  }

  @Nested
  @DisplayName("Inject export")
  class InjectExport {

    @Test
    @DisplayName(
        "given a cross-tenant attachment when exporting on the header route then only the"
            + " in-scope bytes are bundled")
    void
        given_a_cross_tenant_attachment_when_exporting_on_the_header_route_then_only_the_in_scope_bytes_are_bundled()
            throws Exception {
      // -- Arrange --
      ExportableInject exportable = buildExportableInjectWithCrossTenantAttachment();

      // -- Act --
      byte[] zip =
          mvc.perform(
                  post("/api/injects/{id}/inject_export", exportable.injectId)
                      .content("{}")
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      // -- Assert --
      assertZipContainsOnlyInScope(zip, exportable);
    }

    @Test
    @DisplayName(
        "given a cross-tenant attachment when exporting on the prefixed route then only the"
            + " in-scope bytes are bundled")
    void
        given_a_cross_tenant_attachment_when_exporting_on_the_prefixed_route_then_only_the_in_scope_bytes_are_bundled()
            throws Exception {
      // -- Arrange --
      ExportableInject exportable = buildExportableInjectWithCrossTenantAttachment();

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
      assertZipContainsOnlyInScope(zip, exportable);
    }
  }

  @Nested
  @DisplayName("Player download")
  class PlayerDownload {

    @Test
    @DisplayName(
        "given an article carrying a cross-tenant document when a player downloads it then the"
            + " same-tenant document is served and the cross-tenant one returns 404")
    void
        given_an_article_carrying_a_cross_tenant_document_when_a_player_downloads_it_then_only_the_same_tenant_document_is_served()
            throws Exception {
      // -- Arrange --
      // The exercise, its channel and its in-scope document live in a tenant the caller is a member
      // of, so the prefixed player route resolves the exercise; the article also carries a document
      // owned by another tenant, whose bytes must not be served.
      String exerciseTenant = tenantHelper.createTenantWithCurrentUser("attach-player-a").getId();
      String inScopeId =
          seedDocumentInTenant(exerciseTenant, UUID.randomUUID() + ".txt", "in-scope".getBytes());
      String crossTenantId =
          seedDocumentInTenant(tenantB, UUID.randomUUID() + ".txt", "cross-tenant".getBytes());
      Channel channel = ChannelFixture.getDefaultChannel();
      channel.setTenant(new Tenant(exerciseTenant));
      entityManager.persist(channel);
      Exercise exercise = ExerciseFixture.createDefaultExercise();
      exercise.setTenant(new Tenant(exerciseTenant));
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

      // -- Act & Assert -- the exercise's own document is served
      assertEquals(
          "in-scope",
          new String(
              mvc.perform(
                      get(
                          "/api/tenants/{t}/player/{ex}/documents/{doc}/file",
                          exerciseTenant,
                          exercise.getId(),
                          inScopeId))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsByteArray(),
              StandardCharsets.UTF_8),
          "the exercise's own document must still be served by the player download");

      // -- Act & Assert -- the cross-tenant document is refused with the same 404 as a missing file
      mvc.perform(
              get(
                  "/api/tenants/{t}/player/{ex}/documents/{doc}/file",
                  exerciseTenant,
                  exercise.getId(),
                  crossTenantId))
          .andExpect(status().isNotFound());
    }
  }

  // region helpers

  private record ExportableInject(
      String injectId, String injectTenant, String inScopeTarget, String crossTenantTarget) {}

  /**
   * Builds an inject with a same-tenant attachment (through the composer) and a cross-tenant
   * attachment (a document owned by tenant B, linked through a raw {@link InjectDocument} row),
   * then flushes and clears so the export endpoint reloads the full attachment set.
   */
  private ExportableInject buildExportableInjectWithCrossTenantAttachment() throws Exception {
    // Build the inject (and its same-tenant attachment) under a tenant the caller is a member of,
    // so the prefixed export route authorises and the same-tenant attachment resolves under it.
    String tenantA = tenantHelper.createTenantWithCurrentUser("attach-export-a").getId();
    tenantHelper.switchToTenant(tenantA, entityManager);
    PlainTextFile inScopeFile =
        new PlainTextFile("attach-export-" + UUID.randomUUID(), UUID.randomUUID() + ".txt");
    DocumentComposer.Composer inScopeDoc =
        documentComposer
            .forDocument(DocumentFixture.getDocument(inScopeFile))
            .withInMemoryFile(inScopeFile);
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

    byte[] crossBytes =
        ("attach-export-cross-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String crossTarget = UUID.randomUUID() + ".txt";
    String crossId = seedDocumentInTenant(tenantB, crossTarget, crossBytes);

    InjectDocument link = new InjectDocument();
    link.setInject(inject);
    link.setDocument(entityManager.getReference(Document.class, crossId));
    link.setAttached(true);
    entityManager.persist(link);
    entityManager.flush();
    entityManager.clear();

    return new ExportableInject(inject.getId(), injectTenant, inScopeTarget, crossTarget);
  }

  private void assertZipContainsOnlyInScope(byte[] zip, ExportableInject exportable) {
    assertTrue(
        zipHasEntry(zip, exportable.inScopeTarget),
        "the export must include the same-tenant attachment's bytes");
    assertThrows(
        IOException.class,
        () -> ZipUtils.getZipEntry(zip, exportable.crossTenantTarget, ZipUtils::streamToBytes),
        "the export must not include the cross-tenant attachment's bytes");
  }

  private boolean zipHasEntry(byte[] zip, String entryName) {
    try {
      ZipUtils.getZipEntry(zip, entryName, ZipUtils::streamToBytes);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Seeds a document owned by {@code tenantId} without a controller call: the object is written
   * under the tenant's own prefix and the row carries that tenant explicitly, matching what an
   * upload attributed to that tenant produces.
   */
  private String seedDocumentInTenant(String tenantId, String target, byte[] content)
      throws Exception {
    minioService.uploadFileForTenant(
        tenantId,
        target,
        new ByteArrayInputStream(content),
        content.length,
        MediaType.APPLICATION_OCTET_STREAM_VALUE);
    uploadedObjects.add(new String[] {tenantId, target});
    Document document = new Document();
    document.setName("attach-seed-" + UUID.randomUUID());
    document.setTarget(target);
    document.setType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    document.setTenant(new Tenant(tenantId));
    entityManager.persist(document);
    entityManager.flush();
    return document.getId();
  }

  // endregion
}
