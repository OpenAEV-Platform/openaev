package io.openaev.rest.stream;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.config.DefaultOpenAEVPrincipal;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.service.PermissionService;
import io.openaev.service.StreamService;
import io.openaev.service.UserService;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.Disposable;

/**
 * A user is platform-level, so its events reach the stream consumers of every tenant. Each consumer
 * is sent the user as it holds in the tenant of that consumer: the links of the user to the
 * entities of the tenant, and the ids the consumer's tenant knows.
 *
 * <p>The rule, per field of the user, as a consumer of tenant T receives it:
 *
 * <ul>
 *   <li>{@code user_teams}, {@code user_tags}: the ids of tenant T, and the field is left out when
 *       the write was made in another tenant, so the consumer keeps what it holds;
 *   <li>{@code user_organization}: left out when the write was made in another tenant;
 *   <li>{@code user_communications}, {@code team_exercises_users}: never sent, their ids carry no
 *       tenant and no screen reads them from the stream;
 *   <li>{@code user_grants}, {@code user_home_dashboard}: sent to the user they belong to only;
 *   <li>{@code user_groups}, {@code user_capabilities}, {@code user_is_*}: derived from the groups
 *       of tenant T and the platform groups.
 * </ul>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the write commits and its session closes
 * before the events are delivered, as in production, and the stream is fed through {@code
 * makeDataFlux} the way a client subscribes to it. Every committed row is removed in
 * {@code @AfterEach}.
 */
