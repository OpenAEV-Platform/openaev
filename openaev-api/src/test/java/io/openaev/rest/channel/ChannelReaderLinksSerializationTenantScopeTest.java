package io.openaev.rest.channel;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static io.openaev.rest.channel.ChannelApi.TENANT_OBSERVER_CHANNEL_URI;
import static io.openaev.rest.channel.ChannelApi.TENANT_PLAYER_CHANNEL_URI;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The observer and player channel endpoints answer with the raw simulation or scenario. Its lazy
 * links (teams, tags, pauses, articles, lessons categories, variables, linked simulations) are
 * serialized after the controller transaction has committed, where the tenant scope is gone, so
 * every one of them is read without a scope and comes out as an empty array unless the service
 * loads it inside the scoped transaction.
 *
 * <p>The class is deliberately not {@code @Transactional}: a rolled back test transaction leaves
 * the tenant scope alive through serialization and masks the failure. Rows are seeded through an
 * auto-committing {@link JdbcTemplate} and removed on teardown. The production list of isolated
 * tables is armed.
 */
@DisplayName("Channel reader endpoints serialize the links of the simulation and the scenario")
class ChannelReaderLinksSerializationTenantScopeTest extends ProductionTablesIntegrationTest {

  /** The parent the channel page is read for. */
  enum Parent {
    SIMULATION("exercise", "exercises", "channel_exercise"),
    SCENARIO("scenario", "scenarios", "channel_scenario");

    final String prefix;
    final String table;
    final String property;

    Parent(String prefix, String table, String property) {
      this.prefix = prefix;
      this.table = table;
      this.property = property;
    }

    String path(String field) {
      return "$." + property + "." + prefix + "_" + field + "[*]";
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenant;
  private boolean tenantCreatedByHelper;
  private final List<String[]> seededRows = new ArrayList<>();

  private String parentId;
  private String channelId;
  private String teamId;
  private String userId;
  private String tagId;
  private String articleId;
  private String categoryId;
  private String variableId;
  private String pauseId;
  private String linkedSimulationId;

  @AfterEach
  void cleanup() {
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    if (tenantCreatedByHelper) {
      tenantHelper.deleteCommittedTenants(tenant);
    } else {
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenant);
    }
  }

  @Nested
  @DisplayName("Observer route, as the web app calls it")
  @WithMockUser(isAdmin = true)
  class ObserverRoute {

    @BeforeEach
    void seedTenantOfTheCaller() throws Exception {
      jdbc = new JdbcTemplate(dataSource);
      tenant = tenantHelper.createTenantWithCurrentUser("channel-reader-links").getId();
      tenantCreatedByHelper = true;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Parent.class)
    @DisplayName(
        "given a parent with linked teams, tags, articles and lessons, when the observer reads the"
            + " channel, then every link is serialized")
    void given_parentWithLinks_should_serializeEveryLink(Parent parent) throws Exception {
      // Arrange
      seed(parent);

      // Act
      ResultActions result =
          mvc.perform(
              get(
                      TENANT_OBSERVER_CHANNEL_URI + "/{parentId}/{channelId}",
                      tenant,
                      parentId,
                      channelId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()));

      // Assert
      assertLinks(result, parent);
    }

    @Test
    @DisplayName(
        "given a scenario with linked teams, tags, articles and lessons, when an authenticated"
            + " player reads the channel, then every link is serialized")
    void given_scenarioWithLinks_should_serializeEveryLinkOnThePlayerRoute() throws Exception {
      // Arrange
      Parent parent = Parent.SCENARIO;
      seed(parent);

      // Act
      ResultActions result =
          mvc.perform(
              get(
                      TENANT_PLAYER_CHANNEL_URI + "/{parentId}/{channelId}",
                      tenant,
                      parentId,
                      channelId)
                  .accept(MediaType.APPLICATION_JSON));

      // Assert
      assertLinks(result, parent);
    }
  }

  @Nested
  @DisplayName("Player route with a valid token, the shape the platform emits, simulation only")
  class PlayerRoute {

    @BeforeEach
    void seedTenantOfTheSimulation() {
      jdbc = new JdbcTemplate(dataSource);
      tenant = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
              + " VALUES (?, ?, now(), now())",
          tenant,
          "channel-reader-links-" + tenant);
      tenantCreatedByHelper = false;
    }

    @Test
    @DisplayName(
        "given a simulation with linked teams, tags, articles and lessons, when the player reads"
            + " the channel with a token, then every link is serialized")
    void given_simulationWithLinks_should_serializeEveryLink() throws Exception {
      // Arrange
      Parent parent = Parent.SIMULATION;
      seed(parent);
      String rawToken = seedUrlAccessToken();
      MockHttpServletRequestBuilder request =
          get(TENANT_PLAYER_CHANNEL_URI + "/{parentId}/{channelId}", tenant, parentId, channelId)
              .cookie(new Cookie(URL_ACCESS_COOKIE_NAME, rawToken));

      // Act
      ResultActions result = mvc.perform(request);

      // Assert
      assertLinks(result, parent);
    }
  }

