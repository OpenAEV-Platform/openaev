package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.execution.ExecutableInject;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.PermissionService;
import io.openaev.service.StreamService;
import io.openaev.service.UserService;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.service.stream.StreamClearanceService;
import io.openaev.service.stream.StreamVisibility;
import io.openaev.service.stream.StreamVisibilityService;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import reactor.core.Disposable;

/**
 * The comcheck job builds one email inject per running comcheck, with the built-in email contract
 * and injector of the comcheck's own tenant. Every onboarded tenant holds its own copy of that
 * contract under the same id.
 *
 * <p>The job runs on a bare thread, as a Quartz worker does: no transaction, no session, no ambient
 * tenant. Not {@code @Transactional}: rows are committed through JDBC in a tenant of their own and
 * removed after each test; the second tenant's contract and injector go with the tenant.
 */
@WithMockUser(isAdmin = true)
@DisplayName("The comcheck job builds each email with its own tenant's email contract")
class ComchecksExecutionJobTest extends IntegrationTest {

  @Autowired private ComchecksExecutionJob comchecksExecutionJob;
  @Autowired private TenantScopedJobRunner tenantScopedJobRunner;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private DataSource dataSource;
  @Autowired private ConfigurableApplicationContext applicationContext;
  @Autowired private PermissionService permissionService;
  @Autowired private UserService userService;
  @Autowired private AttackPathAccessControl attackPathAccessControl;
  @Autowired private ObjectMapper mapper;

  @MockitoSpyBean private ManagerFactory managerFactory;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private final List<String> seededExercises = new ArrayList<>();
  private final List<String> seededUsers = new ArrayList<>();
  private final List<String> seededComchecks = new ArrayList<>();
  private final List<BaseEvent> publishedEvents = new CopyOnWriteArrayList<>();
  private final ApplicationListener<PayloadApplicationEvent<?>> eventRecorder =
      event -> {
        if (event.getPayload() instanceof BaseEvent baseEvent) {
          publishedEvents.add(baseEvent);
        }
      };

  @BeforeEach
  void openJdbc() {
    jdbc = new JdbcTemplate(dataSource);
    applicationContext.addApplicationListener(eventRecorder);
  }

  @AfterEach
  void sweep() {
    applicationContext
        .getBean(
            AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
            ApplicationEventMulticaster.class)
        .removeApplicationListener(eventRecorder);
    publishedEvents.clear();
    Mockito.reset(managerFactory);
    for (String comcheckId : seededComchecks) {
      jdbc.update("DELETE FROM comchecks_statuses WHERE status_comcheck = ?", comcheckId);
      jdbc.update("DELETE FROM comchecks WHERE comcheck_id = ?", comcheckId);
    }
    for (String exerciseId : seededExercises) {
      jdbc.update("DELETE FROM exercises WHERE exercise_id = ?", exerciseId);
    }
    for (String userId : seededUsers) {
      jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededComchecks.clear();
    seededExercises.clear();
    seededUsers.clear();
    seededTenants.clear();
  }

  // -- SEEDING --

  private String seedTenant() {
    String tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "comcheck-job-" + tenantId);
    seededTenants.add(tenantId);
    return tenantId;
  }