@RecordApplicationEvents
@WithMockUser(isAdmin = true)
@DisplayName("A user event carries the links of the consumer's tenant")
public class UserStreamEventConsumerTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String PLATFORM_USER_URI = "/api/platform-users";
  private static final String STREAM_LOGGER = "io.openaev.service.StreamService";
  private static final String CAPABILITY_OF_A = "ACCESS_ASSESSMENT";
  private static final String CAPABILITY_OF_B = "ACCESS_PAYLOADS";
  private static final String CAPABILITY_OF_PLATFORM = "ACCESS_TEAMS_AND_PLAYERS";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;
  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private AttackPathAccessControl attackPathAccessControl;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;

  private User player;
  private User userOfA;
  private User userOfB;
  private User userOfDefault;

  private String tagA;
  private String tagB;
  private String teamA;
  private String teamB;
  private String simulationA;
  private String simulationB;
  private String communicationA;
  private String communicationB;
  private String dashboardA;
  private String groupA;
  private String groupB;
  private String groupPlatform;
  private final List<String> roles = new ArrayList<>();
  private final List<String> grants = new ArrayList<>();
  private final List<String> createdUserIds = new ArrayList<>();

  private StreamService stream;
  private final List<Consumer> consumers = new ArrayList<>();
  private ListAppender<ILoggingEvent> warnings;

  // -- FIXTURE --

  @BeforeEach
  void aPlayerHoldingLinksInTwoTenants() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantIsolationHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantA = tenantIsolationHelper.createTenant("stream-a").getId();
    tenantB = tenantIsolationHelper.createTenant("stream-b").getId();
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);

    player = persistedUser(Tenant.DEFAULT_TENANT_UUID, tenantA, tenantB);
    userOfA = persistedUser(tenantA);
    userOfB = persistedUser(tenantB);
    userOfDefault = persistedUser(Tenant.DEFAULT_TENANT_UUID);

    tagA = insertTag(tenantA);
    tagB = insertTag(tenantB);
    teamA = insertTeam(tenantA);
    teamB = insertTeam(tenantB);
    simulationA = insertSimulation(tenantA);
    simulationB = insertSimulation(tenantB);
    communicationA = insertCommunication();
    communicationB = insertCommunication();
    dashboardA = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO custom_dashboards (custom_dashboard_id, custom_dashboard_name, tenant_id)"
            + " VALUES (?, ?, ?)",
        dashboardA,
        "stream-dashboard",
        tenantA);
    jdbc.update(
        "UPDATE users SET user_home_dashboard = ? WHERE user_id = ?", dashboardA, player.getId());

    for (String[] link :
        new String[][] {
          {"users_tags", tagA},
          {"users_tags", tagB},
        }) {
      jdbc.update(
          "INSERT INTO " + link[0] + " (user_id, tag_id) VALUES (?, ?)", player.getId(), link[1]);
    }
    for (String team : List.of(teamA, teamB)) {
      jdbc.update("INSERT INTO users_teams (user_id, team_id) VALUES (?, ?)", player.getId(), team);
    }
    jdbc.update(
        "INSERT INTO exercises_teams_users (exercise_id, team_id, user_id) VALUES (?, ?, ?)",
        simulationA,
        teamA,
        player.getId());
    jdbc.update(
        "INSERT INTO exercises_teams_users (exercise_id, team_id, user_id) VALUES (?, ?, ?)",
        simulationB,
        teamB,
        player.getId());
    for (String communication : List.of(communicationA, communicationB)) {
      jdbc.update(
          "INSERT INTO communications_users (communication_id, user_id) VALUES (?, ?)",
          communication,
          player.getId());
    }

    groupA = insertGroup(tenantA, CAPABILITY_OF_A);
    groupB = insertGroup(tenantB, CAPABILITY_OF_B);
    groupPlatform = insertGroup(null, CAPABILITY_OF_PLATFORM);
    for (String group : List.of(groupA, groupB, groupPlatform)) {
      jdbc.update(
          "INSERT INTO users_groups (group_id, user_id) VALUES (?, ?)", group, player.getId());
    }
    insertGrant(groupA, simulationA, "PLANNER");
    insertGrant(groupB, simulationB, "OBSERVER");
    insertGrant(groupPlatform, simulationB, "OBSERVER");

    TenantContext.clearCurrentTenant();
    stream =
        new StreamService(
            permissionsGrantingEverything(), userService, attackPathAccessControl, mapper);
    warnings = new ListAppender<>();
    warnings.start();
    Logger streamLogger = (Logger) LoggerFactory.getLogger(STREAM_LOGGER);
    streamLogger.setLevel(Level.WARN);
    streamLogger.addAppender(warnings);
  }

  @AfterEach
  void removeEveryCommittedRowThenReportTheChecks() {
    try {
      removeEveryCommittedRow();
    } finally {
      assertTrue(failures.isEmpty(), "Failed checks:\n" + String.join("\n", failures));
    }
  }

  private void removeEveryCommittedRow() {
    consumers.forEach(consumer -> consumer.subscription().dispose());
    ((Logger) LoggerFactory.getLogger(STREAM_LOGGER)).detachAppender(warnings);
    TenantContext.clearCurrentTenant();
    List<String> users = new ArrayList<>(createdUserIds);
    users.add(player.getId());
    users.add(userOfA.getId());
    users.add(userOfB.getId());
    users.add(userOfDefault.getId());
    for (String user : users) {
      for (String table :
          List.of(
              "exercises_teams_users",
              "communications_users",
              "users_teams",
              "users_tags",
              "users_groups",
              "users_organizations",
              "users_tenants")) {
        jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", user);
      }
      jdbc.update("DELETE FROM tokens WHERE token_user = ?", user);
      jdbc.update("DELETE FROM users WHERE user_id = ?", user);
    }
    jdbc.update(
        "DELETE FROM grants WHERE grant_id = ANY (?)", (Object) grants.toArray(String[]::new));
    for (String communication : List.of(communicationA, communicationB)) {
      jdbc.update("DELETE FROM communications WHERE communication_id = ?", communication);
    }
    for (String simulation : List.of(simulationA, simulationB)) {
      jdbc.update("DELETE FROM exercises WHERE exercise_id = ?", simulation);
    }
    for (String team : List.of(teamA, teamB)) {
      jdbc.update("DELETE FROM teams WHERE team_id = ?", team);
    }
    for (String tag : List.of(tagA, tagB)) {
      jdbc.update("DELETE FROM tags WHERE tag_id = ?", tag);
    }
    jdbc.update("DELETE FROM custom_dashboards WHERE custom_dashboard_id = ?", dashboardA);
    for (String group : List.of(groupA, groupB, groupPlatform)) {
      jdbc.update("DELETE FROM groups_roles WHERE group_id = ?", group);
      jdbc.update("DELETE FROM groups WHERE group_id = ?", group);
    }
    for (String role : roles) {
      jdbc.update("DELETE FROM roles_capabilities WHERE role_id = ?", role);
      jdbc.update("DELETE FROM roles WHERE role_id = ?", role);
    }
    tenantIsolationHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  private User persistedUser(String... tenantIds) {
    User user = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    for (String tenantId : tenantIds) {
      tenantIsolationHelper.attachUserToTenant(user.getId(), tenantId);
    }
    return user;
  }

  private String insertTag(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "stream-tag-" + id,
        tenantId);
    return id;
  }

  private String insertTeam(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "stream-team-" + id,
        tenantId);
    return id;
  }

  private String insertSimulation(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, tenant_id) VALUES (?, ?, ?)",
        id,
        "stream-simulation-" + id,
        tenantId);
    return id;
  }

  private String insertCommunication() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO communications (communication_id, communication_received_at,"
            + " communication_sent_at, communication_message_id, communication_from,"
            + " communication_to) VALUES (?, now(), now(), ?, 'from@filigran.io', 'to@filigran.io')",
        id,
        "message-" + id);
    return id;
  }

  /** A group with one role holding one capability; a null tenant makes it a platform group. */
  private String insertGroup(String tenantId, String capability) {
    String groupId = UUID.randomUUID().toString();
    String roleId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO roles (role_id, role_name, role_created_at, role_updated_at, tenant_id)"
            + " VALUES (?, ?, now(), now(), ?)",
        roleId,
        "stream-role-" + roleId,
        tenantId);
    jdbc.update(
        "INSERT INTO roles_capabilities (role_id, capability) VALUES (?, ?)", roleId, capability);
    roles.add(roleId);
    jdbc.update(
        "INSERT INTO groups (group_id, group_name, tenant_id) VALUES (?, ?, ?)",
        groupId,
        "stream-group-" + groupId,
        tenantId);
    jdbc.update("INSERT INTO groups_roles (group_id, role_id) VALUES (?, ?)", groupId, roleId);
    return groupId;
  }

  private void insertGrant(String groupId, String simulationId, String grantType) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO grants (grant_id, grant_name, grant_group, grant_resource,"
            + " grant_resource_type) VALUES (?, ?, ?, ?, 'SIMULATION')",
        id,
        grantType,
        groupId,
        simulationId);
    grants.add(id);
  }

  private static PermissionService permissionsGrantingEverything() {
    PermissionService permissions = mock(PermissionService.class);
    when(permissions.hasPermissionInTenant(any(), any(), any(), any(), any())).thenReturn(true);
    return permissions;
  }

  // -- CONSUMERS --

  /** A subscription to the stream, and the events it was sent as they travel on the wire. */
  private record Consumer(
      String name,
      String tenantId,
      String userId,
      List<JsonNode> received,
      List<String> eventTypes,
      Disposable subscription) {}

  /**
   * Subscribes the way a client does: the tenant and the principal are the ones of the subscribing
   * thread. The default tenant is the fallback of a thread with no tenant, as for {@code
   * /api/stream}.
   */
  private Consumer subscribe(String name, String tenantId, User user) {
    List<JsonNode> received = new CopyOnWriteArrayList<>();
    List<String> eventTypes = new CopyOnWriteArrayList<>();
    Authentication previous = SecurityContextHolder.getContext().getAuthentication();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new DefaultOpenAEVPrincipal(user.getId(), List.of(), false, "en"),
                null,
                List.of()));
    TenantContext.clearCurrentTenant();
    if (!Tenant.DEFAULT_TENANT_UUID.equals(tenantId)) {
      TenantContext.setCurrentTenant(tenantId);
    }
    try {
      Disposable subscription =
          stream
              .makeDataFlux(name + "-" + UUID.randomUUID())
              .subscribe(
                  sent -> {
                    @SuppressWarnings("unchecked")
                    BaseEvent event = ((ServerSentEvent<BaseEvent>) sent).data();
                    // Serialized as sent: the payload of one delivery is measured on its own.
                    JsonNode wire = mapper.valueToTree(event);
                    received.add(wire.path("instance"));
                    eventTypes.add(wire.path("event_type").asText());
                  });
      Consumer consumer =
          new Consumer(name, tenantId, user.getId(), received, eventTypes, subscription);
      consumers.add(consumer);
      return consumer;
    } finally {
      SecurityContextHolder.getContext().setAuthentication(previous);
      TenantContext.clearCurrentTenant();
    }
  }

  private Consumer consumerOfA() {
    return subscribe("a", tenantA, userOfA);
  }

  private Consumer playerInA() {
    return subscribe("player-in-a", tenantA, player);
  }

  private Consumer consumerOfB() {
    return subscribe("b", tenantB, userOfB);
  }

  private Consumer playerInB() {
    return subscribe("player-in-b", tenantB, player);
  }

  private Consumer consumerOfDefault() {
    return subscribe("default", Tenant.DEFAULT_TENANT_UUID, userOfDefault);
  }

  // -- WRITES --

  /** Runs the write, then streams the events it published about the user to every consumer. */
  private List<BaseEvent> writeAndStream(String userId, Write write) throws Exception {
    TenantContext.clearCurrentTenant();
    assertFalse(
        TenantContext.hasCurrentTenant(),
        "The write starts with no ambient tenant, as a request does in production");
    long publishedBefore = applicationEvents.stream(BaseEvent.class).count();
    write.run();
    List<BaseEvent> events =
        applicationEvents.stream(BaseEvent.class)
            .skip(publishedBefore)
            .filter(
                event -> event.getInstance() instanceof User user && userId.equals(user.getId()))
            .toList();
    assertFalse(events.isEmpty(), "The write must have published an event for the user");
    List<JsonNode> payloadsBefore = events.stream().map(BaseEvent::getInstanceData).toList();
    events.forEach(
        event -> {
          TenantContext.clearCurrentTenant();
          stream.listenDatabaseUpdate(event);
        });
    soft(
        () -> {
          for (int i = 0; i < events.size(); i++) {
            assertSame(
                payloadsBefore.get(i),
                events.get(i).getInstanceData(),
                "A delivery must leave the published event and its payload as they were");
          }
        });
    return events;
  }

  private interface Write {
    void run() throws Exception;
  }

  private PlayerInput playerInput(String email, String firstname, List<String> tagIds) {
    PlayerInput input = PlayerFixture.createPlayerInput();
    input.setEmail(email);
    input.setFirstname(firstname);
    input.setTagIds(tagIds);
    input.setTeamIds(List.of());
    return input;
  }

  private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
    return request
        .content(asJsonString(body))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .with(csrf());
  }

  private List<BaseEvent> updatePlayerIn(String tenantId, String firstname, List<String> tagIds)
      throws Exception {
    return updateUserIn(player, tenantId, firstname, tagIds);
  }

  private List<BaseEvent> updateUserIn(
      User target, String tenantId, String firstname, List<String> tagIds) throws Exception {
    return writeAndStream(
        target.getId(),
        () ->
            mvc.perform(
                    json(
                        put(PLAYER_URI + "/" + target.getId()).header(TENANT_HEADER, tenantId),
                        playerInput(target.getEmail(), firstname, tagIds)))
                .andExpect(status().is2xxSuccessful()));
  }

  // -- WHAT A CONSUMER RECEIVED --

  /** Every field of a user the stream may carry: a new one must be ruled on before it is sent. */
  private static final Set<String> STREAMED_USER_FIELDS =
      Set.of(
          "user_id",
          "user_firstname",
          "user_lastname",
          "user_lang",
          "user_theme",
          "user_home_dashboard",
          "user_email",
          "user_phone",
          "user_phone2",
          "user_pgp_key",
          "user_status",
          "user_created_at",
          "user_updated_at",
          "user_organization",
          "user_admin",
          "user_country",
          "user_city",
          "user_groups",
          "user_teams",
          "user_tags",
          "user_gravatar",
          "user_is_planner",
          "user_is_observer",
          "user_is_manager",
          "user_is_player",
          "user_is_external",
          "user_is_only_player",
          "user_is_admin_or_bypass",
          "user_capabilities",
          "user_grants",
          "listened");

  private List<String> idsOf(JsonNode array) {
    return mapper.convertValue(
        array, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
  }

  private static List<String> sorted(String... ids) {
    return new TreeSet<>(List.of(ids)).stream().toList();
  }

  private static List<String> sorted(Collection<String> ids) {
    return new TreeSet<>(ids).stream().toList();
  }

  // The checks of one test are all run and reported together, once the committed rows are gone:
  // a field that fails does not hide the ones after it.
  private final List<String> failures = new ArrayList<>();

  private void soft(Runnable assertion) {
    try {
      assertion.run();
    } catch (AssertionError e) {
      failures.add(e.getMessage());
    }
  }

  private void assertDelivered(
      Consumer consumer, List<BaseEvent> published, String field, String expectedValue) {
    assertDelivered(consumer, player, published, field, expectedValue);
  }

  private void assertDelivered(
      Consumer consumer,
      User target,
      List<BaseEvent> published,
      String field,
      String expectedValue) {
    soft(() -> hardAssertDelivered(consumer, target, published, field, expectedValue));
    assertKnownFields(consumer);
  }

  private void assertKnownFields(Consumer consumer) {
    soft(
        () ->
            consumer
                .received()
                .forEach(
                    instance -> {
                      Set<String> unknown = new TreeSet<>();
                      instance.fieldNames().forEachRemaining(unknown::add);
                      unknown.removeAll(STREAMED_USER_FIELDS);
                      assertTrue(
                          unknown.isEmpty(),
                          consumer.name() + " received fields no rule covers: " + unknown);
                    }));
  }

  private void assertIds(Consumer consumer, String field, List<String> expected) {
    soft(() -> hardAssertIds(consumer, field, expected));
  }

  private void assertLeftOut(Consumer consumer, String field) {
    soft(() -> hardAssertLeftOut(consumer, field));
  }

  private void assertCapabilities(Consumer consumer, String... expected) {
    soft(() -> hardAssertCapabilities(consumer, expected));
  }

  private void assertGrants(Consumer consumer, Map<String, String> expected) {
    soft(() -> hardAssertGrants(consumer, expected));
  }

  private void assertFlags(Consumer consumer, boolean planner, boolean observer) {
    soft(() -> hardAssertFlags(consumer, planner, observer));
  }

  /** {@code user_is_player} as the consumer received it: absent when {@code expected} is null. */
  private void assertIsPlayer(Consumer consumer, Boolean expected) {
    soft(() -> hardAssertIsPlayer(consumer, expected));
  }

  private void assertNoIdOutsideTheScope(Consumer consumer, String... allowedIds) {
    soft(() -> hardAssertNoIdOutsideTheScope(consumer, allowedIds));
  }

  /** The field is a single id, the same in every event of the consumer. */
  private void assertValue(Consumer consumer, String field, String expected) {
    soft(
        () ->
            consumer
                .received()
                .forEach(
                    instance ->
                        assertEquals(
                            expected,
                            instance.path(field).asText(),
                            consumer.name() + " " + field)));
  }

  /** No warning of the stream was logged: a delivery that failed must not read as a trim. */
  private void assertNothingFailedToStream() {
    List<String> failures =
        warnings.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .filter(
                message ->
                    message.startsWith("Failed to stream") || message.contains("can't be streamed"))
            .toList();
    assertTrue(failures.isEmpty(), "No delivery may fail, got " + failures);
  }

  /** Every published event reached the consumer, as the target user, carrying the written value. */
  private void hardAssertDelivered(
      Consumer consumer,
      User target,
      List<BaseEvent> published,
      String field,
      String expectedValue) {
    assertNothingFailedToStream();
    assertEquals(
        published.size(),
        consumer.received().size(),
        consumer.name() + " must receive one event per published event");
    consumer
        .received()
        .forEach(
            instance -> {
              assertEquals(
                  target.getId(), instance.path("user_id").asText(), consumer.name() + " user_id");
              assertEquals(
                  expectedValue,
                  instance.path(field).asText(),
                  consumer.name() + " must receive the written " + field);
            });
  }

  /** The field holds exactly these ids in every event of the consumer. */
  private void hardAssertIds(Consumer consumer, String field, List<String> expected) {
    consumer
        .received()
        .forEach(
            instance -> {
              assertTrue(
                  instance.has(field),
                  consumer.name() + " must receive " + field + " but got " + instance);
              assertEquals(
                  sorted(expected),
                  sorted(idsOf(instance.path(field))),
                  consumer.name() + " " + field);
            });
  }

  /** The field is left out of every event of the consumer, which keeps the value it holds. */
  private void hardAssertLeftOut(Consumer consumer, String field) {
    consumer
        .received()
        .forEach(
            instance ->
                assertFalse(
                    instance.has(field),
                    consumer.name()
                        + " must not receive "
                        + field
                        + " but got "
                        + instance.path(field)));
  }

  private void hardAssertCapabilities(Consumer consumer, String... expected) {
    consumer
        .received()
        .forEach(
            instance ->
                assertEquals(
                    sorted(expected),
                    sorted(idsOf(instance.path("user_capabilities"))),
                    consumer.name() + " user_capabilities"));
  }

  /** The grants of the player, as a map from the resource to the grant. */
  private void hardAssertGrants(Consumer consumer, Map<String, String> expected) {
    consumer
        .received()
        .forEach(
            instance -> {
              assertTrue(
                  instance.has("user_grants"), consumer.name() + " must receive user_grants");
              Map<String, String> actual =
                  mapper.convertValue(
                      instance.path("user_grants"),
                      mapper
                          .getTypeFactory()
                          .constructMapType(LinkedHashMap.class, String.class, String.class));
              assertEquals(expected, actual, consumer.name() + " user_grants");
            });
  }

  private void hardAssertFlags(Consumer consumer, boolean planner, boolean observer) {
    consumer
        .received()
        .forEach(
            instance -> {
              assertEquals(
                  planner,
                  instance.path("user_is_planner").asBoolean(),
                  consumer.name() + " user_is_planner");
              assertEquals(
                  observer,
                  instance.path("user_is_observer").asBoolean(),
                  consumer.name() + " user_is_observer");
              assertTrue(instance.has("user_is_manager"), consumer.name() + " user_is_manager");
              if (planner || observer) {
                assertTrue(
                    instance.path("user_is_player").asBoolean(),
                    consumer.name() + " user_is_player, as a planner or an observer");
              }
              assertTrue(
                  instance.has("user_is_only_player"), consumer.name() + " user_is_only_player");
            });
  }

  private void hardAssertIsPlayer(Consumer consumer, Boolean expected) {
    consumer
        .received()
        .forEach(
            instance -> {
              if (expected == null) {
                assertFalse(
                    instance.has("user_is_player"),
                    consumer.name()
                        + " must not receive user_is_player but got "
                        + instance.path("user_is_player"));
              } else {
                assertTrue(
                    instance.has("user_is_player"),
                    consumer.name() + " must receive user_is_player");
                assertEquals(
                    expected,
                    instance.path("user_is_player").asBoolean(),
                    consumer.name() + " user_is_player");
              }
            });
  }

  /** No id of the fixture that belongs to another tenant than the allowed ones is in the event. */
  private void hardAssertNoIdOutsideTheScope(Consumer consumer, String... allowedIds) {
    Set<String> fixtureIds =
        new HashSet<>(
            List.of(
                tagA,
                tagB,
                teamA,
                teamB,
                simulationA,
                simulationB,
                communicationA,
                communicationB,
                dashboardA,
                groupA,
                groupB,
                groupPlatform));
    fixtureIds.removeAll(List.of(allowedIds));
    consumer
        .received()
        .forEach(
            instance -> {
              Map<String, String> found = new LinkedHashMap<>();
              collectStrings("", instance, found);
              found.values().retainAll(fixtureIds);
              assertTrue(
                  found.isEmpty(), consumer.name() + " received ids of other tenants: " + found);
            });
  }

  /** The strings of the tree, and the keys of its objects, by the path they were found at. */
  private static void collectStrings(String path, JsonNode node, Map<String, String> into) {
    if (node.isTextual()) {
      into.put(path, node.asText());
    }
    if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        collectStrings(path + "[" + i + "]", node.get(i), into);
      }
    }
    node.fields()
        .forEachRemaining(
            field -> {
              into.put(path + "/key:" + field.getKey(), field.getKey());
              collectStrings(path + "/" + field.getKey(), field.getValue(), into);
            });
  }

  // -- THE CASES --

  @Test
  @DisplayName("The check of the stream catches a warning")
  void given_aWarningOfTheStream_should_beCaptured() {
    // -- EXECUTE --
    LoggerFactory.getLogger(STREAM_LOGGER).warn("Failed to stream control");

    // -- ASSERT --
    assertEquals(1, warnings.list.size(), "The appender must capture the warnings of the stream");
    assertFalse(warnings.list.isEmpty());
    org.junit.jupiter.api.Assertions.assertThrows(
        AssertionError.class,
        this::assertNothingFailedToStream,
        "A failed delivery must turn the check red");
  }

  @Nested
  @DisplayName("Player updated in tenant A")
  class UpdatedInA {

    private static final String NEW_NAME = "Written-in-a";

    @Test
    @DisplayName("The consumers of A receive the links of A and the grants of the player")
    void given_updateInA_should_sendTheConsumersOfATheLinksOfA() throws Exception {
      // -- PREPARE --
      Consumer other = consumerOfA();
      Consumer self = playerInA();
      Consumer ofB = consumerOfB();

      // -- EXECUTE --
      List<BaseEvent> events = updatePlayerIn(tenantA, NEW_NAME, List.of(tagA));

      // -- ASSERT --
      assertEquals(tenantA, events.getFirst().getTenantId());
      for (Consumer consumer : List.of(other, self)) {
        assertDelivered(consumer, events, "user_firstname", NEW_NAME);
        assertIds(consumer, "user_teams", List.of(teamA));
        assertIds(consumer, "user_tags", List.of(tagA));
        assertIds(consumer, "user_groups", List.of(groupA, groupPlatform));
        assertCapabilities(consumer, CAPABILITY_OF_A, CAPABILITY_OF_PLATFORM);
        assertFlags(consumer, true, true);
        assertTrue(consumer.received().getFirst().has("user_organization"));
      }
      assertDelivered(ofB, events, "user_firstname", NEW_NAME);
    }

    @Test
    @DisplayName("No consumer receives the communications or the simulation links of the player")
    void given_updateInA_should_sendNoCommunicationNorSimulationLink() throws Exception {
      // -- PREPARE --
      List<Consumer> all =
          List.of(consumerOfA(), playerInA(), consumerOfB(), playerInB(), consumerOfDefault());

      // -- EXECUTE --
      List<BaseEvent> events = updatePlayerIn(tenantA, NEW_NAME, List.of(tagA));

      // -- ASSERT --
      for (Consumer consumer : all) {
        assertDelivered(consumer, events, "user_firstname", NEW_NAME);
        assertLeftOut(consumer, "user_communications");
        assertLeftOut(consumer, "team_exercises_users");
      }
    }

    @Test
    @DisplayName("The grants and the home dashboard reach the player only, as of the consumer")
    void given_updateInA_should_sendGrantsAndDashboardToThePlayerOnly() throws Exception {
      // -- PREPARE --
      Consumer other = consumerOfA();
      Consumer self = playerInA();
      Consumer selfInB = playerInB();
      Consumer ofB = consumerOfB();
      Consumer ofDefault = consumerOfDefault();

      // -- EXECUTE --
      List<BaseEvent> events = updatePlayerIn(tenantA, NEW_NAME, List.of(tagA));

      // -- ASSERT --
      for (Consumer consumer : List.of(other, self, selfInB, ofB, ofDefault)) {
        assertDelivered(consumer, events, "user_firstname", NEW_NAME);
      }
      assertGrants(self, Map.of(simulationA, "PLANNER", simulationB, "OBSERVER"));
      assertGrants(selfInB, Map.of(simulationB, "OBSERVER"));
      assertFlags(self, true, true);
      assertFlags(selfInB, false, true);
      assertValue(self, "user_home_dashboard", dashboardA);
      assertValue(selfInB, "user_home_dashboard", dashboardA);
      for (Consumer consumer : List.of(other, ofB, ofDefault)) {
        assertLeftOut(consumer, "user_grants");
        assertLeftOut(consumer, "user_home_dashboard");
        assertFlags(consumer, consumer == other, true);
      }
    }

    @Test
    @DisplayName("The consumers of B keep the links they hold: the field is left out")
    void given_updateInA_should_leaveTheLinksOutOfTheConsumersOfB() throws Exception {
      // -- PREPARE --
      Consumer other = consumerOfB();
      Consumer self = playerInB();

      // -- EXECUTE --
      List<BaseEvent> events = updatePlayerIn(tenantA, NEW_NAME, List.of(tagA));

      // -- ASSERT --
      for (Consumer consumer : List.of(other, self)) {
        assertDelivered(consumer, events, "user_firstname", NEW_NAME);
        assertLeftOut(consumer, "user_teams");
        assertLeftOut(consumer, "user_tags");
        assertLeftOut(consumer, "user_organization");
        assertIds(consumer, "user_groups", List.of(groupB, groupPlatform));
        assertCapabilities(consumer, CAPABILITY_OF_B, CAPABILITY_OF_PLATFORM);
      }
      assertNoIdOutsideTheScope(other, groupB, groupPlatform);
      assertNoIdOutsideTheScope(self, groupB, groupPlatform, simulationB, dashboardA);
    }

    @Test
    @DisplayName(
        "The consumer of the default tenant keeps the links it holds: the field is left out")
    void given_updateInA_should_leaveTheLinksOutOfTheConsumerOfTheDefaultTenant() throws Exception {
      // -- PREPARE --
      Consumer ofDefault = consumerOfDefault();

      // -- EXECUTE --
      List<BaseEvent> events = updatePlayerIn(tenantA, NEW_NAME, List.of(tagA));

      // -- ASSERT --
      assertDelivered(ofDefault, events, "user_firstname", NEW_NAME);
      assertLeftOut(ofDefault, "user_teams");
      assertLeftOut(ofDefault, "user_tags");
      assertLeftOut(ofDefault, "user_organization");
      assertIds(ofDefault, "user_groups", List.of(groupPlatform));
      assertCapabilities(ofDefault, CAPABILITY_OF_PLATFORM);
      assertNoIdOutsideTheScope(ofDefault, groupPlatform);
    }

    @Test
    @DisplayName(
        "A member of a team of A only is a player for the consumers of A, and unknown to the others")
    void given_updateInA_should_sendIsPlayerFromTheTeamsOfTheConsumerTenant() throws Exception {
      // -- PREPARE --
      User teamMember = persistedUser(tenantA);
      createdUserIds.add(teamMember.getId());
      jdbc.update(
          "INSERT INTO users_teams (user_id, team_id) VALUES (?, ?)", teamMember.getId(), teamA);
      Consumer ofA = consumerOfA();
      Consumer ofB = consumerOfB();
      Consumer ofDefault = consumerOfDefault();

      // -- EXECUTE --
      List<BaseEvent> events = updateUserIn(teamMember, tenantA, NEW_NAME, List.of(tagA));

      // -- ASSERT --
      for (Consumer consumer : List.of(ofA, ofB, ofDefault)) {
        assertDelivered(consumer, teamMember, events, "user_firstname", NEW_NAME);
        assertFlags(consumer, false, false);
      }
      assertIds(ofA, "user_teams", List.of(teamA));
      assertIsPlayer(ofA, true);
      assertLeftOut(ofB, "user_teams");
      assertIsPlayer(ofB, null);
      assertLeftOut(ofDefault, "user_teams");
      assertIsPlayer(ofDefault, null);
    }

    @Test
    @DisplayName("The consumers of A receive no id of B")
    void given_updateInA_should_sendTheConsumersOfANoIdOfB() throws Exception {
      // -- PREPARE --
      Consumer other = consumerOfA();
      Consumer self = playerInA();

      // -- EXECUTE --
      List<BaseEvent> events = updatePlayerIn(tenantA, NEW_NAME, List.of(tagA));

      // -- ASSERT --
      assertDelivered(other, events, "user_firstname", NEW_NAME);
      assertDelivered(self, events, "user_firstname", NEW_NAME);
      assertNoIdOutsideTheScope(other, tagA, teamA, simulationA, groupA, groupPlatform);
      assertNoIdOutsideTheScope(
          self, tagA, teamA, simulationA, groupA, groupPlatform, simulationB, dashboardA);
    }
  }

  @Nested
  @DisplayName("Player updated in tenant B")
  class UpdatedInB {

    private static final String NEW_NAME = "Written-in-b";

    @Test
    @DisplayName("The consumers of B receive the links of B, the consumers of A none")
    void given_updateInB_should_sendTheLinksOfBToTheConsumersOfBOnly() throws Exception {
      // -- PREPARE --
      Consumer other = consumerOfB();
      Consumer self = playerInB();
      Consumer ofA = consumerOfA();
      Consumer selfInA = playerInA();
      Consumer ofDefault = consumerOfDefault();

      // -- EXECUTE --
      List<BaseEvent> events = updatePlayerIn(tenantB, NEW_NAME, List.of(tagB));

      // -- ASSERT --
      assertEquals(tenantB, events.getFirst().getTenantId());
      for (Consumer consumer : List.of(other, self)) {
        assertDelivered(consumer, events, "user_firstname", NEW_NAME);
        assertIds(consumer, "user_teams", List.of(teamB));
        assertIds(consumer, "user_tags", List.of(tagB));
        assertIds(consumer, "user_groups", List.of(groupB, groupPlatform));
        assertCapabilities(consumer, CAPABILITY_OF_B, CAPABILITY_OF_PLATFORM);
        assertLeftOut(consumer, "user_communications");
        assertLeftOut(consumer, "team_exercises_users");
      }
      assertGrants(self, Map.of(simulationB, "OBSERVER"));
      assertLeftOut(other, "user_grants");
      assertNoIdOutsideTheScope(other, tagB, teamB, simulationB, groupB, groupPlatform);
      for (Consumer consumer : List.of(ofA, selfInA, ofDefault)) {
        assertDelivered(consumer, events, "user_firstname", NEW_NAME);
        assertLeftOut(consumer, "user_teams");
        assertLeftOut(consumer, "user_tags");
        assertLeftOut(consumer, "user_communications");
        assertLeftOut(consumer, "team_exercises_users");
      }
      assertNoIdOutsideTheScope(ofA, groupA, groupPlatform);
      assertNoIdOutsideTheScope(ofDefault, groupPlatform);
    }
  }

  @Nested
  @DisplayName("User whose links were not loaded when the event is delivered")
  class NotLoaded {

    /**
     * The user as a stream finds it after the write's session is gone: loaded outside any
     * transaction, its organizations initialized, and the {@code lazyField} collection left lazy.
     * The event is built with a mapper that serializes nothing, so the instance keeps the
     * collection as loaded: the constructor of the event would otherwise initialize it in the
     * write's session.
     */
    private BaseEvent eventOfAUserLeavingLazy(String lazyField) {
      User created = persistedUser(tenantA);
      createdUserIds.add(created.getId());
      jdbc.update(
          "INSERT INTO users_teams (user_id, team_id) VALUES (?, ?)", created.getId(), teamA);
      jdbc.update("INSERT INTO users_tags (user_id, tag_id) VALUES (?, ?)", created.getId(), tagA);
      User detached =
          new TransactionTemplate(transactionManager)
              .execute(
                  status -> {
                    User loaded = userRepository.findById(created.getId()).orElseThrow();
                    loaded.loadOrganizations();
                    if (!"teams".equals(lazyField)) {
                      Hibernate.initialize(loaded.getTeams());
                    }
                    if (!"tags".equals(lazyField)) {
                      Hibernate.initialize(loaded.getTags());
                    }
                    return loaded;
                  });
      assertFalse(
          Hibernate.isInitialized(
              "teams".equals(lazyField) ? detached.getTeams() : detached.getTags()),
          "The fixture must leave " + lazyField + " lazy");
      TenantContext.clearCurrentTenant();
      return new BaseEvent(ModelBaseListener.DATA_UPDATE, detached, mock(ObjectMapper.class));
    }

    private void assertLeftLazyAndLeftOut(
        String lazyField, String jsonField, boolean isPlayerLeftOut) {
      // -- PREPARE --
      Consumer ofDefault = consumerOfDefault();
      BaseEvent event = eventOfAUserLeavingLazy(lazyField);
      User detached = (User) event.getInstance();

      // -- EXECUTE --
      stream.listenDatabaseUpdate(event);

      // -- ASSERT --
      soft(
          () -> {
            assertNothingFailedToStream();
            assertEquals(1, ofDefault.received().size(), "one event per consumer");
            assertEquals(
                detached.getId(), ofDefault.received().getFirst().path("user_id").asText());
          });
      assertLeftOut(ofDefault, jsonField);
      if (isPlayerLeftOut) {
        assertIsPlayer(ofDefault, null);
      }
      assertKnownFields(ofDefault);
      soft(
          () ->
              assertFalse(
                  Hibernate.isInitialized(
                      "teams".equals(lazyField) ? detached.getTeams() : detached.getTags()),
                  "The delivery must not initialize " + lazyField));
    }

    @Test
    @DisplayName("A user whose teams are not loaded is delivered without them, still not loaded")
    void given_teamsNotLoaded_should_deliverTheUserWithoutTeams() {
      assertLeftLazyAndLeftOut("teams", "user_teams", true);
    }

    @Test
    @DisplayName("A user whose tags are not loaded is delivered without them, still not loaded")
    void given_tagsNotLoaded_should_deliverTheUserWithoutTags() {
      assertLeftLazyAndLeftOut("tags", "user_tags", false);
    }
  }

  @Nested
  @DisplayName("User updated from the platform, with no tenant named")
  class UpdatedFromThePlatform {

    private static final String NEW_NAME = "Written-by-the-platform";

    private List<BaseEvent> updateFromThePlatform() throws Exception {
      UserInput input =
          new UserInput(
              player.getEmail(),
              NEW_NAME,
              "User",
              null,
              null,
              null,
              null,
              null,
              List.of(tagA, tagB),
              false,
              List.of(Tenant.DEFAULT_TENANT_UUID, tenantA, tenantB));
      return writeAndStream(
          player.getId(),
          () ->
              mvc.perform(json(put(PLATFORM_USER_URI + "/" + player.getId()), input))
                  .andExpect(status().is2xxSuccessful()));
    }

    @Test
    @DisplayName("Each consumer receives the ids of its own tenant among the ones that were loaded")
    void given_platformUpdate_should_sendEachConsumerTheIdsOfItsTenant() throws Exception {
      // -- PREPARE --
      Consumer ofA = consumerOfA();
      Consumer ofB = consumerOfB();
      Consumer ofDefault = consumerOfDefault();

      // -- EXECUTE --
      List<BaseEvent> events = updateFromThePlatform();

      // -- ASSERT --
      assertEquals(Tenant.DEFAULT_TENANT_UUID, events.getFirst().getTenantId());
      for (Consumer consumer : List.of(ofA, ofB, ofDefault)) {
        assertDelivered(consumer, events, "user_firstname", NEW_NAME);
        assertLeftOut(consumer, "user_communications");
        assertLeftOut(consumer, "team_exercises_users");
        assertLeftOut(consumer, "user_grants");
        assertLeftOut(consumer, "user_home_dashboard");
      }
      assertIds(ofA, "user_teams", List.of(teamA));
      assertIds(ofA, "user_tags", List.of(tagA));
      assertIds(ofB, "user_teams", List.of(teamB));
      assertIds(ofB, "user_tags", List.of(tagB));
      assertIds(ofDefault, "user_teams", List.of());
      assertIds(ofDefault, "user_tags", List.of());
      assertNoIdOutsideTheScope(ofA, tagA, teamA, groupA, groupPlatform);
      assertNoIdOutsideTheScope(ofB, tagB, teamB, groupB, groupPlatform);
      assertNoIdOutsideTheScope(ofDefault, groupPlatform);
    }
  }

  @Nested
  @DisplayName("Player created in tenant A")
  class CreatedInA {

    @Test
    @DisplayName("The consumers of A receive the links of A, the other consumers none")
    void given_creationInA_should_sendTheLinksOfAToTheConsumersOfAOnly() throws Exception {
      // -- PREPARE --
      Consumer ofA = consumerOfA();
      Consumer ofB = consumerOfB();
      Consumer ofDefault = consumerOfDefault();
      String email = "stream-created-" + UUID.randomUUID() + "@filigran.io";
      PlayerInput input = playerInput(email, "Created-in-a", List.of(tagA));
      String[] createdId = new String[1];

      // -- EXECUTE --
      TenantContext.clearCurrentTenant();
      long publishedBefore = applicationEvents.stream(BaseEvent.class).count();
      String response =
          mvc.perform(json(post(PLAYER_URI).header(TENANT_HEADER, tenantA), input))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      createdId[0] = mapper.readTree(response).path("user_id").asText();
      createdUserIds.add(createdId[0]);
      List<BaseEvent> events =
          applicationEvents.stream(BaseEvent.class)
              .skip(publishedBefore)
              .filter(
                  event ->
                      event.getInstance() instanceof User user && createdId[0].equals(user.getId()))
              .toList();
      assertFalse(events.isEmpty(), "The creation must have published an event for the player");
      events.forEach(
          event -> {
            TenantContext.clearCurrentTenant();
            stream.listenDatabaseUpdate(event);
          });

      // -- ASSERT --
      assertNothingFailedToStream();
      for (Consumer consumer : List.of(ofA, ofB, ofDefault)) {
        assertEquals(events.size(), consumer.received().size(), consumer.name() + " events");
        consumer
            .received()
            .forEach(
                instance -> {
                  assertEquals(createdId[0], instance.path("user_id").asText());
                  assertEquals("Created-in-a", instance.path("user_firstname").asText());
                });
        assertKnownFields(consumer);
        assertLeftOut(consumer, "user_communications");
        assertLeftOut(consumer, "team_exercises_users");
        assertLeftOut(consumer, "user_grants");
        assertLeftOut(consumer, "user_home_dashboard");
      }
      assertIds(ofA, "user_tags", List.of(tagA));
      for (Consumer consumer : List.of(ofB, ofDefault)) {
        assertLeftOut(consumer, "user_tags");
        assertLeftOut(consumer, "user_teams");
      }
      assertNoIdOutsideTheScope(ofB, groupPlatform);
      assertNoIdOutsideTheScope(ofDefault, groupPlatform);
    }
  }

  @Nested
  @DisplayName("Player deleted from its tenants")
  class Deleted {

    private void deleteFrom(String tenantId) throws Exception {
      TenantContext.clearCurrentTenant();
      mvc.perform(
              delete(PLAYER_URI + "/" + player.getId())
                  .header(TENANT_HEADER, tenantId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("The deletion of the account carries no id outside the tenant of the consumer")
    void given_lastDetachFromB_should_sendTheDeletionWithTheIdsOfTheConsumerTenant()
        throws Exception {
      // -- PREPARE --
      Consumer ofA = consumerOfA();
      Consumer ofB = consumerOfB();
      Consumer ofDefault = consumerOfDefault();
      Write detachFromA = () -> deleteFrom(tenantA);
      Write detachFromDefault =
          () -> {
            jdbc.update(
                "DELETE FROM users_tenants WHERE user_id = ? AND tenant_id = ?",
                player.getId(),
                Tenant.DEFAULT_TENANT_UUID);
          };
      detachFromDefault.run();
      detachFromA.run();
      ofA.received().clear();
      ofB.received().clear();
      ofDefault.received().clear();
      ofA.eventTypes().clear();
      ofB.eventTypes().clear();
      ofDefault.eventTypes().clear();
      warnings.list.clear();

      // -- EXECUTE --
      List<BaseEvent> events = writeAndStream(player.getId(), () -> deleteFrom(tenantB));

      // -- ASSERT --
      List<BaseEvent> deletions =
          events.stream()
              .filter(event -> ModelBaseListener.DATA_DELETE.equals(event.getType()))
              .toList();
      assertEquals(1, deletions.size(), "The account is deleted once");
      assertEquals(tenantB, deletions.getFirst().getTenantId());
      assertNothingFailedToStream();
      for (Consumer consumer : List.of(ofA, ofB, ofDefault)) {
        assertEquals(events.size(), consumer.received().size(), consumer.name() + " events");
        assertTrue(consumer.eventTypes().contains(ModelBaseListener.DATA_DELETE));
        assertKnownFields(consumer);
        assertLeftOut(consumer, "user_communications");
        assertLeftOut(consumer, "team_exercises_users");
        assertLeftOut(consumer, "user_grants");
        assertLeftOut(consumer, "user_home_dashboard");
      }
      assertNoIdOutsideTheScope(ofA, tagA, teamA, simulationA, groupA, groupPlatform);
      assertNoIdOutsideTheScope(ofB, tagB, teamB, simulationB, groupB, groupPlatform);
      assertNoIdOutsideTheScope(ofDefault, groupPlatform);
    }
  }
}
