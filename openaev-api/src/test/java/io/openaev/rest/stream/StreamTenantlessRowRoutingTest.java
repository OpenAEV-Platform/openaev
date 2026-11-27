package io.openaev.rest.stream;

import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.objective.ExerciseObjectiveApi.EXERCISE_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.DefaultOpenAEVPrincipal;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.model.Objective;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.ObjectiveRepository;
import io.openaev.service.StreamService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.LessonsCategoryFixture;
import io.openaev.utils.fixtures.ObjectiveFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.FluxSink;

/**
 * The event of a row with no tenant column (an objective, a lessons category, an expectation)
 * reaches the stream consumers of the tenant it is written in, and no other.
 *
 * <p>Real stack: the rows are written and committed in tenant B, the entity listener publishes the
 * event, and the real {@link StreamService} bean delivers it on its own executor. The readers are
 * not admins: each holds the assessment capability in one tenant only, so the read check lets the
 * event through and only the tenant routing can hold it back. A consumer of tenant A stands for the
 * {@code /api/tenants/{tenantId}/stream} route, and a consumer of the default tenant for {@code
 * /api/stream}: {@code StreamApiTenantRouteTest} pins that each route registers that tenant.
 *
 * <p>A consumer that must not receive a row is checked once a later row of its own tenant, the
 * barrier, has reached it: the barrier is written after the row reached the consumer of its own
 * tenant, so the fan-out of the row has started before the barrier exists.
 *
 * <p>Not {@code @Transactional}: the stream listener fires after commit. The committed rows go with
 * their tenants in {@code @AfterEach}, and the simulation of the default tenant with its rows.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Stream routing of the event of a row with no tenant column")
class StreamTenantlessRowRoutingTest extends IntegrationTest {

  private static final long DELIVERY_TIMEOUT_MS = 10_000;
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final Set<Capability> READER_CAPABILITIES = Set.of(Capability.ACCESS_ASSESSMENT);

  @Autowired private StreamService streamService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ObjectiveRepository objectiveRepository;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;

  private String tenantA;
  private String tenantB;
  private final Map<String, Simulation> simulations = new java.util.HashMap<>();
  private final List<String> readerIds = new ArrayList<>();
  private final List<String> defaultTenantGroupIds = new ArrayList<>();
  private final List<String> defaultTenantRoleIds = new ArrayList<>();
  private final List<String> sessionIds = new ArrayList<>();
  private Map<String, Object> consumers;

  /** A simulation and one of its injects, in one tenant. */
  private record Simulation(String exerciseId, String injectId) {}

  /** A row with no tenant column, written as a background job writes it. */
  enum TenantlessRow {
    OBJECTIVE,
    LESSONS_CATEGORY,
    EXPECTATION
  }

  @BeforeEach
  void twoTenantsWithASimulationEach() {
    tenantA = insertTenant("stream-tenantless-a");
    tenantB = insertTenant("stream-tenantless-b");
    simulations.put(tenantA, seedSimulation(tenantA));
    simulations.put(tenantB, seedSimulation(tenantB));
    simulations.put(Tenant.DEFAULT_TENANT_UUID, seedSimulation(Tenant.DEFAULT_TENANT_UUID));
    StreamService target = AopTestUtils.getUltimateTargetObject(streamService);
    @SuppressWarnings("unchecked")
    Map<String, Object> map =
        (Map<String, Object>) ReflectionTestUtils.getField(target, "consumers");
    consumers = map;
  }

  @AfterEach
  void removeTheConsumersAndTheRows() {
    sessionIds.forEach(consumers::remove);
    sessionIds.clear();
    for (String userId : readerIds) {
      jdbc.update("DELETE FROM users_groups WHERE user_id = ?", userId);
    }
    for (String groupId : defaultTenantGroupIds) {
      jdbc.update("DELETE FROM groups_roles WHERE group_id = ?", groupId);
      jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    }
    for (String roleId : defaultTenantRoleIds) {
      jdbc.update("DELETE FROM roles WHERE role_id = ?", roleId);
    }
    for (String userId : readerIds) {
      jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }
    readerIds.clear();
    defaultTenantGroupIds.clear();
    defaultTenantRoleIds.clear();
    jdbc.update(
        "DELETE FROM exercises WHERE exercise_id = ?",
        simulations.get(Tenant.DEFAULT_TENANT_UUID).exerciseId());
    simulations.clear();
    tenantIsolationHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  // -- Arrange helpers --

  private String insertTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name + "-" + id);
    return id;
  }

  private Simulation seedSimulation(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      Exercise exercise = ExerciseFixture.createDefaultExercise();
      exercise.setTenant(new Tenant(tenantId));
      Inject inject = InjectFixture.getDefaultInject();
      exerciseComposer.forExercise(exercise).withInject(injectComposer.forInject(inject)).persist();
      return new Simulation(exercise.getId(), inject.getId());
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  /** A user who is no admin, a member of {@code tenantId} only, reading simulations there. */
  private String readerOf(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      User[] reader = new User[1];
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> {
            TenantRoleComposer.Composer role =
                tenantRoleComposer.forRole(TenantRoleFixture.getRole(READER_CAPABILITIES));
            TenantGroupComposer.Composer group =
                tenantGroupComposer.forGroup(TenantGroupFixture.getGroup()).withRole(role);
            reader[0] =
                userComposer
                    .forUser(
                        UserFixture.getUser(
                            "Stream", "Reader", UUID.randomUUID() + "@unittests.invalid"))
                    .withGroup(group)
                    .persist()
                    .get();
            if (Tenant.DEFAULT_TENANT_UUID.equals(tenantId)) {
              defaultTenantGroupIds.add(group.get().getId());
              defaultTenantRoleIds.add(role.get().getId());
            }
          });
      readerIds.add(reader[0].getId());
      tenantIsolationHelper.attachUserToTenant(reader[0].getId(), tenantId);
      return reader[0].getId();
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  @SuppressWarnings("unchecked")
  private FluxSink<Object> subscribe(String userId, boolean admin, String consumerTenantId)
      throws Exception {
    FluxSink<Object> sink = mock(FluxSink.class);
    OpenAEVPrincipal principal = new DefaultOpenAEVPrincipal(userId, List.of(), admin, "en");
    Class<?> consumerType = Class.forName("io.openaev.service.StreamService$StreamConsumer");
    Constructor<?> constructor =
        consumerType.getDeclaredConstructor(OpenAEVPrincipal.class, String.class, FluxSink.class);
    constructor.setAccessible(true);
    String sessionId = "stream-tenantless-" + UUID.randomUUID();
    sessionIds.add(sessionId);
    consumers.put(sessionId, constructor.newInstance(principal, consumerTenantId, sink));
    return sink;
  }

  // -- Act helpers --

  /** Writes the row in a transaction scoped to {@code tenantId}, as a background job writes it. */
  private String writeInTenant(TenantlessRow row, String tenantId) {
    Simulation simulation = simulations.get(tenantId);
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Exercise exercise = exerciseRepository.findById(simulation.exerciseId()).orElseThrow();
          return switch (row) {
            case OBJECTIVE -> {
              Objective objective = ObjectiveFixture.getDefaultObjective();
              objective.setExercise(exercise);
              yield objectiveRepository.save(objective).getId();
            }
            case LESSONS_CATEGORY -> {
              LessonsCategory category = LessonsCategoryFixture.createDefaultLessonsCategory();
              category.setExercise(exercise);
              yield lessonsCategoryRepository.save(category).getId();
            }
            case EXPECTATION ->
                injectExpectationRepository
                    .save(
                        InjectExpectationFixture.createManualInjectExpectationWithExercise(
                            null,
                            injectRepository.findById(simulation.injectId()).orElseThrow(),
                            exercise,
                            "stream-tenantless-" + UUID.randomUUID()))
                    .getId();
          };
        });
  }

  /** Creates an objective through the API, on the route that names {@code tenantId}. */
  private String createObjectiveOnTheRouteOf(String tenantId) throws Exception {
    tenantIsolationHelper.attachCurrentUserToTenant(tenantId);
    String body =
        mvc.perform(
                post(TENANT_PREFIX.replace("{" + TENANT_ID_PATH_VARIABLE + "}", tenantId)
                        + "/exercises/"
                        + simulations.get(tenantId).exerciseId()
                        + "/objectives")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"objective_title\":\"stream routing\",\"objective_priority\":1}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode created = mapper.readTree(body);
    return created.get("objective_id").asText();
  }

  /**
   * Creates an objective through the API, on the route that names no tenant, with {@code tenantId}
   * in the header. That route sets no thread-local tenant, so the thread falls back to the default
   * tenant while the transaction scope names {@code tenantId}.
   */
  private String createObjectiveOnTheHeaderRouteOf(String tenantId) throws Exception {
    tenantIsolationHelper.attachCurrentUserToTenant(tenantId);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    String body =
        mvc.perform(
                post(EXERCISE_URI + simulations.get(tenantId).exerciseId() + "/objectives")
                    .header(TENANT_IDS_HEADER, tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"objective_title\":\"header route\",\"objective_priority\":1}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).get("objective_id").asText();
  }

  /**
   * Creates an objective of the simulation of {@code tenantId} through the API, on the route that
   * names no tenant and with no header: for a caller in several tenants, the scope of the request
   * holds them all and pins none.
   */
  private String createObjectiveOnTheRouteWithNoTenantOf(String tenantId) throws Exception {
    tenantIsolationHelper.attachCurrentUserToTenant(tenantId);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    String body =
        mvc.perform(
                post(EXERCISE_URI + simulations.get(tenantId).exerciseId() + "/objectives")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"objective_title\":\"no tenant\",\"objective_priority\":1}")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).get("objective_id").asText();
  }

  // -- Assert helpers --

  private static boolean isEventOf(Object message, String rowId) {
    return message instanceof ServerSentEvent<?> event
        && StreamService.EVENT_TYPE_MESSAGE.equals(event.event())
        && event.data() instanceof BaseEvent baseEvent
        && rowId.equals(baseEvent.getInstance().getId());
  }

  private static void assertReceives(FluxSink<Object> sink, String rowId) {
    verify(sink, timeout(DELIVERY_TIMEOUT_MS)).next(argThat(m -> isEventOf(m, rowId)));
  }

  /**
   * Writes the barrier of {@code tenantId}: an objective its consumers receive. Called once the row
   * under test has reached the consumer of its own tenant.
   */
  private String barrierOf(String tenantId) {
    return writeInTenant(TenantlessRow.OBJECTIVE, tenantId);
  }

  /** The consumer receives the barrier of its tenant, and the row under test is not before it. */
  private static void assertReceivesTheBarrierAndNotTheRow(
      FluxSink<Object> sink, String barrierId, String rowId) {
    assertReceives(sink, barrierId);
    verify(sink, never()).next(argThat(m -> isEventOf(m, rowId)));
  }

  @Nested
  @DisplayName("A row written in a background transaction of tenant B")
  class WrittenOffRequest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(TenantlessRow.class)
    @DisplayName(
        "given a row of tenant B, should reach the consumer of tenant B and none of tenant A or of"
            + " the default tenant")
    void given_rowOfTenantB_should_reachTheConsumerOfTenantBOnly(TenantlessRow row)
        throws Exception {
      // Arrange: a lessons category is read by admins only, so an admin of B witnesses delivery
      FluxSink<Object> adminOnB = subscribe(User.ADMIN_UUID, true, tenantB);
      FluxSink<Object> readerOfA = subscribe(readerOf(tenantA), false, tenantA);
      FluxSink<Object> adminOnA = subscribe(User.ADMIN_UUID, true, tenantA);
      FluxSink<Object> readerOfDefault =
          subscribe(readerOf(Tenant.DEFAULT_TENANT_UUID), false, Tenant.DEFAULT_TENANT_UUID);

      // Act
      String rowId = writeInTenant(row, tenantB);

      // Assert
      assertReceives(adminOnB, rowId);
      String barrierOfA = barrierOf(tenantA);
      String barrierOfDefault = barrierOf(Tenant.DEFAULT_TENANT_UUID);
      assertReceivesTheBarrierAndNotTheRow(readerOfA, barrierOfA, rowId);
      assertReceivesTheBarrierAndNotTheRow(adminOnA, barrierOfA, rowId);
      assertReceivesTheBarrierAndNotTheRow(readerOfDefault, barrierOfDefault, rowId);
    }

    @Test
    @DisplayName(
        "given a row of tenant A, should reach the reader of tenant A and not the one of tenant B")
    void given_rowOfTenantA_should_reachTheReaderOfTenantAOnly() throws Exception {
      // Arrange
      FluxSink<Object> readerOfA = subscribe(readerOf(tenantA), false, tenantA);
      FluxSink<Object> readerOfB = subscribe(readerOf(tenantB), false, tenantB);

      // Act
      String rowId = writeInTenant(TenantlessRow.OBJECTIVE, tenantA);

      // Assert
      assertReceives(readerOfA, rowId);
      assertReceivesTheBarrierAndNotTheRow(readerOfB, barrierOf(tenantB), rowId);
    }

    @Test
    @DisplayName(
        "given a row of the default tenant, should reach the reader of the default tenant and not"
            + " the one of tenant A")
    void given_rowOfTheDefaultTenant_should_reachTheReaderOfTheDefaultTenantOnly()
        throws Exception {
      // Arrange
      FluxSink<Object> readerOfDefault =
          subscribe(readerOf(Tenant.DEFAULT_TENANT_UUID), false, Tenant.DEFAULT_TENANT_UUID);
      FluxSink<Object> readerOfA = subscribe(readerOf(tenantA), false, tenantA);

      // Act
      String rowId = writeInTenant(TenantlessRow.OBJECTIVE, Tenant.DEFAULT_TENANT_UUID);

      // Assert
      assertReceives(readerOfDefault, rowId);
      assertReceivesTheBarrierAndNotTheRow(readerOfA, barrierOf(tenantA), rowId);
    }
  }

  @Nested
  @DisplayName("A row written by a request in tenant B")
  class WrittenOnRequest {

    @Test
    @DisplayName(
        "given an objective created on the route of tenant B, should reach the reader of tenant B"
            + " and none of tenant A or of the default tenant")
    void given_objectiveCreatedOnTheRouteOfTenantB_should_reachTheReaderOfTenantBOnly()
        throws Exception {
      // Arrange
      FluxSink<Object> readerOfB = subscribe(readerOf(tenantB), false, tenantB);
      FluxSink<Object> readerOfA = subscribe(readerOf(tenantA), false, tenantA);
      FluxSink<Object> adminOnA = subscribe(User.ADMIN_UUID, true, tenantA);
      FluxSink<Object> readerOfDefault =
          subscribe(readerOf(Tenant.DEFAULT_TENANT_UUID), false, Tenant.DEFAULT_TENANT_UUID);

      // Act
      String objectiveId = createObjectiveOnTheRouteOf(tenantB);

      // Assert
      assertReceives(readerOfB, objectiveId);
      String barrierOfA = barrierOf(tenantA);
      assertReceivesTheBarrierAndNotTheRow(readerOfA, barrierOfA, objectiveId);
      assertReceivesTheBarrierAndNotTheRow(adminOnA, barrierOfA, objectiveId);
      assertReceivesTheBarrierAndNotTheRow(
          readerOfDefault, barrierOf(Tenant.DEFAULT_TENANT_UUID), objectiveId);
    }

    @Test
    @DisplayName(
        "given an objective created on the route that names no tenant with tenant B in the header,"
            + " should reach the reader of tenant B and not the one of the default tenant")
    void given_objectiveCreatedWithTenantBInTheHeader_should_reachTheReaderOfTenantBOnly()
        throws Exception {
      // Arrange
      FluxSink<Object> readerOfB = subscribe(readerOf(tenantB), false, tenantB);
      FluxSink<Object> readerOfDefault =
          subscribe(readerOf(Tenant.DEFAULT_TENANT_UUID), false, Tenant.DEFAULT_TENANT_UUID);

      // Act
      String objectiveId = createObjectiveOnTheHeaderRouteOf(tenantB);

      // Assert
      assertReceives(readerOfB, objectiveId);
      assertReceivesTheBarrierAndNotTheRow(
          readerOfDefault, barrierOf(Tenant.DEFAULT_TENANT_UUID), objectiveId);
    }
  }

  @Nested
  @DisplayName("A row written by a request whose scope holds several tenants")
  class WrittenOnRequestOfSeveralTenants {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given an objective of tenant B created on the route that names no tenant by a caller in"
            + " the default tenant and tenant B, should reach the reader of neither tenant")
    void given_objectiveCreatedOnTheRouteWithNoTenantByACallerInTwoTenants_should_reachNoReader()
        throws Exception {
      // Arrange: a consumer with no tenant receives every event, so it witnesses the fan-out
      FluxSink<Object> readerOfB = subscribe(readerOf(tenantB), false, tenantB);
      FluxSink<Object> readerOfDefault =
          subscribe(readerOf(Tenant.DEFAULT_TENANT_UUID), false, Tenant.DEFAULT_TENANT_UUID);
      FluxSink<Object> adminWithNoTenant = subscribe(User.ADMIN_UUID, true, null);

      // Act
      String objectiveId = createObjectiveOnTheRouteWithNoTenantOf(tenantB);

      // Assert: the same row written on the route of tenant B reaches the reader of tenant B
      assertReceives(adminWithNoTenant, objectiveId);
      String objectiveOnTheRouteOfB = createObjectiveOnTheRouteOf(tenantB);
      assertReceivesTheBarrierAndNotTheRow(readerOfB, objectiveOnTheRouteOfB, objectiveId);
      assertReceivesTheBarrierAndNotTheRow(
          readerOfDefault, barrierOf(Tenant.DEFAULT_TENANT_UUID), objectiveId);
    }
  }

  @Nested
  @DisplayName("A consumer with no tenant")
  class ConsumerWithNoTenant {

    @Test
    @DisplayName("given a consumer with no tenant, should receive the row of tenant B")
    void given_consumerWithNoTenant_should_receiveTheRowOfTenantB() throws Exception {
      // Arrange
      FluxSink<Object> adminWithNoTenant = subscribe(User.ADMIN_UUID, true, null);

      // Act
      String rowId = writeInTenant(TenantlessRow.OBJECTIVE, tenantB);

      // Assert
      assertReceives(adminWithNoTenant, rowId);
    }
  }
}
