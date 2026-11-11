package io.openaev.rest.channel;

import static io.openaev.rest.channel.ChannelApi.CHANNEL_URI;
import static io.openaev.rest.channel.ChannelApi.TENANT_CHANNEL_URI;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The channel logos and the channels and documents named in the body of an article write are read
 * in the tenant of their owner: the channel for the logos, the simulation or the scenario for the
 * articles. An id of a foreign tenant is treated as an unknown id.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so each request runs its own transaction with its own
 * tenant scope, as in production, and they are removed on teardown.
 *
 * <p>Header cases select the default tenant and a second one through {@code X-Tenant-Ids}. On that
 * route the simulation and scenario owners are looked up in the default tenant (the ambient one),
 * so they live there and the foreign channel and documents are the second tenant's. The channel of
 * the logos cases is the second tenant's (channels are v2, the scope covers both) and the foreign
 * logo document is the default tenant's.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Channel logo and article links are read in the tenant of their owner")
class ChannelArticleLinksTenantScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantB;
  private final List<Object[]> deletes = new ArrayList<>();
  private final List<Object[]> firstDeletes = new ArrayList<>();
  private final List<String[]> seeded = new ArrayList<>();

  /** The two kinds of owner of an article, with the tables and routes their writes use. */
  private enum Owner {
    SIMULATION(
        EXERCISE_URI,
        TENANT_EXERCISE_URI,
        "exercises",
        "exercise_id",
        "article_exercise",
        "exercises_documents"),
    SCENARIO(
        SCENARIO_URI,
        TENANT_SCENARIO_URI,
        "scenarios",
        "scenario_id",
        "article_scenario",
        "scenarios_documents");

    final String uri;
    final String tenantUri;
    final String table;
    final String idColumn;
    final String articleColumn;
    final String documentsTable;

    Owner(
        String uri,
        String tenantUri,
        String table,
        String idColumn,
        String articleColumn,
        String documentsTable) {
      this.uri = uri;
      this.tenantUri = tenantUri;
      this.table = table;
      this.idColumn = idColumn;
      this.articleColumn = articleColumn;
      this.documentsTable = documentsTable;
    }
  }

  @BeforeEach
  void seedTenants() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // The caller is a member of the default tenant first, then of a tenant of its own.
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
    tenantB = tenantHelper.createTenantWithCurrentUser("channel-article-links").getId();
    assertNotEquals(DEFAULT_TENANT, tenantB);
  }

  @AfterEach
  void cleanup() {
    for (Object[] delete : firstDeletes) {
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    firstDeletes.clear();
    // Reverse insertion order: links and children before the rows they reference.
    for (int i = deletes.size() - 1; i >= 0; i--) {
      Object[] delete = deletes.get(i);
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    deletes.clear();
    seeded.clear();
    tenantHelper.deleteCommittedTenants(tenantB);
  }

  @Nested
  @DisplayName("Channel logos")
  class ChannelLogos {

    @Test
    @DisplayName(
        "given a dark logo of a foreign tenant when updating the logos then the logo is null")
    void given_foreignDarkLogo_should_setNullDarkLogoThroughHeader() throws Exception {
      // Arrange
      String channelId = seedChannel(tenantB);
      String foreignLogoId = seedDocument(DEFAULT_TENANT);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateLogos(channelId, foreignLogoId, null, false);

      // Assert
      result.andExpect(status().isOk());
      assertNull(channelLogo(channelId, "channel_logo_dark"));
    }

    @Test
    @DisplayName(
        "given a light logo of a foreign tenant when updating the logos then the logo is null")
    void given_foreignLightLogo_should_setNullLightLogoThroughHeader() throws Exception {
      // Arrange
      String channelId = seedChannel(tenantB);
      String foreignLogoId = seedDocument(DEFAULT_TENANT);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateLogos(channelId, null, foreignLogoId, false);

      // Assert
      result.andExpect(status().isOk());
      assertNull(channelLogo(channelId, "channel_logo_light"));
    }

    @Test
    @DisplayName("given a dark logo of the channel's tenant when updating the logos then it is set")
    void given_ownDarkLogo_should_setDarkLogoThroughHeader() throws Exception {
      // Arrange
      String channelId = seedChannel(tenantB);
      String ownLogoId = seedDocument(tenantB);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateLogos(channelId, ownLogoId, null, false);

      // Assert
      result.andExpect(status().isOk());
      assertEquals(ownLogoId, channelLogo(channelId, "channel_logo_dark"));
    }

    @Test
    @DisplayName(
        "given a light logo of the channel's tenant when updating the logos then it is set")
    void given_ownLightLogo_should_setLightLogoThroughHeader() throws Exception {
      // Arrange
      String channelId = seedChannel(tenantB);
      String ownLogoId = seedDocument(tenantB);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateLogos(channelId, null, ownLogoId, false);

      // Assert
      result.andExpect(status().isOk());
      assertEquals(ownLogoId, channelLogo(channelId, "channel_logo_light"));
    }

    @Test
    @DisplayName("given own logos on the tenant route when updating the logos then both are set")
    void given_ownLogosOnTenantRoute_should_setBothLogos() throws Exception {
      // Arrange
      String channelId = seedChannel(tenantB);
      String darkId = seedDocument(tenantB);
      String lightId = seedDocument(tenantB);
      assertSeededTenants();

      // Act
      ResultActions result = updateLogos(channelId, darkId, lightId, true);

      // Assert
      result.andExpect(status().isOk());
      assertEquals(darkId, channelLogo(channelId, "channel_logo_dark"));
      assertEquals(lightId, channelLogo(channelId, "channel_logo_light"));
    }
  }

  abstract class ArticleLinks {

    abstract Owner owner();

    @Test
    @DisplayName(
        "given a channel of a foreign tenant when creating an article then the channel is not found"
            + " and no article is written")
    void given_foreignChannel_should_refuseCreateThroughHeader() throws Exception {
      // Arrange
      String ownerId = seedOwner(owner(), DEFAULT_TENANT);
      String foreignChannelId = seedChannel(tenantB);
      arrangeHeaderRoute();

      // Act
      ResultActions result =
          createArticle(owner(), ownerId, false, articleBody("created", foreignChannelId, null));

      // Assert
      result.andExpect(status().isNotFound());
      assertEquals(0, articleIds(owner(), ownerId).size());
    }

    @Test
    @DisplayName(
        "given an own and a foreign document when creating an article then only the own one is"
            + " linked, to the article and to its owner")
    void given_ownAndForeignDocument_should_linkOnlyOwnDocumentOnCreateThroughHeader()
        throws Exception {
      // Arrange
      String ownerId = seedOwner(owner(), DEFAULT_TENANT);
      String channelId = seedChannel(DEFAULT_TENANT);
      String ownDocumentId = seedDocument(DEFAULT_TENANT);
      String foreignDocumentId = seedDocument(tenantB);
      arrangeHeaderRoute();

      // Act
      ResultActions result =
          createArticle(
              owner(),
              ownerId,
              false,
              articleBody("created", channelId, List.of(ownDocumentId, foreignDocumentId)));

      // Assert
      result.andExpect(status().isOk());
      List<String> articleIds = articleIds(owner(), ownerId);
      assertEquals(1, articleIds.size());
      assertEquals(channelId, articleChannel(articleIds.get(0)));
      assertEquals(List.of(ownDocumentId), articleDocuments(articleIds.get(0)));
      assertEquals(List.of(ownDocumentId), ownerDocuments(owner(), ownerId));
    }

    @Test
    @DisplayName(
        "given a null document id when creating an article then the request is refused and no"
            + " article is written")
    void given_nullDocumentId_should_refuseCreateThroughHeader() throws Exception {
      // Arrange
      String ownerId = seedOwner(owner(), DEFAULT_TENANT);
      String channelId = seedChannel(DEFAULT_TENANT);
      arrangeHeaderRoute();

      // Act
      ResultActions result =
          createArticle(owner(), ownerId, false, articleBody("created", channelId, nullDocument()));

      // Assert
      assertNullDocumentRefused(result);
      assertEquals(0, articleIds(owner(), ownerId).size());
    }

    @Test
    @DisplayName(
        "given a channel of a foreign tenant when updating an article then the channel is not found"
            + " and the article is unchanged")
    void given_foreignChannel_should_refuseUpdateThroughHeader() throws Exception {
      // Arrange
      String ownerId = seedOwner(owner(), DEFAULT_TENANT);
      String channelId = seedChannel(DEFAULT_TENANT);
      String foreignChannelId = seedChannel(tenantB);
      String articleId = seedArticle(channelId, owner(), ownerId);
      arrangeHeaderRoute();

      // Act
      ResultActions result =
          updateArticle(
              owner(), ownerId, articleId, false, articleBody("after", foreignChannelId, null));

      // Assert
      result.andExpect(status().isNotFound());
      assertEquals(channelId, articleChannel(articleId));
      assertEquals("before", articleName(articleId));
    }

    @Test
    @DisplayName(
        "given an own and a foreign new document when updating an article then only the own one is"
            + " linked, to the article and to its owner")
    void given_ownAndForeignDocument_should_linkOnlyOwnDocumentOnUpdateThroughHeader()
        throws Exception {
      // Arrange
      String ownerId = seedOwner(owner(), DEFAULT_TENANT);
      String channelId = seedChannel(DEFAULT_TENANT);
      String articleId = seedArticle(channelId, owner(), ownerId);
      String ownDocumentId = seedDocument(DEFAULT_TENANT);
      String foreignDocumentId = seedDocument(tenantB);
      arrangeHeaderRoute();

      // Act
      ResultActions result =
          updateArticle(
              owner(),
              ownerId,
              articleId,
              false,
              articleBody("after", channelId, List.of(ownDocumentId, foreignDocumentId)));

      // Assert
      result.andExpect(status().isOk());
      assertEquals("after", articleName(articleId));
      assertEquals(List.of(ownDocumentId), articleDocuments(articleId));
      assertEquals(List.of(ownDocumentId), ownerDocuments(owner(), ownerId));
    }

    @Test
    @DisplayName(
        "given a null document id when updating an article then the request is refused and the"
            + " article is unchanged")
    void given_nullDocumentId_should_refuseUpdateThroughHeader() throws Exception {
      // Arrange
      String ownerId = seedOwner(owner(), DEFAULT_TENANT);
      String channelId = seedChannel(DEFAULT_TENANT);
      String articleId = seedArticle(channelId, owner(), ownerId);
      arrangeHeaderRoute();

      // Act
      ResultActions result =
          updateArticle(
              owner(), ownerId, articleId, false, articleBody("after", channelId, nullDocument()));

      // Assert
      assertNullDocumentRefused(result);
      assertEquals("before", articleName(articleId));
      assertEquals(List.of(), articleDocuments(articleId));
    }

    @Test
    @DisplayName(
        "given own ids on the tenant route when creating an article then the channel and the"
            + " document are linked")
    void given_ownIdsOnTenantRoute_should_createArticle() throws Exception {
      // Arrange
      String ownerId = seedOwner(owner(), tenantB);
      String channelId = seedChannel(tenantB);
      String documentId = seedDocument(tenantB);
      assertSeededTenants();

      // Act
      ResultActions result =
          createArticle(
              owner(), ownerId, true, articleBody("created", channelId, List.of(documentId)));

      // Assert
      result.andExpect(status().isOk());
      List<String> articleIds = articleIds(owner(), ownerId);
      assertEquals(1, articleIds.size());
      assertEquals(channelId, articleChannel(articleIds.get(0)));
      assertEquals(List.of(documentId), articleDocuments(articleIds.get(0)));
      assertEquals(List.of(documentId), ownerDocuments(owner(), ownerId));
    }

    @Test
    @DisplayName(
        "given own ids on the tenant route when updating an article then the channel and the"
            + " document are linked")
    void given_ownIdsOnTenantRoute_should_updateArticle() throws Exception {
      // Arrange
      String ownerId = seedOwner(owner(), tenantB);
      String channelId = seedChannel(tenantB);
      String articleId = seedArticle(channelId, owner(), ownerId);
      String documentId = seedDocument(tenantB);
      assertSeededTenants();

      // Act
      ResultActions result =
          updateArticle(
              owner(),
              ownerId,
              articleId,
              true,
              articleBody("after", channelId, List.of(documentId)));

      // Assert
      result.andExpect(status().isOk());
      assertEquals("after", articleName(articleId));
      assertEquals(List.of(documentId), articleDocuments(articleId));
      assertEquals(List.of(documentId), ownerDocuments(owner(), ownerId));
    }
  }

  @Nested
  @DisplayName("Simulation articles")
  class SimulationArticles extends ArticleLinks {

    @Override
    Owner owner() {
      return Owner.SIMULATION;
    }

    @Test
    @DisplayName(
        "given a null channel id when creating an article then the request is a bad request")
    void given_nullChannelId_should_refuseCreateWithBadRequest() throws Exception {
      // Arrange
      String ownerId = seedOwner(owner(), DEFAULT_TENANT);
      arrangeHeaderRoute();

      // Act
      ResultActions result =
          createArticle(owner(), ownerId, false, articleBody("created", null, null));

      // Assert
      result.andExpect(status().isBadRequest());
      assertEquals(0, articleIds(owner(), ownerId).size());
    }
  }

  @Nested
  @DisplayName("Scenario articles")
  class ScenarioArticles extends ArticleLinks {

    @Override
    Owner owner() {
      return Owner.SCENARIO;
    }
  }

  // -- Requests --

  private ResultActions updateLogos(
      String channelId, String darkId, String lightId, boolean tenantRoute) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("channel_logo_dark", darkId);
    body.put("channel_logo_light", lightId);
    MockHttpServletRequestBuilder request =
        tenantRoute
            ? put(TENANT_CHANNEL_URI + "/{channelId}/logos", tenantB, channelId)
            : put(CHANNEL_URI + "/{channelId}/logos", channelId)
                .header(TENANT_HEADER, scopeHeader());
    return perform(request, body);
  }

  private ResultActions createArticle(
      Owner owner, String ownerId, boolean tenantRoute, Map<String, Object> body) throws Exception {
    MockHttpServletRequestBuilder request =
        tenantRoute
            ? post(owner.tenantUri + "/{ownerId}/articles", tenantB, ownerId)
            : post(owner.uri + "/{ownerId}/articles", ownerId).header(TENANT_HEADER, scopeHeader());
    return perform(request, body);
  }

  private ResultActions updateArticle(
      Owner owner, String ownerId, String articleId, boolean tenantRoute, Map<String, Object> body)
      throws Exception {
    MockHttpServletRequestBuilder request =
        tenantRoute
            ? put(owner.tenantUri + "/{ownerId}/articles/{articleId}", tenantB, ownerId, articleId)
            : put(owner.uri + "/{ownerId}/articles/{articleId}", ownerId, articleId)
                .header(TENANT_HEADER, scopeHeader());
    return perform(request, body);
  }

  private ResultActions perform(MockHttpServletRequestBuilder request, Map<String, Object> body)
      throws Exception {
    return mvc.perform(
        request
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(asJsonString(body))
            .with(csrf()));
  }

  private String scopeHeader() {
    return DEFAULT_TENANT + "," + tenantB;
  }

  private static Map<String, Object> articleBody(
      String name, String channelId, List<String> documents) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("article_name", name);
    body.put("article_channel", channelId);
    if (documents != null) {
      body.put("article_documents", documents);
    }
    return body;
  }

  private static List<String> nullDocument() {
    return Arrays.asList((String) null);
  }

  /**
   * A null document id reaches {@code findById(null)}, which Spring Data refuses with an {@code
   * InvalidDataAccessApiUsageException} caused by an {@code IllegalArgumentException}; the REST
   * handler answers 400 with that message and the transaction rolls back.
   */
  private static void assertNullDocumentRefused(ResultActions result) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The given id must not be null"));
  }

  /**
   * Header cases run with the ambient tenant of the header route, the default one: the tenant
   * fixtures leave a tenant on the test thread, which the request thread of that route does not
   * carry. Every seeded row is read back to confirm the tenant the case relies on.
   */
  private void arrangeHeaderRoute() {
    TenantContext.clearCurrentTenant();
    assertEquals(DEFAULT_TENANT, TenantContext.getCurrentTenant());
    assertSeededTenants();
  }

  private void assertSeededTenants() {
    for (String[] row : seeded) {
      String actual =
          jdbc.queryForObject(
              "SELECT tenant_id FROM " + row[0] + " WHERE " + row[1] + " = ?",
              String.class,
              row[2]);
      assertEquals(row[3], actual, row[0] + " " + row[2]);
    }
  }

  // -- Reads --

  private String channelLogo(String channelId, String column) {
    return jdbc.queryForList(
            "SELECT " + column + " FROM channels WHERE channel_id = ?", String.class, channelId)
        .get(0);
  }

  private List<String> articleIds(Owner owner, String ownerId) {
    return jdbc.queryForList(
        "SELECT article_id FROM articles WHERE " + owner.articleColumn + " = ?",
        String.class,
        ownerId);
  }

  private String articleChannel(String articleId) {
    return jdbc.queryForObject(
        "SELECT article_channel FROM articles WHERE article_id = ?", String.class, articleId);
  }

  private String articleName(String articleId) {
    return jdbc.queryForObject(
        "SELECT article_name FROM articles WHERE article_id = ?", String.class, articleId);
  }

  private List<String> articleDocuments(String articleId) {
    return jdbc.queryForList(
        "SELECT document_id FROM articles_documents WHERE article_id = ? ORDER BY document_id",
        String.class,
        articleId);
  }

  private List<String> ownerDocuments(Owner owner, String ownerId) {
    return jdbc.queryForList(
        "SELECT document_id FROM "
            + owner.documentsTable
            + " WHERE "
            + owner.idColumn
            + " = ? ORDER BY document_id",
        String.class,
        ownerId);
  }

  // -- Seeds --

  private String seedOwner(Owner owner, String tenantId) {
    String id = UUID.randomUUID().toString();
    String nameColumn = owner == Owner.SIMULATION ? "exercise_name" : "scenario_name";
    jdbc.update(
        "INSERT INTO "
            + owner.table
            + " ("
            + owner.idColumn
            + ", "
            + nameColumn
            + ", tenant_id) VALUES (?, ?, ?)",
        id,
        "channel-article-links-" + id,
        tenantId);
    trackDelete("DELETE FROM " + owner.table + " WHERE " + owner.idColumn + " = ?", id);
    seeded.add(new String[] {owner.table, owner.idColumn, id, tenantId});
    // Rows the requests under test add to the owner are removed before anything they reference.
    trackFirst("DELETE FROM " + owner.documentsTable + " WHERE " + owner.idColumn + " = ?", id);
    trackFirst(
        "DELETE FROM articles_documents WHERE article_id IN (SELECT article_id FROM articles"
            + " WHERE "
            + owner.articleColumn
            + " = ?)",
        id);
    trackFirst("DELETE FROM articles WHERE " + owner.articleColumn + " = ?", id);
    return id;
  }

  private String seedChannel(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO channels (channel_id, channel_type, channel_name, tenant_id)"
            + " VALUES (?, 'newsletter', ?, ?)",
        id,
        "channel-article-links-" + id,
        tenantId);
    trackDelete("DELETE FROM channels WHERE channel_id = ?", id);
    trackFirst(
        "UPDATE channels SET channel_logo_dark = NULL, channel_logo_light = NULL"
            + " WHERE channel_id = ?",
        id);
    seeded.add(new String[] {"channels", "channel_id", id, tenantId});
    return id;
  }

  private String seedDocument(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_type, document_target,"
            + " tenant_id) VALUES (?, ?, 'image/png', ?, ?)",
        id,
        "channel-article-links-" + id,
        id + ".png",
        tenantId);
    trackDelete("DELETE FROM documents WHERE document_id = ?", id);
    seeded.add(new String[] {"documents", "document_id", id, tenantId});
    return id;
  }

  private String seedArticle(String channelId, Owner owner, String ownerId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO articles (article_id, article_channel, "
            + owner.articleColumn
            + ", article_name) VALUES (?, ?, ?, ?)",
        id,
        channelId,
        ownerId,
        "before");
    trackDelete("DELETE FROM articles WHERE article_id = ?", id);
    return id;
  }

  private void trackDelete(String sql, Object... params) {
    deletes.add(statement(sql, params));
  }

  private void trackFirst(String sql, Object... params) {
    firstDeletes.add(statement(sql, params));
  }

  private static Object[] statement(String sql, Object[] params) {
    Object[] statement = new Object[params.length + 1];
    statement[0] = sql;
    System.arraycopy(params, 0, statement, 1, params.length);
    return statement;
  }
}
