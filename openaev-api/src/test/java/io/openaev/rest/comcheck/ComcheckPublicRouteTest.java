package io.openaev.rest.comcheck;

import static io.openaev.config.TenantUriUtils.TENANT_BASE_PATH;
import static io.openaev.rest.comcheck.ComcheckApi.ALREADY_ANSWERED;
import static io.openaev.rest.comcheck.ComcheckApi.CLOSED;
import static io.openaev.rest.comcheck.ComcheckApi.COMCHECK_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.DefaultOpenAEVPrincipal;
import io.openaev.config.OpenAEVPrincipal;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Comcheck;
import io.openaev.database.model.ComcheckStatus;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ComcheckRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.StreamService;
import io.openaev.utils.fixtures.ComcheckFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utilstest.RowTenantScope;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.FluxSink;

/**
 * The page a comcheck mail links to ({@code <base>/comcheck/<status id>}) has no tenant in its URL,
 * so it calls the public route with no tenant either, anonymously. The status id alone names the
 * comcheck, whatever the tenant of its simulation. The rows are read through JDBC on the test
 * connection, which the statement inspector does not rewrite. The live update of an answer is
 * checked on committed rows, outside the test transaction.
 */
@TestInstance(PER_CLASS)
@DisplayName("Public comcheck route")
class ComcheckPublicRouteTest extends IntegrationTest {

  private static final String RECEIVE_DATE =
      "SELECT status_receive_date FROM comchecks_statuses WHERE status_id = ?";
  private static final String COMCHECK_STATE =
      "SELECT comcheck_state FROM comchecks WHERE comcheck_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ComcheckRepository comcheckRepository;
  @Autowired private RowTenantScope rowTenantScope;

  private String tenantB;
  private String comcheckId;
  private String statusId;

  private record Seeded(String comcheckId, List<String> statusIds) {}