  /** Registers the built-in email injector and its contracts in the tenant, as onboarding does. */
  private void provisionEmailInjector(String tenantId) {
    tenantScopedJobRunner.runInTenant(
        tenantId,
        () -> {
          try {
            emailInjectorIntegrationFactory.registerConnectorForTenant(tenantId);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
  }

  private String seedRunningExercise(String tenantId) {
    String exerciseId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status,"
            + " exercise_start_date, tenant_id) VALUES (?, ?, 'RUNNING',"
            + " now() - interval '1 hour', ?)",
        exerciseId,
        "comcheck simulation " + exerciseId,
        tenantId);
    seededExercises.add(exerciseId);
    return exerciseId;
  }

  private String seedUser() {
    String userId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        userId,
        "comcheck-" + userId + "@openaev.io");
    seededUsers.add(userId);
    return userId;
  }

  /** A comcheck and its one status, sent to {@code userId}. */
  private record SeededComcheck(String comcheckId, String statusId, String userId) {}

  /** A running comcheck with one status that has never been sent. */
  private SeededComcheck seedRunningComcheck(String exerciseId) {
    return seedComcheck(exerciseId, "now() + interval '1 day'");
  }

  /** A running comcheck whose end date is past, with one status that has never been sent. */
  private SeededComcheck seedOutdatedComcheck(String exerciseId) {
    return seedComcheck(exerciseId, "now() - interval '1 minute'");
  }

  private SeededComcheck seedComcheck(String exerciseId, String endDate) {
    String comcheckId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO comchecks (comcheck_id, comcheck_exercise, comcheck_start_date,"
            + " comcheck_end_date, comcheck_state, comcheck_name, comcheck_subject,"
            + " comcheck_message) VALUES (?, ?, now() - interval '1 hour', "
            + endDate
            + ", 'RUNNING', 'comcheck', 'subject', 'message')",
        comcheckId,
        exerciseId);
    seededComchecks.add(comcheckId);
    String statusId = UUID.randomUUID().toString();
    String userId = seedUser();
    jdbc.update(
        "INSERT INTO comchecks_statuses (status_id, status_user, status_comcheck,"
            + " status_sent_retry) VALUES (?, ?, ?, 0)",
        statusId,
        userId,
        comcheckId);
    return new SeededComcheck(comcheckId, statusId, userId);
  }

  /** Replaces the email executor so the built injects are captured instead of sent. */
  private io.openaev.executors.Injector captureEmailExecutor() {
    io.openaev.executors.Injector emailExecutor = mock(io.openaev.executors.Injector.class);
    when(emailExecutor.executeInjection(any())).thenReturn(new Execution(false));
    Manager manager = mock(Manager.class);
    when(manager.requestEmailInjector()).thenReturn(emailExecutor);
    doReturn(manager).when(managerFactory).getManager(anyString());
    return emailExecutor;
  }

  /** Replaces the email executor with one that reports the email delivered to {@code userId}. */
  private void emailExecutorDeliveringTo(String userId) {
    Execution execution = new Execution(false);
    execution.addTrace(
        ExecutionTrace.getNewSuccessTrace(
            "delivered", ExecutionTraceAction.COMPLETE, List.of(userId)));
    io.openaev.executors.Injector emailExecutor = mock(io.openaev.executors.Injector.class);
    when(emailExecutor.executeInjection(any())).thenReturn(execution);
    Manager manager = mock(Manager.class);
    when(manager.requestEmailInjector()).thenReturn(emailExecutor);
    doReturn(manager).when(managerFactory).getManager(anyString());
  }

  /** Runs the job the way a Quartz worker does: a fresh thread, no transaction, no tenant. */
  private void executeOnBareThread() throws InterruptedException {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                comchecksExecutionJob.execute(null);
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the comcheck job failed", failure.get());
    }
  }

  /** The entity events the job published about the row {@code rowId}. */
  private List<BaseEvent> eventsAbout(String rowId) {
    return publishedEvents.stream()
        .filter(event -> rowId.equals(event.getInstance().getId()))
        .toList();
  }

  /**
   * Delivers {@code events} through a stream with an admin consumer in {@code tenantId} and one in
   * the default tenant, and returns the ids each received, in that order.
   */
  @SuppressWarnings("unchecked")
  private List<List<String>> deliverToConsumersOf(String tenantId, List<BaseEvent> events) {
    StreamVisibilityService markings = mock(StreamVisibilityService.class);
    when(markings.visibilityOrUnresolved(any(), any())).thenReturn(StreamVisibility.UNFILTERED);
    StreamService stream =
        new StreamService(
            permissionService,
            userService,
            attackPathAccessControl,
            mock(StreamClearanceService.class),
            markings,
            mapper);
    List<String> receivedInTenant = new CopyOnWriteArrayList<>();
    List<String> receivedInDefault = new CopyOnWriteArrayList<>();
    Disposable consumerOfTenant =
        stream
            .makeDataFlux("comcheck-" + UUID.randomUUID(), tenantId)
            .subscribe(sent -> receivedInTenant.add(idOf((ServerSentEvent<Object>) sent)));
    Disposable consumerOfDefault =
        stream
            .makeDataFlux("comcheck-" + UUID.randomUUID(), Tenant.DEFAULT_TENANT_UUID)
            .subscribe(sent -> receivedInDefault.add(idOf((ServerSentEvent<Object>) sent)));
    try {
      events.forEach(stream::listenDatabaseUpdate);
    } finally {
      consumerOfTenant.dispose();
      consumerOfDefault.dispose();
    }
    return List.of(receivedInTenant, receivedInDefault);
  }

  private static String idOf(ServerSentEvent<Object> sent) {
    return sent.data() instanceof BaseEvent event ? event.getInstance().getId() : null;
  }

