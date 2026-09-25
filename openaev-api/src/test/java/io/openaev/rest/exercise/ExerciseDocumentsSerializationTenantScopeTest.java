package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
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
 * The two exercise endpoints that are about documents themselves, the document removal and the logo
 * update, return the raw {@code Exercise} entity, whose lazy {@code exercise_documents} is
 * serialized through {@code MultiIdListSerializer} open-in-view, after the controller transaction
 * has committed. The tenant scope is transaction-local, so a lazy load at that point runs unscoped
 * and fails closed: with {@code documents} active the array comes back empty although the links
 * exist, and the response of the very endpoint that manages those links no longer reflects them.
 * The association must be initialized inside the scoped transaction.
 *
 * <p>The header route is covered with an exercise of the default tenant: the exercise lookup still
 * runs under the ambient tenant, which is the default one on that route, so only a default-tenant
 * exercise is reachable there until {@code exercises} activates. The documents side of the request
 * (the by-id lookups and the collection load) follows the header scope either way.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the scope would still be set during serialization and mask the failure. Rows are
 * seeded through an auto-committing {@link JdbcTemplate} and removed on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("Exercise document endpoints serialize the exercise documents with documents active")
class ExerciseDocumentsSerializationTenantScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenant;
  private final List<String[]> seededRows = new ArrayList<>();

  @BeforeEach
  void seedTenant() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("exercise-documents").getId();
  }

  @AfterEach
  void cleanup() {
    // Reverse insertion order: links before the rows they reference. A row the endpoint under
    // test already deleted makes the statement a no-op.
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    tenantHelper.deleteCommittedTenants(tenant);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Document removal")
  class DocumentRemoval {

    @Test
    @DisplayName(
        "given an exercise with two documents when one is removed then the response lists the"
            + " remaining one")
    void given_exerciseWithTwoDocuments_should_serializeRemainingDocumentOnRemoval()
        throws Exception {
      // -- Arrange --
      String exerciseId = seedExercise(tenant);
      String removed = seedDocument(tenant);
      String kept = seedDocument(tenant);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", removed);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", kept);

      // -- Act & Assert --
      mvc.perform(
              delete(
                      TENANT_EXERCISE_URI + "/{exerciseId}/{documentId}",
                      tenant,
                      exerciseId,
                      removed)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_documents[*]", hasItem(kept)))
          .andExpect(jsonPath("$.exercise_documents[*]", not(hasItem(removed))));
      assertEquals(List.of(kept), linkedDocuments(exerciseId), "only the kept link must remain");
    }

    @Test
    @DisplayName(
        "given a document shared with another exercise when it is removed from one then the"
            + " response lists the remaining document and the other exercise keeps it")
    void given_documentSharedWithAnotherExercise_should_serializeRemainingDocumentOnRemoval()
        throws Exception {
      // -- Arrange --
      String exerciseId = seedExercise(tenant);
      String otherExerciseId = seedExercise(tenant);
      String shared = seedDocument(tenant);
      String kept = seedDocument(tenant);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", shared);
      link("exercises_documents", "exercise_id", otherExerciseId, "document_id", shared);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", kept);

      // -- Act & Assert --
      mvc.perform(
              delete(TENANT_EXERCISE_URI + "/{exerciseId}/{documentId}", tenant, exerciseId, shared)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_documents[*]", hasItem(kept)))
          .andExpect(jsonPath("$.exercise_documents[*]", not(hasItem(shared))));
      assertEquals(List.of(kept), linkedDocuments(exerciseId), "only the kept link must remain");
      assertEquals(
          List.of(shared),
          linkedDocuments(otherExerciseId),
          "the other exercise must keep the shared document");
    }
  }

  @Nested
  @DisplayName("Logo update")
  class LogoUpdate {

    @Test
    @DisplayName(
        "given an exercise with documents when its logos are updated then the response lists the"
            + " documents and the new logo")
    void given_exerciseWithDocuments_should_serializeDocumentsOnLogoUpdate() throws Exception {
      // -- Arrange --
      String exerciseId = seedExercise(tenant);
      String documentId = seedDocument(tenant);
      String logoId = seedDocument(tenant);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", documentId);

      // -- Act & Assert --
      mvc.perform(
              put(TENANT_EXERCISE_URI + "/{exerciseId}/logos", tenant, exerciseId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(logosBody(logoId))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_logo_dark").value(logoId))
          .andExpect(jsonPath("$.exercise_documents[*]", hasItem(documentId)));
    }

    @Test
    @DisplayName(
        "given an exercise already carrying the logos when the same logos are sent then the"
            + " response still lists the documents")
    void given_exerciseAlreadyCarryingTheLogos_should_serializeDocumentsOnUnchangedLogoUpdate()
        throws Exception {
      // -- Arrange --
      // Nothing changes on the exercise row, so no update-time side effect loads the collection
      // for the handler: only an explicit initialization inside the transaction fills it.
      String logoId = seedDocument(tenant);
      String exerciseId = seedExerciseWithLogos(tenant, logoId);
      String documentId = seedDocument(tenant);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", documentId);

      // -- Act & Assert --
      mvc.perform(
              put(TENANT_EXERCISE_URI + "/{exerciseId}/logos", tenant, exerciseId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(logosBody(logoId))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_logo_dark").value(logoId))
          .andExpect(jsonPath("$.exercise_documents[*]", hasItem(documentId)));
    }
  }

  @Nested
  @DisplayName("Header route, ambient tenant on the default one")
  class HeaderRoute {

    @BeforeEach
    void joinDefaultTenant() {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      // The tenant fixtures leave a tenant on the test thread; the request thread of the header
      // route carries none, so the ambient tenant falls back to the default one.
      TenantContext.clearCurrentTenant();
      assertEquals(DEFAULT_TENANT, TenantContext.getCurrentTenant());
    }

    @Test
    @DisplayName(
        "given a default-tenant exercise when a document is removed through the header route then"
            + " the response lists the remaining document")
    void given_defaultTenantExercise_should_serializeRemainingDocumentOnRemovalThroughHeader()
        throws Exception {
      // -- Arrange --
      String exerciseId = seedExercise(DEFAULT_TENANT);
      String removed = seedDocument(DEFAULT_TENANT);
      String kept = seedDocument(DEFAULT_TENANT);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", removed);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", kept);

      // -- Act & Assert --
      mvc.perform(
              delete(EXERCISE_URI + "/{exerciseId}/{documentId}", exerciseId, removed)
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_documents[*]", hasItem(kept)))
          .andExpect(jsonPath("$.exercise_documents[*]", not(hasItem(removed))));
      assertEquals(List.of(kept), linkedDocuments(exerciseId), "only the kept link must remain");
    }

    @Test
    @DisplayName(
        "given a default-tenant exercise when its logos are updated through the header route then"
            + " the response lists the documents")
    void given_defaultTenantExercise_should_serializeDocumentsOnLogoUpdateThroughHeader()
        throws Exception {
      // -- Arrange --
      String exerciseId = seedExercise(DEFAULT_TENANT);
      String documentId = seedDocument(DEFAULT_TENANT);
      String logoId = seedDocument(DEFAULT_TENANT);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", documentId);

      // -- Act & Assert --
      mvc.perform(
              put(EXERCISE_URI + "/{exerciseId}/logos", exerciseId)
                  .header(TENANT_HEADER, DEFAULT_TENANT)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(logosBody(logoId))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_logo_dark").value(logoId))
          .andExpect(jsonPath("$.exercise_documents[*]", hasItem(documentId)));
    }
  }

  // -- Helpers --

  private static String logosBody(String logoId) {
    return "{\"exercise_logo_dark\": \""
        + logoId
        + "\", \"exercise_logo_light\": \""
        + logoId
        + "\"}";
  }

  /** Ground truth on the join table, read outside any scoped transaction. */
  private List<String> linkedDocuments(String exerciseId) {
    return jdbc.queryForList(
        "SELECT document_id FROM exercises_documents WHERE exercise_id = ? ORDER BY document_id",
        String.class,
        exerciseId);
  }

  private String seedExercise(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "exercise-documents-" + id,
        tenantId);
    seededRows.add(new String[] {"exercises", "exercise_id", id});
    return id;
  }

  private String seedExerciseWithLogos(String tenantId, String logoId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_logo_dark,"
            + " exercise_logo_light, tenant_id) VALUES (?, ?, 'noreply@openaev.io', ?, ?, ?)",
        id,
        "exercise-documents-" + id,
        logoId,
        logoId,
        tenantId);
    seededRows.add(new String[] {"exercises", "exercise_id", id});
    return id;
  }

  private String seedDocument(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_type, document_target,"
            + " tenant_id) VALUES (?, ?, 'text/plain', ?, ?)",
        id,
        "exercise-documents-" + id,
        id + ".txt",
        tenantId);
    seededRows.add(new String[] {"documents", "document_id", id});
    return id;
  }

  private void link(
      String table, String leftColumn, String leftId, String rightColumn, String rightId) {
    jdbc.update(
        "INSERT INTO " + table + " (" + leftColumn + ", " + rightColumn + ") VALUES (?, ?)",
        leftId,
        rightId);
    seededRows.add(new String[] {table, leftColumn, leftId});
  }
}