  /** A comcheck of the second tenant in the given state, waiting for the answer of each player. */
  private Seeded seedInSecondTenant(int players, Comcheck.COMCHECK_STATUS state) {
    Seeded[] seeded = new Seeded[1];
    rowTenantScope.write(tenantB, () -> seeded[0] = seedComcheck(tenantB, players, state));
    // Inside the test transaction the rows are flushed, so the request reads them; outside it
    // they are committed by the write.
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      entityManager.flush();
      entityManager.clear();
    }
    // The mail link is opened by a browser with no session, on a thread that carries no tenant.
    TenantContext.clearCurrentTenant();
    return seeded[0];
  }

  /** A simulation of the tenant, and a comcheck of it with one status per player. */
  private Seeded seedComcheck(String tenantId, int players, Comcheck.COMCHECK_STATUS state) {
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    simulation.setTenant(new Tenant(tenantId));
    simulation = exerciseRepository.save(simulation);
    Comcheck comcheck = ComcheckFixture.createComcheck(simulation);
    comcheck.setState(state);
    for (int i = 0; i < players; i++) {
      User player =
          userRepository.save(
              UserFixture.getUser(
                  "first", "last", "comcheck-" + UUID.randomUUID() + "@unittests.invalid", false));
      ComcheckStatus status = new ComcheckStatus(player);
      status.setComcheck(comcheck);
      comcheck.getComcheckStatus().add(status);
    }
    comcheck = comcheckRepository.save(comcheck);
    return new Seeded(
        comcheck.getId(),
        comcheck.getComcheckStatus().stream().map(ComcheckStatus::getId).toList());
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @Transactional
  @DisplayName("Anonymous caller")
  class AnonymousCaller {

    @BeforeEach
    void seedComcheckInSecondTenant() {
      tenantB =
          tenantRepository
              .save(TenantFixture.getTenant("comcheck-public-" + UUID.randomUUID()))
              .getId();
      Seeded seeded = seedInSecondTenant(1, Comcheck.COMCHECK_STATUS.RUNNING);
      comcheckId = seeded.comcheckId();
      statusId = seeded.statusIds().getFirst();
    }

    @Test
    @DisplayName(
        "Given a comcheck of a second tenant, should record the answer on the public route")
    void given_comcheckOfSecondTenant_should_recordTheAnswerOnThePublicRoute() throws Exception {
      // Act
      mvc.perform(get(COMCHECK_URI + "/" + statusId))
          // Assert
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.comcheckstatus_id").value(statusId))
          .andExpect(jsonPath("$.comcheckstatus_state").value("SUCCESS"));
      assertThat(column(entityManager, RECEIVE_DATE, statusId)).hasSize(1).doesNotContainNull();
      assertThat(column(entityManager, COMCHECK_STATE, comcheckId)).containsExactly("FINISHED");
    }

    @Test
    @DisplayName("Given the only answer already recorded, should answer 409 and keep the first one")
    void given_onlyAnswerAlreadyRecorded_should_answer409AndKeepTheFirstOne() throws Exception {
      // Arrange
      mvc.perform(get(COMCHECK_URI + "/" + statusId)).andExpect(status().isOk());
      List<String> firstAnswer = column(entityManager, RECEIVE_DATE, statusId);
      // Act
      mvc.perform(get(COMCHECK_URI + "/" + statusId))
          // Assert
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value(ALREADY_ANSWERED));
      assertThat(column(entityManager, RECEIVE_DATE, statusId)).isEqualTo(firstAnswer);
      assertThat(column(entityManager, COMCHECK_STATE, comcheckId)).containsExactly("FINISHED");
    }

    @Test
    @DisplayName(
        "Given an answer already recorded in a comcheck still waiting for others, should answer 409 and keep the first one")
    void given_answerRecordedInRunningComcheck_should_answer409AndKeepTheFirstOne()
        throws Exception {
      // Arrange
      Seeded seeded = seedInSecondTenant(2, Comcheck.COMCHECK_STATUS.RUNNING);
      String answered = seeded.statusIds().get(0);
      mvc.perform(get(COMCHECK_URI + "/" + answered)).andExpect(status().isOk());
      List<String> firstAnswer = column(entityManager, RECEIVE_DATE, answered);
      // Act
      mvc.perform(get(COMCHECK_URI + "/" + answered))
          // Assert
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value(ALREADY_ANSWERED));
      assertThat(column(entityManager, RECEIVE_DATE, answered)).isEqualTo(firstAnswer);
      assertThat(column(entityManager, COMCHECK_STATE, seeded.comcheckId()))
          .containsExactly("RUNNING");
    }

    @Test
    @DisplayName("Given an expired comcheck, should answer 409 and record nothing")
    void given_expiredComcheck_should_answer409AndRecordNothing() throws Exception {
      // Arrange
      Seeded seeded = seedInSecondTenant(1, Comcheck.COMCHECK_STATUS.EXPIRED);
      String expiredStatusId = seeded.statusIds().getFirst();
      // Act
      mvc.perform(get(COMCHECK_URI + "/" + expiredStatusId))
          // Assert
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value(CLOSED));
      assertThat(column(entityManager, RECEIVE_DATE, expiredStatusId)).containsOnlyNulls();
      assertThat(column(entityManager, COMCHECK_STATE, seeded.comcheckId()))
          .containsExactly("EXPIRED");
    }

    @Test
    @DisplayName("Given a tenant in the path, should refuse it and record nothing")
    void given_tenantInThePath_should_refuseItAndRecordNothing() throws Exception {
      // Act
      mvc.perform(get(TENANT_BASE_PATH + Tenant.DEFAULT_TENANT_UUID + "/comcheck/" + statusId))
          // Assert
          .andExpect(status().isUnauthorized());
      assertThat(column(entityManager, RECEIVE_DATE, statusId)).containsOnlyNulls();
      assertThat(column(entityManager, COMCHECK_STATE, comcheckId)).containsExactly("RUNNING");
    }
  }

  /**
   * The live update of an answer reaches the stream consumers of the simulation's tenant. Real
   * stack: the rows are committed, the entity listener publishes the events after the commit of the
   * request, and the real {@link StreamService} bean delivers them on its own executor. A comcheck
   * and its statuses have no resource of their own, so only an admin reads their events: the
   * consumers are admins, one per tenant, and only the tenant routing tells them apart. A consumer
   * that must not receive a row is checked once a later row of its own tenant, the barrier, has
   * reached it.
   */
  @Nested
  @DisplayName("Live update of an answer")
  class LiveUpdate {

    private static final long DELIVERY_TIMEOUT_MS = 10_000;

    @Autowired private StreamService streamService;
    @Autowired private TenantScopedTransaction tenantTx;
    @Autowired private JdbcTemplate jdbc;

    private final List<String> sessionIds = new ArrayList<>();
    private final List<String> barrierIds = new ArrayList<>();
    private List<String> playerIds = List.of();
    private Map<String, Object> consumers;

    @BeforeEach
    void committedComcheckInSecondTenant() {
      tenantB = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
              + " VALUES (?, ?, now(), now())",
          tenantB,
          "comcheck-live-" + tenantB);
      Seeded seeded = seedInSecondTenant(1, Comcheck.COMCHECK_STATUS.RUNNING);
      comcheckId = seeded.comcheckId();
      statusId = seeded.statusIds().getFirst();
      playerIds =
          jdbc.queryForList(
              "SELECT status_user FROM comchecks_statuses WHERE status_comcheck = ?",
              String.class,
              comcheckId);
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
      for (String barrierId : barrierIds) {
        jdbc.update("DELETE FROM comchecks WHERE comcheck_id = ?", barrierId);
      }
      barrierIds.clear();
      jdbc.update("DELETE FROM comchecks_statuses WHERE status_comcheck = ?", comcheckId);
      jdbc.update("DELETE FROM comchecks WHERE comcheck_id = ?", comcheckId);
      jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenantB);
      for (String playerId : playerIds) {
        jdbc.update("DELETE FROM users WHERE user_id = ?", playerId);
      }
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantB);
    }

    @SuppressWarnings("unchecked")
    private FluxSink<Object> adminConsumerOf(String consumerTenantId) throws Exception {
      FluxSink<Object> sink = mock(FluxSink.class);
      OpenAEVPrincipal principal =
          new DefaultOpenAEVPrincipal(User.ADMIN_UUID, List.of(), true, "en");
      Class<?> consumerType = Class.forName("io.openaev.service.StreamService$StreamConsumer");
      Constructor<?> constructor =
          consumerType.getDeclaredConstructor(OpenAEVPrincipal.class, String.class, FluxSink.class);
      constructor.setAccessible(true);
      String sessionId = "comcheck-live-" + UUID.randomUUID();
      sessionIds.add(sessionId);
      consumers.put(sessionId, constructor.newInstance(principal, consumerTenantId, sink));
      return sink;
    }

    /**
     * Writes the barrier of the default tenant: a comcheck with no simulation, in a transaction
     * scoped to the default tenant.
     */
    private String barrierOfTheDefaultTenant() {
      String barrierId =
          tenantTx.execute(
              TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
              () -> comcheckRepository.save(ComcheckFixture.createComcheck(null)).getId());
      barrierIds.add(barrierId);
      return barrierId;
    }

    private static boolean isEventOf(Object message, String rowId, String tenantId) {
      return message instanceof ServerSentEvent<?> event
          && StreamService.EVENT_TYPE_MESSAGE.equals(event.event())
          && event.data() instanceof BaseEvent baseEvent
          && rowId.equals(baseEvent.getInstance().getId())
          && (tenantId == null || tenantId.equals(baseEvent.getTenantId()));
    }

    private static void assertReceives(FluxSink<Object> sink, String rowId, String tenantId) {
      verify(sink, timeout(DELIVERY_TIMEOUT_MS)).next(argThat(m -> isEventOf(m, rowId, tenantId)));
    }

    private static void assertReceivesNot(FluxSink<Object> sink, String rowId) {
      verify(sink, never()).next(argThat(m -> isEventOf(m, rowId, null)));
    }

    @Test
    @DisplayName(
        "given a player of a second tenant answering on the public route, should stream the status"
            + " and the finished comcheck to the consumer of that tenant and not to the one of the"
            + " default tenant")
    void given_playerOfSecondTenantAnswering_should_streamTheAnswerToThatTenantOnly()
        throws Exception {
      // Arrange
      FluxSink<Object> consumerOfB = adminConsumerOf(tenantB);
      FluxSink<Object> consumerOfDefault = adminConsumerOf(Tenant.DEFAULT_TENANT_UUID);
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // Act
      mvc.perform(get(COMCHECK_URI + "/" + statusId)).andExpect(status().isOk());

      // Assert
      assertReceives(consumerOfB, statusId, tenantB);
      assertReceives(consumerOfB, comcheckId, tenantB);
      assertReceives(consumerOfDefault, barrierOfTheDefaultTenant(), Tenant.DEFAULT_TENANT_UUID);
      assertReceivesNot(consumerOfDefault, statusId);
      assertReceivesNot(consumerOfDefault, comcheckId);
    }
  }
}