  private void assertLinks(ResultActions result, Parent parent) throws Exception {
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath(parent.path("teams"), contains(teamId)))
        .andExpect(jsonPath(parent.path("tags"), contains(tagId)))
        .andExpect(jsonPath(parent.path("articles"), contains(articleId)))
        .andExpect(jsonPath(parent.path("lessons_categories"), contains(categoryId)))
        .andExpect(jsonPath(parent.path("teams_users") + ".team_id", contains(teamId)));
    if (parent == Parent.SIMULATION) {
      result
          .andExpect(jsonPath(parent.path("pauses"), contains(pauseId)))
          .andExpect(jsonPath(parent.path("variables"), contains(variableId)))
          .andExpect(jsonPath(parent.path("users"), contains(userId)))
          .andExpect(jsonPath("$.channel_exercise.exercise_all_users_number").value(1));
    } else {
      result
          .andExpect(jsonPath(parent.path("exercises"), contains(linkedSimulationId)))
          .andExpect(jsonPath(parent.path("users"), contains(userId)))
          .andExpect(jsonPath("$.channel_scenario.scenario_all_users_number").value(1));
    }
  }

  // -- SEEDING (committed, cross-thread visible) --

  private void seed(Parent parent) {
    parentId = seedRow(parent.table, parent.prefix + "_id", parent.prefix + "_name");
    channelId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO channels (channel_id, channel_type, channel_name, tenant_id)"
            + " VALUES (?, 'newsletter', ?, ?)",
        channelId,
        "channel-reader-" + channelId,
        tenant);
    seededRows.add(new String[] {"channels", "channel_id", channelId});

    userId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        userId,
        "channel-reader-" + userId + "@openaev.io");
    seededRows.add(new String[] {"users", "user_id", userId});
    teamId = seedRow("teams", "team_id", "team_name");
    link(parent.table + "_teams", parent.prefix + "_id", teamId);
    jdbc.update(
        "INSERT INTO "
            + parent.table
            + "_teams_users ("
            + parent.prefix
            + "_id, team_id, user_id) VALUES (?, ?, ?)",
        parentId,
        teamId,
        userId);
    seededRows.add(new String[] {parent.table + "_teams_users", parent.prefix + "_id", parentId});
    jdbc.update("INSERT INTO users_teams (user_id, team_id) VALUES (?, ?)", userId, teamId);
    seededRows.add(new String[] {"users_teams", "user_id", userId});

    tagId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tag_color, tag_created_at, tag_updated_at, tenant_id)"
            + " VALUES (?, ?, '#000000', now(), now(), ?)",
        tagId,
        "channel-reader-" + tagId,
        tenant);
    seededRows.add(new String[] {"tags", "tag_id", tagId});
    jdbc.update(
        "INSERT INTO " + parent.table + "_tags (" + parent.prefix + "_id, tag_id) VALUES (?, ?)",
        parentId,
        tagId);
    seededRows.add(new String[] {parent.table + "_tags", parent.prefix + "_id", parentId});

    articleId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO articles (article_id, article_channel, article_"
            + parent.prefix
            + ", article_name) VALUES (?, ?, ?, ?)",
        articleId,
        channelId,
        parentId,
        "channel-reader-" + articleId);
    seededRows.add(new String[] {"articles", "article_id", articleId});

    categoryId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO lessons_categories (lessons_category_id, lessons_category_name,"
            + " lessons_category_order, lessons_category_"
            + parent.prefix
            + ") VALUES (?, ?, 0, ?)",
        categoryId,
        "channel-reader-" + categoryId,
        parentId);
    seededRows.add(new String[] {"lessons_categories", "lessons_category_id", categoryId});

    if (parent == Parent.SIMULATION) {
      pauseId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO pauses (pause_id, pause_exercise, pause_date, pause_duration)"
              + " VALUES (?, ?, now(), 0)",
          pauseId,
          parentId);
      seededRows.add(new String[] {"pauses", "pause_id", pauseId});
      variableId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO variables (variable_id, variable_key, variable_value, variable_type,"
              + " variable_exercise, variable_created_at, variable_updated_at)"
              + " VALUES (?, 'k', 'v', 'String', ?, now(), now())",
          variableId,
          parentId);
      seededRows.add(new String[] {"variables", "variable_id", variableId});
    } else {
      linkedSimulationId = seedRow("exercises", "exercise_id", "exercise_name");
      jdbc.update(
          "INSERT INTO scenarios_exercises (scenario_id, exercise_id) VALUES (?, ?)",
          parentId,
          linkedSimulationId);
      seededRows.add(new String[] {"scenarios_exercises", "scenario_id", parentId});
    }
  }

  private void link(String table, String parentColumn, String childId) {
    jdbc.update(
        "INSERT INTO " + table + " (" + parentColumn + ", team_id) VALUES (?, ?)",
        parentId,
        childId);
    seededRows.add(new String[] {table, parentColumn, parentId});
  }

  private String seedRow(String table, String idColumn, String nameColumn) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO "
            + table
            + " ("
            + idColumn
            + ", "
            + nameColumn
            + ", tenant_id) VALUES (?, ?, ?)",
        id,
        "channel-reader-" + id,
        tenant);
    seededRows.add(new String[] {table, idColumn, id});
    return id;
  }

  private String seedUrlAccessToken() {
    String raw = "channel-reader-token-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO url_access_token (id, token_hash, url, user_id, exercise_id, expires_at)"
            + " VALUES (?, ?, ?, ?, ?, now() + interval '30 days')",
        UUID.randomUUID().toString(),
        sha256Hex(raw),
        "/" + tenant + "/channels/" + parentId,
        userId,
        parentId);
    seededRows.add(new String[] {"url_access_token", "user_id", userId});
    return raw;
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