  private static Inject injectOf(List<ExecutableInject> sent, String exerciseId) {
    List<Inject> matching =
        sent.stream()
            .map(executable -> executable.getInjection().getInject())
            .filter(inject -> inject.getExercise().getId().equals(exerciseId))
            .toList();
    assertThat(matching).hasSize(1);
    return matching.getFirst();
  }

  // -- TESTS --

  @Nested
  @DisplayName("Email contract resolution")
  class EmailContractResolution {

    @Test
    @DisplayName(
        "given running comchecks in two tenants holding the email contract, should build each"
            + " email with its own tenant's contract")
    void given_runningComchecksInTwoTenants_should_buildEachWithItsOwnTenantsContract()
        throws Exception {
      // -- ARRANGE --
      String otherTenant = seedTenant();
      provisionEmailInjector(Tenant.DEFAULT_TENANT_UUID);
      provisionEmailInjector(otherTenant);
      String defaultExercise = seedRunningExercise(Tenant.DEFAULT_TENANT_UUID);
      String otherExercise = seedRunningExercise(otherTenant);
      seedRunningComcheck(defaultExercise);
      seedRunningComcheck(otherExercise);
      io.openaev.executors.Injector emailExecutor = captureEmailExecutor();

      // -- ACT --
      executeOnBareThread();

      // -- ASSERT --
      ArgumentCaptor<ExecutableInject> captor = ArgumentCaptor.forClass(ExecutableInject.class);
      verify(emailExecutor, atLeast(2)).executeInjection(captor.capture());
      List<ExecutableInject> sent = captor.getAllValues();
      assertThat(injectOf(sent, defaultExercise).getInjectorContract().orElseThrow().getTenant())
          .extracting(Tenant::getId)
          .isEqualTo(Tenant.DEFAULT_TENANT_UUID);
      assertThat(injectOf(sent, otherExercise).getInjectorContract().orElseThrow().getTenant())
          .extracting(Tenant::getId)
          .isEqualTo(otherTenant);
    }
  }

  @Nested
  @DisplayName("Stream delivery of the rows the job writes")
  class StreamDelivery {

    @Test
    @DisplayName(
        "given an outdated comcheck of another tenant, should stream its expiry to the consumer of"
            + " that tenant and not to the one of the default tenant")
    void given_outdatedComcheckOfAnotherTenant_should_streamItsExpiryToThatTenantOnly()
        throws Exception {
      // -- ARRANGE --
      String otherTenant = seedTenant();
      String comcheckId = seedOutdatedComcheck(seedRunningExercise(otherTenant)).comcheckId();

      // -- ACT --
      executeOnBareThread();

      // -- ASSERT --
      assertThat(
              jdbc.queryForObject(
                  "SELECT comcheck_state FROM comchecks WHERE comcheck_id = ?",
                  String.class,
                  comcheckId))
          .isEqualTo("EXPIRED");
      List<BaseEvent> events = eventsAbout(comcheckId);
      assertThat(events).isNotEmpty().extracting(BaseEvent::getTenantId).containsOnly(otherTenant);
      List<List<String>> received = deliverToConsumersOf(otherTenant, events);
      assertThat(received.get(0)).contains(comcheckId);
      assertThat(received.get(1)).doesNotContain(comcheckId);
    }

    @Test
    @DisplayName(
        "given an outdated comcheck with no simulation, should expire it in the default tenant")
    void given_outdatedComcheckWithNoSimulation_should_expireItInTheDefaultTenant()
        throws Exception {
      // -- ARRANGE --
      String comcheckId = seedOutdatedComcheck(null).comcheckId();

      // -- ACT --
      executeOnBareThread();

      // -- ASSERT --
      assertThat(
              jdbc.queryForObject(
                  "SELECT comcheck_state FROM comchecks WHERE comcheck_id = ?",
                  String.class,
                  comcheckId))
          .isEqualTo("EXPIRED");
      assertThat(eventsAbout(comcheckId))
          .isNotEmpty()
          .extracting(BaseEvent::getTenantId)
          .containsOnly(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName(
        "given a comcheck email delivered in another tenant, should stream the sent date of its"
            + " status to the consumer of that tenant and not to the one of the default tenant")
    void given_emailDeliveredInAnotherTenant_should_streamTheSentStatusToThatTenantOnly()
        throws Exception {
      // -- ARRANGE --
      String otherTenant = seedTenant();
      provisionEmailInjector(otherTenant);
      SeededComcheck comcheck = seedRunningComcheck(seedRunningExercise(otherTenant));
      emailExecutorDeliveringTo(comcheck.userId());

      // -- ACT --
      executeOnBareThread();

      // -- ASSERT --
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM comchecks_statuses WHERE status_id = ?"
                      + " AND status_sent_date IS NOT NULL",
                  Integer.class,
                  comcheck.statusId()))
          .isEqualTo(1);
      List<BaseEvent> events = eventsAbout(comcheck.statusId());
      assertThat(events).isNotEmpty().extracting(BaseEvent::getTenantId).containsOnly(otherTenant);
      List<List<String>> received = deliverToConsumersOf(otherTenant, events);
      assertThat(received.get(0)).contains(comcheck.statusId());
      assertThat(received.get(1)).doesNotContain(comcheck.statusId());
    }
  }

