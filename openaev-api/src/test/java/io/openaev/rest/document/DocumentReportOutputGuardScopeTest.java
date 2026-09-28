package io.openaev.rest.document;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
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

/**
 * The guard that makes a report generation output read-only from the generic documents surface must
 * answer the same on both routes, now that {@code documents} follows the request scope while {@code
 * reporting_generations} is still scoped by the ambient tenant.
 *
 * <p>On the tenant-prefixed route the two agree, because the interceptor sets the ambient tenant to
 * the path tenant. On the non-prefixed {@code X-Tenant-Ids} route the ambient tenant stays the
 * default one while the request scope is the selected tenant, so a lookup that depends on the
 * ambient tenant answers for the wrong tenant: the caller sees another tenant's report output as
 * updatable and deletable, and the update and the delete go through. The foreign key on {@code
 * reporting_generations.document_id} is {@code ON DELETE SET NULL}, so the delete does not even
 * fail: the generation silently loses its output.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so every read of the request is a statement the inspector
 * rewrites, and they are removed on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("Report output guard on a document, both routes")
class DocumentReportOutputGuardScopeTest extends IntegrationTest {

  private static final String DOCUMENTS = "/api/documents";
  private static final String TENANT_DOCUMENTS = "/api/tenants/{tenantId}/documents";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String READ_ONLY_MESSAGE = "managed by the Reporting module";
  // document_tags has no default in DocumentUpdateInput: omitting it fails validation before the
  // handler runs, which would make the refusal below pass for the wrong reason.
  private static final String UPDATE_BODY =
      "{\"document_description\":\"renamed behind the Reporting module\",\"document_tags\":[]}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String[]> seededRows = new ArrayList<>();
  private final List<String> createdTenants = new ArrayList<>();

  private String tenantB;
  private String documentB;

  @BeforeEach
  void seedAReportOutputInAnotherTenant() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // The caller belongs to the default tenant and to B: the header route can select B while the
    // ambient tenant stays the default one, which is the asymmetry under test.
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
    tenantB = tenantHelper.createTenantWithCurrentUser("doc-report-guard-b").getId();
    createdTenants.add(tenantB);
    documentB = seedDocument(tenantB);
    seedSuccessfulGeneration(tenantB, documentB);
    // Onboarding leaves the new tenant on the test thread; production has no ambient tenant on the
    // non-prefixed route, where it falls back to the default one. Without this the test would run
    // with B ambient and could not see the divergence at all.
    TenantContext.clearCurrentTenant();
    assertEquals(
        DEFAULT_TENANT,
        TenantContext.getCurrentTenant(),
        "the ambient tenant must be the default one, as it is on the non-prefixed route");
  }

  @AfterEach
  void cleanup() {
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    tenantHelper.deleteCommittedTenants(createdTenants.toArray(new String[0]));
    createdTenants.clear();
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("on the non-prefixed route, with the tenant selected by X-Tenant-Ids")
  class OnTheHeaderRoute {

    @Test
    @DisplayName("given a report output of the selected tenant, when it is deleted, then 400")
    void given_reportOutputOfSelectedTenant_should_refuseTheDelete() throws Exception {
      // -- ACT --
      mvc.perform(
              delete(DOCUMENTS + "/{documentId}", documentB)
                  .header(TENANT_HEADER, tenantB)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isBadRequest());
      assertEquals(
          documentB,
          generationDocumentId(),
          "the generation must keep its output; the foreign key sets it to null on a delete");
    }

    @Test
    @DisplayName("given a report output of the selected tenant, when it is updated, then 400")
    void given_reportOutputOfSelectedTenant_should_refuseTheUpdate() throws Exception {
      // -- ACT --
      mvc.perform(
              put(DOCUMENTS + "/{documentId}", documentB)
                  .header(TENANT_HEADER, tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(UPDATE_BODY)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(READ_ONLY_MESSAGE)));
      assertNull(documentDescription(), "the report output must keep its description");
    }

    @Test
    @DisplayName(
        "given a report output of the selected tenant, when documents are searched, then it is neither updatable nor deletable")
    void given_reportOutputOfSelectedTenant_should_flagItReadOnlyInSearch() throws Exception {
      // -- ACT --
      String body =
          mvc.perform(
                  post(DOCUMENTS + "/search")
                      .header(TENANT_HEADER, tenantB)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}")
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertEquals(
          List.of(false),
          JsonPath.read(
              body, "$.content[?(@.document_id == '" + documentB + "')].document_can_be_deleted"),
          "a report output must not be reported as deletable");
      assertEquals(
          List.of(false),
          JsonPath.read(
              body, "$.content[?(@.document_id == '" + documentB + "')].document_can_be_updated"),
          "a report output must not be reported as updatable");
    }
  }

  @Nested
  @DisplayName("on the tenant-prefixed route, where the two scopes already agree")
  class OnThePrefixedRoute {

    @Test
    @DisplayName("given a report output of the path tenant, when it is deleted, then 400")
    void given_reportOutputOfPathTenant_should_refuseTheDelete() throws Exception {
      // -- ACT --
      mvc.perform(delete(TENANT_DOCUMENTS + "/{documentId}", tenantB, documentB).with(csrf()))
          // -- ASSERT --
          .andExpect(status().isBadRequest());
      assertEquals(documentB, generationDocumentId(), "the generation must keep its output");
    }

    @Test
    @DisplayName("given a report output of the path tenant, when it is updated, then 400")
    void given_reportOutputOfPathTenant_should_refuseTheUpdate() throws Exception {
      // -- ACT --
      mvc.perform(
              put(TENANT_DOCUMENTS + "/{documentId}", tenantB, documentB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(UPDATE_BODY)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isBadRequest())
          .andExpect(content().string(containsString(READ_ONLY_MESSAGE)));
      assertNull(documentDescription(), "the report output must keep its description");
    }
  }

  private String documentDescription() {
    return jdbc.queryForObject(
        "SELECT document_description FROM documents WHERE document_id = ?",
        String.class,
        documentB);
  }

  private String generationDocumentId() {
    List<String> ids =
        jdbc.queryForList(
            "SELECT document_id FROM reporting_generations WHERE tenant_id = ?",
            String.class,
            tenantB);
    assertEquals(1, ids.size(), "exactly one generation was seeded in B");
    assertNotNull(ids.getFirst(), "the generation lost its output document");
    return ids.getFirst();
  }

  private String seedDocument(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_target, document_type,"
            + " tenant_id) VALUES (?, ?, ?, ?, ?)",
        id,
        "report-" + UUID.randomUUID() + ".pdf",
        UUID.randomUUID() + ".pdf",
        MediaType.APPLICATION_PDF_VALUE,
        tenantId);
    seededRows.add(new String[] {"documents", "document_id", id});
    return id;
  }

  private void seedSuccessfulGeneration(String tenantId, String documentId) {
    String reportingId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type, tenant_id)"
            + " VALUES (?, ?, ?, ?)",
        reportingId,
        "report-template-" + UUID.randomUUID(),
        ReportingContextType.PLATFORM.name(),
        tenantId);
    seededRows.add(new String[] {"reportings", "reporting_id", reportingId});
    String generationId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO reporting_generations (reporting_generation_id, reporting_id,"
            + " reporting_generation_status, reporting_generation_format, document_id, tenant_id)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        generationId,
        reportingId,
        ReportingGenerationStatus.SUCCESS.name(),
        ReportingFormat.PDF.name(),
        documentId,
        tenantId);
    seededRows.add(new String[] {"reporting_generations", "reporting_generation_id", generationId});
  }
}
