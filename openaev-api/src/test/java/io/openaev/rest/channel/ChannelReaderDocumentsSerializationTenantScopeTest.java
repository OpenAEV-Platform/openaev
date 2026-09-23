package io.openaev.rest.channel;

import static io.openaev.injectors.channel.ChannelContract.CHANNEL_PUBLISH;
import static io.openaev.rest.channel.ChannelApi.OBSERVER_CHANNEL_URI;
import static io.openaev.rest.channel.ChannelApi.PLAYER_CHANNEL_URI;
import static io.openaev.rest.channel.ChannelApi.TENANT_OBSERVER_CHANNEL_URI;
import static io.openaev.rest.channel.ChannelApi.TENANT_PLAYER_CHANNEL_URI;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * With {@code documents} on v2 tenant isolation (which the default test properties do NOT
 * exercise), the observer and player channel endpoints return a {@code ChannelReader} holding raw
 * {@code Article} entities and the raw parent {@code Exercise} or {@code Scenario}. Their lazy
 * {@code documents} associations ({@code article_documents}, {@code exercise_documents}, {@code
 * scenario_documents}) are serialized open-in-view, AFTER the controller transaction has committed
 * and the transaction-local tenant scope is gone, so every collection load fails closed to an empty
 * array and the channel pages lose their media with no error. {@code ChannelService} initializes
 * those associations inside the scoped transaction to prevent it.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the tenant-local scope would stay alive through serialization and MASK the very
 * failure this pins. Seeding goes through an auto-committing {@link JdbcTemplate} and is removed on
 * teardown.
 *
 * <p>On the non-prefixed route the parent simulation is looked up in the ambient tenant, the
 * default one on that route until exercises are tenant-active, so the header-route cases seed in
 * the default tenant and select it through {@code X-Tenant-Ids}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("Channel reader endpoints serialize their document links with documents v2-activated")
class ChannelReaderDocumentsSerializationTenantScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenant;
  private final List<Object[]> deletes = new ArrayList<>();

  @BeforeEach
  void seedTenant() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("channel-reader-doc-sink").getId();
  }

  @AfterEach
  void cleanup() {
    // Reverse insertion order: links and children before the rows they reference.
    for (int i = deletes.size() - 1; i >= 0; i--) {
      Object[] delete = deletes.get(i);
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    deletes.clear();
    // The tenant is committed by the helper (the class is not transactional): remove it with its
    // memberships and onboarding rows, so it does not accumulate in the shared database.
    tenantHelper.deleteCommittedTenants(tenant);
  }

  @Nested
  @DisplayName("Observer route")
  class ObserverRoute {

    @Test
    @DisplayName(
        "given a simulation article carrying a document when the observer reads the channel then"
            + " the article and simulation document links are serialized")
    void given_simulationArticleWithDocument_should_serializeArticleAndSimulationDocumentLinks()
        throws Exception {
      // Arrange
      String exerciseId = seedExercise(tenant);
      String channelId = seedChannel(tenant);
      String articleId = seedArticle(channelId, exerciseId, null);
      String articleDocumentId = seedDocument(tenant);
      String exerciseDocumentId = seedDocument(tenant);
      link("articles_documents", "article_id", articleId, "document_id", articleDocumentId);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", exerciseDocumentId);

      // Act
      ResultActions result =
          mvc.perform(
              get(
                      TENANT_OBSERVER_CHANNEL_URI + "/{exerciseId}/{channelId}",
                      tenant,
                      exerciseId,
                      channelId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()));

      // Assert
      // Without the in-scope initialization both lazy associations serialize as [] here: the
      // post-commit open-in-view load runs with the tenant scope cleared and fails closed.
      assertArticleDocuments(result, articleId, articleDocumentId);
      result.andExpect(
          jsonPath("$.channel_exercise.exercise_documents[*]", hasItem(exerciseDocumentId)));
    }

    @Test
    @DisplayName(
        "given a scenario article carrying a document when the observer reads the channel then the"
            + " article and scenario document links are serialized")
    void given_scenarioArticleWithDocument_should_serializeArticleAndScenarioDocumentLinks()
        throws Exception {
      // Arrange
      String scenarioId = seedScenario(tenant);
      String channelId = seedChannel(tenant);
      String articleId = seedArticle(channelId, null, scenarioId);
      String articleDocumentId = seedDocument(tenant);
      String scenarioDocumentId = seedDocument(tenant);
      link("articles_documents", "article_id", articleId, "document_id", articleDocumentId);
      link("scenarios_documents", "scenario_id", scenarioId, "document_id", scenarioDocumentId);

      // Act
      ResultActions result =
          mvc.perform(
              get(
                      TENANT_OBSERVER_CHANNEL_URI + "/{exerciseId}/{channelId}",
                      tenant,
                      scenarioId,
                      channelId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()));

      // Assert
      assertArticleDocuments(result, articleId, articleDocumentId);
      result.andExpect(
          jsonPath("$.channel_scenario.scenario_documents[*]", hasItem(scenarioDocumentId)));
    }

    @Test
    @DisplayName(
        "given an article without document when the observer reads the channel then its document"
            + " links are genuinely empty")
    void given_articleWithoutDocument_should_serializeEmptyDocumentLinks() throws Exception {
      // Arrange
      String exerciseId = seedExercise(tenant);
      String channelId = seedChannel(tenant);
      String articleId = seedArticle(channelId, exerciseId, null);

      // Act
      ResultActions result =
          mvc.perform(
              get(
                      TENANT_OBSERVER_CHANNEL_URI + "/{exerciseId}/{channelId}",
                      tenant,
                      exerciseId,
                      channelId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()));

      // Assert
      // Empty here proves the assertions above track the real links and are not always non-empty.
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath(articleSelector(articleId) + ".article_documents[*]", empty()))
          .andExpect(jsonPath("$.channel_exercise.exercise_documents[*]", empty()));
    }
  }

  @Nested
  @DisplayName("Player route")
  class PlayerRoute {

    @Test
    @DisplayName(
        "given a published simulation article carrying a document when the player reads the channel"
            + " then the article and simulation document links are serialized")
    void
        given_publishedSimulationArticleWithDocument_should_serializeArticleAndSimulationDocumentLinks()
            throws Exception {
      // Arrange
      String exerciseId = seedExercise(tenant);
      String channelId = seedChannel(tenant);
      String articleId = seedArticle(channelId, exerciseId, null);
      String articleDocumentId = seedDocument(tenant);
      String exerciseDocumentId = seedDocument(tenant);
      link("articles_documents", "article_id", articleId, "document_id", articleDocumentId);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", exerciseDocumentId);
      seedPublishedInject(tenant, exerciseId, articleId);

      // Act
      ResultActions result =
          mvc.perform(
              get(
                      TENANT_PLAYER_CHANNEL_URI + "/{exerciseId}/{channelId}",
                      tenant,
                      exerciseId,
                      channelId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()));

      // Assert
      assertArticleDocuments(result, articleId, articleDocumentId);
      result.andExpect(
          jsonPath("$.channel_exercise.exercise_documents[*]", hasItem(exerciseDocumentId)));
    }
  }

  @Nested
  @DisplayName("Header route, ambient tenant on the default one")
  class HeaderRoute {

    @BeforeEach
    void selectDefaultTenantThroughHeader() {
      // The parent simulation is looked up in the ambient tenant on this route, so the rows live in
      // the default tenant and the caller must be a member of it to select it through the header.
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      // The tenant fixtures leave a tenant on the test thread; the request thread of the header
      // route carries none, so the ambient tenant must fall back to the default one here.
      TenantContext.clearCurrentTenant();
      assertEquals(DEFAULT_TENANT, TenantContext.getCurrentTenant());
    }

    @Test
    @DisplayName(
        "given a simulation article carrying a document when the observer reads the channel through"
            + " the header route then the document links are serialized")
    void given_simulationArticleWithDocument_should_serializeDocumentLinksThroughHeader()
        throws Exception {
      // Arrange
      String exerciseId = seedExercise(DEFAULT_TENANT);
      String channelId = seedChannel(DEFAULT_TENANT);
      String articleId = seedArticle(channelId, exerciseId, null);
      String articleDocumentId = seedDocument(DEFAULT_TENANT);
      String exerciseDocumentId = seedDocument(DEFAULT_TENANT);
      link("articles_documents", "article_id", articleId, "document_id", articleDocumentId);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", exerciseDocumentId);

      // Act
      ResultActions result =
          mvc.perform(
              withHeader(
                  get(OBSERVER_CHANNEL_URI + "/{exerciseId}/{channelId}", exerciseId, channelId)));

      // Assert
      assertArticleDocuments(result, articleId, articleDocumentId);
      result.andExpect(
          jsonPath("$.channel_exercise.exercise_documents[*]", hasItem(exerciseDocumentId)));
    }

    @Test
    @DisplayName(
        "given a published simulation article carrying a document when the player reads the channel"
            + " through the header route then the document links are serialized")
    void given_publishedSimulationArticleWithDocument_should_serializeDocumentLinksThroughHeader()
        throws Exception {
      // Arrange
      String exerciseId = seedExercise(DEFAULT_TENANT);
      String channelId = seedChannel(DEFAULT_TENANT);
      String articleId = seedArticle(channelId, exerciseId, null);
      String articleDocumentId = seedDocument(DEFAULT_TENANT);
      String exerciseDocumentId = seedDocument(DEFAULT_TENANT);
      link("articles_documents", "article_id", articleId, "document_id", articleDocumentId);
      link("exercises_documents", "exercise_id", exerciseId, "document_id", exerciseDocumentId);
      seedPublishedInject(DEFAULT_TENANT, exerciseId, articleId);

      // Act
      ResultActions result =
          mvc.perform(
              withHeader(
                  get(PLAYER_CHANNEL_URI + "/{exerciseId}/{channelId}", exerciseId, channelId)));

      // Assert
      assertArticleDocuments(result, articleId, articleDocumentId);
      result.andExpect(
          jsonPath("$.channel_exercise.exercise_documents[*]", hasItem(exerciseDocumentId)));
    }

    private MockHttpServletRequestBuilder withHeader(MockHttpServletRequestBuilder request) {
      return request
          .header(TENANT_HEADER, DEFAULT_TENANT)
          .accept(MediaType.APPLICATION_JSON)
          .with(csrf());
    }
  }

  // -- Helpers --

  private static String articleSelector(String articleId) {
    return "$.channel_articles[?(@.article_id=='" + articleId + "')]";
  }

  private static void assertArticleDocuments(
      ResultActions result, String articleId, String documentId) throws Exception {
    result
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(articleSelector(articleId) + ".article_documents[*]", hasItem(documentId)));
  }

  private String seedExercise(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "channel-reader-" + id,
        tenantId);
    trackDelete("DELETE FROM exercises WHERE exercise_id = ?", id);
    return id;
  }

  private String seedScenario(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "channel-reader-" + id,
        tenantId);
    trackDelete("DELETE FROM scenarios WHERE scenario_id = ?", id);
    return id;
  }

  private String seedChannel(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO channels (channel_id, channel_type, channel_name, tenant_id)"
            + " VALUES (?, 'newsletter', ?, ?)",
        id,
        "channel-reader-" + id,
        tenantId);
    trackDelete("DELETE FROM channels WHERE channel_id = ?", id);
    return id;
  }

  private String seedArticle(String channelId, String exerciseId, String scenarioId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO articles (article_id, article_channel, article_exercise, article_scenario,"
            + " article_name) VALUES (?, ?, ?, ?, ?)",
        id,
        channelId,
        exerciseId,
        scenarioId,
        "channel-reader-" + id);
    trackDelete("DELETE FROM articles WHERE article_id = ?", id);
    return id;
  }

  private String seedDocument(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_type, document_target,"
            + " tenant_id) VALUES (?, ?, 'image/png', ?, ?)",
        id,
        "channel-reader-" + id,
        id + ".png",
        tenantId);
    trackDelete("DELETE FROM documents WHERE document_id = ?", id);
    return id;
  }

  /**
   * The player route only returns the articles a channel publish inject has sent: an inject of the
   * publish contract, already executed, whose content names the article. The contract row is keyed
   * by tenant, so it is seeded in the inject's tenant when the platform has not registered it
   * there.
   */
  private void seedPublishedInject(String tenantId, String exerciseId, String articleId) {
    int inserted =
        jdbc.update(
            "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
                + " injector_contract_manual, tenant_id) VALUES (?, '{}', false, ?)"
                + " ON CONFLICT DO NOTHING",
            CHANNEL_PUBLISH,
            tenantId);
    if (inserted == 1) {
      trackDelete(
          "DELETE FROM injectors_contracts WHERE injector_contract_id = ? AND tenant_id = ?",
          CHANNEL_PUBLISH,
          tenantId);
    }
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_exercise, inject_injector_contract, inject_content,"
            + " tenant_id) VALUES (?, ?, false, true, 0, ?, ?, ?, ?)",
        injectId,
        "channel-reader-" + injectId,
        exerciseId,
        CHANNEL_PUBLISH,
        "{\"articles\":[\"" + articleId + "\"]}",
        tenantId);
    trackDelete("DELETE FROM injects WHERE inject_id = ?", injectId);
    String statusId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects_statuses (status_id, status_inject, status_name, tracking_sent_date)"
            + " VALUES (?, ?, 'EXECUTED', now())",
        statusId,
        injectId);
    trackDelete("DELETE FROM injects_statuses WHERE status_id = ?", statusId);
  }

  private void link(
      String table, String leftColumn, String leftId, String rightColumn, String rightId) {
    jdbc.update(
        "INSERT INTO " + table + " (" + leftColumn + ", " + rightColumn + ") VALUES (?, ?)",
        leftId,
        rightId);
    trackDelete("DELETE FROM " + table + " WHERE " + leftColumn + " = ?", leftId);
  }

  private void trackDelete(String sql, Object... params) {
    Object[] delete = new Object[params.length + 1];
    delete[0] = sql;
    System.arraycopy(params, 0, delete, 1, params.length);
    deletes.add(delete);
  }
}