  @Nested
  @DisplayName("Expiry of a comcheck that changed after the gather")
  class ExpiryAfterGather {

    private static final long LOCK_WAIT_TIMEOUT_MS = 10_000;

    private String stateOf(String comcheckId) {
      return jdbc.queryForObject(
          "SELECT comcheck_state FROM comchecks WHERE comcheck_id = ?", String.class, comcheckId);
    }

    /** Whether a session waits on a lock while running a statement on the comchecks. */
    private boolean aSessionWaitsOnAComcheckLock() {
      Integer waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                  + " AND query ILIKE '%comchecks%'",
              Integer.class);
      return waiting != null && waiting > 0;
    }

    @Test
    @DisplayName(
        "given an outdated comcheck finished after the gather, should leave it finished and"
            + " publish no event about it")
    void given_comcheckFinishedAfterTheGather_should_leaveItFinished() {
      // -- ARRANGE --
      String tenantId = seedTenant();
      String comcheckId = seedOutdatedComcheck(seedRunningExercise(tenantId)).comcheckId();
      jdbc.update(
          "UPDATE comchecks SET comcheck_state = 'FINISHED' WHERE comcheck_id = ?", comcheckId);

      // -- ACT --
      comchecksExecutionJob.expireInTenant(tenantId, List.of(comcheckId), Instant.now());

      // -- ASSERT --
      assertThat(stateOf(comcheckId)).isEqualTo("FINISHED");
      assertThat(eventsAbout(comcheckId)).isEmpty();
    }

    @Test
    @DisplayName(
        "given an outdated comcheck whose end date moved past now after the gather, should"
            + " leave it running")
    void given_endDateMovedAfterTheGather_should_leaveItRunning() {
      // -- ARRANGE --
      String tenantId = seedTenant();
      String comcheckId = seedOutdatedComcheck(seedRunningExercise(tenantId)).comcheckId();
      jdbc.update(
          "UPDATE comchecks SET comcheck_end_date = now() + interval '1 day'"
              + " WHERE comcheck_id = ?",
          comcheckId);

      // -- ACT --
      comchecksExecutionJob.expireInTenant(tenantId, List.of(comcheckId), Instant.now());

      // -- ASSERT --
      assertThat(stateOf(comcheckId)).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName(
        "given an outdated comcheck a player finishes in a transaction open while it expires,"
            + " should wait for that answer and leave it finished")
    void given_comcheckFinishedInAnOpenTransaction_should_waitAndLeaveItFinished()
        throws Exception {
      // -- ARRANGE --
      String tenantId = seedTenant();
      String comcheckId = seedOutdatedComcheck(seedRunningExercise(tenantId)).comcheckId();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread expiry;
      try (Connection answer = dataSource.getConnection()) {
        answer.setAutoCommit(false);
        try (PreparedStatement finish =
            answer.prepareStatement(
                "UPDATE comchecks SET comcheck_state = 'FINISHED' WHERE comcheck_id = ?")) {
          finish.setString(1, comcheckId);
          finish.executeUpdate();
        }

        // -- ACT --
        expiry =
            new Thread(
                () -> {
                  try {
                    comchecksExecutionJob.expireInTenant(
                        tenantId, List.of(comcheckId), Instant.now());
                  } catch (Throwable t) {
                    failure.set(t);
                  }
                });
        expiry.start();
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MS;
        while (!aSessionWaitsOnAComcheckLock() && System.currentTimeMillis() < deadline) {
          Thread.sleep(50);
        }
        assertThat(aSessionWaitsOnAComcheckLock()).isTrue();
        answer.commit();
      }
      expiry.join(LOCK_WAIT_TIMEOUT_MS);

      // -- ASSERT --
      assertThat(failure.get()).isNull();
      assertThat(expiry.isAlive()).isFalse();
      assertThat(stateOf(comcheckId)).isEqualTo("FINISHED");
    }
  }
}
