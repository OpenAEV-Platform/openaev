package io.openaev.rest.document;

import static io.openaev.rest.document.DocumentApi.TENANT_DOCUMENT_API;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Endpoints that return the raw {@code Document} entity serialize its lazy {@code document_tags},
 * {@code document_exercises} and {@code document_scenarios} through {@code MultiIdSetSerializer}
 * open-in-view, after the controller transaction has committed. The tenant scope is
 * transaction-local, so a lazy load at that point runs unscoped and fails closed: with {@code
 * documents} and {@code tags} active the arrays come back empty although the links exist. The
 * associations must be initialized inside the scoped transaction.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the scope would still be set during serialization and mask the failure. Rows are
 * seeded through an auto-committing {@link JdbcTemplate} and removed on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents,tags")
@WithMockUser(isAdmin = true)
@DisplayName("Raw document responses serialize their links with documents v2-activated")
class DocumentSerializationTenantScopeTest extends IntegrationTest {

  private static final String TENANT_PLAYER_DOCUMENTS_API =
      "/api/tenants/{tenantId}/player/{exerciseOrScenarioId}/documents";
  private static final String TENANT_PLAYER_SCENARIO_DOCUMENTS_API =
      "/api/tenants/{tenantId}/player/scenarios/{scenarioId}/documents";
  private static final String TENANT_PLAYER_SIMULATION_DOCUMENTS_API =
      "/api/tenants/{tenantId}/player/simulations/{simulationId}/documents";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenant;
  private String documentId;
  private String tagId;
  private String exerciseId;
  private String scenarioId;
  private final List<String[]> seededRows = new ArrayList<>();

