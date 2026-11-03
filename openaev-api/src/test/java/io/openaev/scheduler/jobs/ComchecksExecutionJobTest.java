package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Execution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.execution.ExecutableInject;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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

  @MockitoSpyBean private ManagerFactory managerFactory;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private final List<String> seededExercises = new ArrayList<>();
  private final List<String> seededUsers = new ArrayList<>();
  private final List<String> seededComchecks = new ArrayList<>();

  @BeforeEach
  void openJdbc() {
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void sweep() {
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
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_status,"
            + " exercise_start_date, tenant_id) VALUES (?, ?, 'sim@openaev.io', 'RUNNING',"
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

  /** A running comcheck with one status that has never been sent. */
  private void seedRunningComcheck(String exerciseId) {
    String comcheckId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO comchecks (comcheck_id, comcheck_exercise, comcheck_start_date,"
            + " comcheck_end_date, comcheck_state, comcheck_name, comcheck_subject,"
            + " comcheck_message) VALUES (?, ?, now() - interval '1 hour',"
            + " now() + interval '1 day', 'RUNNING', 'comcheck', 'subject', 'message')",
        comcheckId,
        exerciseId);
    seededComchecks.add(comcheckId);
    jdbc.update(
        "INSERT INTO comchecks_statuses (status_id, status_user, status_comcheck,"
            + " status_sent_retry) VALUES (?, ?, ?, 0)",
        UUID.randomUUID().toString(),
        seedUser(),
        comcheckId);
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
}