  @BeforeEach
  void seedLinkedDocument() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("doc-serialization").getId();
    tagId = seedTag();
    exerciseId = seedExercise();
    scenarioId = seedScenario();
    documentId = seedDocument();
    link("documents_tags", "document_id", documentId, "tag_id", tagId);
    link("exercises_documents", "document_id", documentId, "exercise_id", exerciseId);
    link("scenarios_documents", "document_id", documentId, "scenario_id", scenarioId);
  }

  @AfterEach
  void cleanup() {
    // Reverse insertion order: links and children before the rows they reference.
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    tenantHelper.deleteCommittedTenants(tenant);
  }

  @Nested
  @DisplayName("Document endpoints")
  class DocumentEndpoints {

    @Test
    @DisplayName("given a linked document when read by id then its three link arrays are filled")
    void given_linkedDocument_should_serializeLinksOnRead() throws Exception {
      // -- Act & Assert --
      assertLinksSerialized(
          mvc.perform(get(TENANT_DOCUMENT_API + "/{documentId}", tenant, documentId)), "$");
    }

    @Test
    @DisplayName("given a document without links when read by id then its arrays are empty")
    void given_unlinkedDocument_should_serializeEmptyLinks() throws Exception {
      // -- Arrange --
      String unlinked = seedDocument();

      // -- Act & Assert --
      // Empty here proves the assertions above track the real links and are not always filled.
      mvc.perform(get(TENANT_DOCUMENT_API + "/{documentId}", tenant, unlinked))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.document_tags[*]", empty()))
          .andExpect(jsonPath("$.document_exercises[*]", empty()))
          .andExpect(jsonPath("$.document_scenarios[*]", empty()));
    }

    @Test
    @DisplayName(
        "given a linked document when its tags are updated then the response carries its parents")
    void given_linkedDocument_should_serializeLinksOnTagsUpdate() throws Exception {
      // -- Act & Assert --
      assertLinksSerialized(
          mvc.perform(
              put(TENANT_DOCUMENT_API + "/{documentId}/tags", tenant, documentId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"tags\": [\"" + tagId + "\"]}")
                  .with(csrf())),
          "$");
    }

    @Test
    @DisplayName("given a linked document when it is updated then the response carries its links")
    void given_linkedDocument_should_serializeLinksOnUpdate() throws Exception {
      // -- Act & Assert --
      assertLinksSerialized(
          mvc.perform(
              put(TENANT_DOCUMENT_API + "/{documentId}", tenant, documentId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"document_description\": \"updated\", \"document_tags\": [\""
                          + tagId
                          + "\"], \"document_exercises\": [\""
                          + exerciseId
                          + "\"], \"document_scenarios\": [\""
                          + scenarioId
                          + "\"]}")
                  .with(csrf())),
          "$");
    }
  }

  @Nested
  @DisplayName("Parent document lists")
  class ParentDocumentLists {

    @Test
    @DisplayName(
        "given a document published to a simulation when its documents are listed then the links"
            + " are filled")
    void given_documentOnSimulation_should_serializeLinksOnSimulationList() throws Exception {
      // -- Arrange --
      seedArticleWithDocument(null, exerciseId);

      // -- Act & Assert --
      assertLinksSerialized(
          mvc.perform(get(TENANT_EXERCISE_URI + "/{exerciseId}/documents", tenant, exerciseId)),
          selector());
    }

    @Test
    @DisplayName(
        "given a document published to a scenario when its documents are listed then the links"
            + " are filled")
    void given_documentOnScenario_should_serializeLinksOnScenarioList() throws Exception {
      // -- Arrange --
      seedArticleWithDocument(scenarioId, null);

      // -- Act & Assert --
      assertLinksSerialized(
          mvc.perform(get(TENANT_SCENARIO_URI + "/{scenarioId}/documents", tenant, scenarioId)),
          selector());
    }
  }

  @Nested
  @DisplayName("Player document lists")
  class PlayerDocumentLists {

    @Test
    @DisplayName(
        "given a document published to a simulation when the player documents are listed then"
            + " the links are filled")
    void given_documentOnSimulation_should_serializeLinksOnPlayerList() throws Exception {
      // -- Arrange --
      seedArticleWithDocument(null, exerciseId);

      // -- Act & Assert --
      assertLinksSerialized(
          mvc.perform(get(TENANT_PLAYER_DOCUMENTS_API, tenant, exerciseId)), selector());
      assertLinksSerialized(
          mvc.perform(get(TENANT_PLAYER_SIMULATION_DOCUMENTS_API, tenant, exerciseId)), selector());
    }

    @Test
    @DisplayName(
        "given a document published to a scenario when the player documents are listed then the"
            + " links are filled")
    void given_documentOnScenario_should_serializeLinksOnPlayerList() throws Exception {
      // -- Arrange --
      seedArticleWithDocument(scenarioId, null);

      // -- Act & Assert --
      assertLinksSerialized(
          mvc.perform(get(TENANT_PLAYER_DOCUMENTS_API, tenant, scenarioId)), selector());
      assertLinksSerialized(
          mvc.perform(get(TENANT_PLAYER_SCENARIO_DOCUMENTS_API, tenant, scenarioId)), selector());
    }
  }

  // -- Helpers --

  private String selector() {
    return "$[?(@.document_id=='" + documentId + "')]";
  }

  private void assertLinksSerialized(ResultActions actions, String node) throws Exception {
    actions
        .andExpect(status().isOk())
        .andExpect(jsonPath(node + ".document_tags[*]", hasItem(tagId)))
        .andExpect(jsonPath(node + ".document_exercises[*]", hasItem(exerciseId)))
        .andExpect(jsonPath(node + ".document_scenarios[*]", hasItem(scenarioId)));
  }

  private String seedDocument() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_type, document_target,"
            + " tenant_id) VALUES (?, ?, 'text/plain', ?, ?)",
        id,
        "doc-serialization-" + id,
        id + ".txt",
        tenant);
    seededRows.add(new String[] {"documents", "document_id", id});
    return id;
  }

  private String seedTag() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "doc-serialization-" + id,
        tenant);
    seededRows.add(new String[] {"tags", "tag_id", id});
    return id;
  }

  private String seedExercise() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "doc-serialization-" + id,
        tenant);
    seededRows.add(new String[] {"exercises", "exercise_id", id});
    return id;
  }

  private String seedScenario() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "doc-serialization-" + id,
        tenant);
    seededRows.add(new String[] {"scenarios", "scenario_id", id});
    return id;
  }

  /** An article of a channel carrying the document, which publishes it to the players. */
  private void seedArticleWithDocument(String articleScenarioId, String articleExerciseId) {
    String channelId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO channels (channel_id, channel_type, channel_name, tenant_id)"
            + " VALUES (?, 'newsletter', ?, ?)",
        channelId,
        "doc-serialization-" + channelId,
        tenant);
    seededRows.add(new String[] {"channels", "channel_id", channelId});
    String articleId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO articles (article_id, article_channel, article_scenario, article_exercise,"
            + " article_name) VALUES (?, ?, ?, ?, ?)",
        articleId,
        channelId,
        articleScenarioId,
        articleExerciseId,
        "doc-serialization-" + articleId);
    seededRows.add(new String[] {"articles", "article_id", articleId});
    link("articles_documents", "article_id", articleId, "document_id", documentId);
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
