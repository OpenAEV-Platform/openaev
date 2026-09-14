package io.openaev.scheduler.jobs;

import static io.openaev.helper.StreamHelper.fromIterable;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScenarioExecutionJobTest extends IntegrationTest {

  @Autowired private ScenarioExecutionJob job;
  @Autowired private InjectsExecutionJob injectsExecutionJob;
  @Autowired private ScenarioComposer scenarioComposer;

  @Autowired private ScenarioService scenarioService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private WorkflowRepository workflowRepository;

  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private EntityManager entityManager;

  static String SCENARIO_ID_1;
  static String SCENARIO_ID_2;
  static String SCENARIO_ID_3;
  static String EXERCISE_ID;

  @AfterEach
  void after() {
    entityManager.unwrap(org.hibernate.Session.class).disableFilter("tenantFilter");
    entityManager.clear();
    workflowRepository.deleteAll();
    exerciseRepository.deleteAll();
    scenarioRepository.deleteAll();
  }

  @Nested
  @DisplayName("When using cron-based recurrence")
  class WhenUsingCronBasedRecurrence {
    @DisplayName("Not create simulation based on recurring scenario in one hour")
    @Test
    void given_cron_in_one_hour_should_not_create_simulation() throws JobExecutionException {
      // -- PREPARE --
      ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));
      int hourToStart = (zonedDateTime.getHour() + 1) % 24;

      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setRecurrence(
          "0 "
              + zonedDateTime.getMinute()
              + " "
              + hourToStart
              + " * * *"); // Every day now + 1 hour
      Scenario scenarioSaved = scenarioService.createScenario(scenario);
      SCENARIO_ID_1 = scenarioSaved.getId();

      // -- EXECUTE --
      job.execute(null);

      // -- ASSERT --
      List<Exercise> createdExercises =
          fromIterable(exerciseRepository.findAll()).stream()
              .filter(exercise -> exercise.getScenario() != null)
              .filter(exercise -> SCENARIO_ID_1.equals(exercise.getScenario().getId()))
              .toList();
      assertEquals(0, createdExercises.size());
    }

    @DisplayName("Create simulation based on recurring scenario now")
    @Test
    void given_cron_in_one_minute_should_create_simulation() throws JobExecutionException {
      // -- PREPARE --
      ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));

      Scenario scenario = ScenarioFixture.getScenario();
      int minuteToStart = (zonedDateTime.getMinute() + 1) % 60;
      int hourToStart = zonedDateTime.getHour() + ((zonedDateTime.getMinute() + 1) / 60);
      hourToStart = hourToStart % 24;

      scenario.setRecurrence(
          "0 " + minuteToStart + " " + hourToStart + " * * *"); // Every day now + 1 minute
      Scenario scenarioSaved = scenarioService.createScenario(scenario);
      SCENARIO_ID_2 = scenarioSaved.getId();

      // -- EXECUTE --
      job.execute(null);

      // -- ASSERT --
      List<Exercise> createdExercises =
          fromIterable(exerciseRepository.findAll()).stream()
              .filter(exercise -> exercise.getScenario() != null)
              .filter(exercise -> SCENARIO_ID_2.equals(exercise.getScenario().getId()))
              .toList();
      assertEquals(1, createdExercises.size());
      Exercise createdExercise = createdExercises.getFirst();
      assertNotNull(createdExercise.getStart());
      assertThat(workflowRepository.existsBySimulationId(createdExercise.getId())).isFalse();

      EXERCISE_ID = createdExercise.getId();
    }

    /**
     * Creates a chained recurring scenario due in one minute, runs {@link ScenarioExecutionJob},
     * and returns the resulting simulation. The simulation is expected to be SCHEDULED with only
     * its workflow TEMPLATE provisioned (no RUN yet).
     */
    private Exercise createDueChainedScheduledExercise() throws Exception {
      ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));
      int minuteToStart = (zonedDateTime.getMinute() + 1) % 60;
      int hourToStart = (zonedDateTime.getHour() + ((zonedDateTime.getMinute() + 1) / 60)) % 24;

      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setRecurrence(
          "0 " + minuteToStart + " " + hourToStart + " * * *"); // Every day now + 1 minute
      Scenario scenarioSaved = scenarioService.createScenario(scenario);
      scenarioSaved = scenarioService.createScenarioChaining(scenarioSaved);
      SCENARIO_ID_3 = scenarioSaved.getId();

      job.execute(null);

      List<Exercise> createdExercises =
          fromIterable(exerciseRepository.findAll()).stream()
              .filter(exercise -> exercise.getScenario() != null)
              .filter(exercise -> SCENARIO_ID_3.equals(exercise.getScenario().getId()))
              .toList();
      assertEquals(1, createdExercises.size());
      return createdExercises.getFirst();
    }

    @DisplayName("Create chained simulation template based on recurring scenario now")
    @Test
    void given_chained_cron_in_one_minute_should_create_template_and_delay_run() throws Exception {
      // -- PREPARE & EXECUTE --
      Exercise createdExercise = createDueChainedScheduledExercise();

      // -- ASSERT --
      assertNotNull(createdExercise.getStart());
      assertEquals(ExerciseStatus.SCHEDULED, createdExercise.getStatus());
      assertThat(workflowRepository.existsBySimulationId(createdExercise.getId())).isTrue();
      assertThat(
              workflowRepository.findAllBySimulation_IdAndStatus(
                  createdExercise.getId(), WorkflowStatus.RUN))
          .isEmpty();
      assertNotNull(
          exerciseRepository.rawDetailsById(createdExercise.getId()).getExercise_workflow_id());
    }

    @DisplayName("Create chained workflow run when scheduled chained simulation auto-starts")
    @Test
    void given_chained_cron_in_one_minute_should_create_run_when_auto_start_runs()
        throws Exception {
      // -- PREPARE --
      Exercise createdExercise = createDueChainedScheduledExercise();
      createdExercise.setStart(Instant.now().minusSeconds(60));
      exerciseRepository.save(createdExercise);

      // -- EXECUTE --
      // injectsExecutionJob.execute(...) both auto-starts due simulations and starts their
      // chained workflow run; calling autoStartDueExercises() alone would not start the workflow.
      injectsExecutionJob.execute(null);

      // -- ASSERT --
      // The fixture scenario has no step templates, so the workflow run created on auto-start
      // completes immediately (no step is ever ready) and the simulation is finished in the same
      // call. This still proves the RUN was created and started: without the auto-start wiring,
      // no RUN would exist at all and the simulation would remain SCHEDULED.
      Exercise startedExercise = exerciseRepository.findById(createdExercise.getId()).orElseThrow();
      assertThat(startedExercise.getStatus()).isEqualTo(ExerciseStatus.FINISHED);
      assertThat(
              workflowRepository.findAllBySimulation_IdAndStatus(
                  createdExercise.getId(), WorkflowStatus.END))
          .singleElement()
          .satisfies(workflow -> assertThat(workflow.getWorkflowTemplate()).isNotNull());
    }

    @DisplayName("Already created simulation based on recurring scenario")
    @Test
    void given_cron_in_one_minute_should_not_create_second_simulation()
        throws JobExecutionException {
      // -- PREPARE --
      ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));

      Scenario scenario = ScenarioFixture.getScenario();
      int minuteToStart = (zonedDateTime.getMinute() + 1) % 60;
      int hourToStart = zonedDateTime.getHour() + ((zonedDateTime.getMinute() + 1) / 60);
      hourToStart = hourToStart % 24;

      scenario.setRecurrence(
          "0 " + minuteToStart + " " + hourToStart + " * * *"); // Every day now + 1 minute
      Scenario scenarioSaved = scenarioService.createScenario(scenario);
      SCENARIO_ID_2 = scenarioSaved.getId();

      // -- EXECUTE --
      job.execute(null);

      // -- EXECUTE AGAIN --
      job.execute(null);

      // -- ASSERT --
      List<Exercise> createdExercises =
          fromIterable(exerciseRepository.findAll()).stream()
              .filter(exercise -> exercise.getScenario() != null)
              .filter(exercise -> SCENARIO_ID_2.equals(exercise.getScenario().getId()))
              .toList();
      assertEquals(1, createdExercises.size());
    }

    @DisplayName("Not create simulation based on end date before now")
    @Test
    void given_end_date_before_now_should_not_create_second_simulation()
        throws JobExecutionException {
      // -- PREPARE --
      ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));

      Scenario scenario = ScenarioFixture.getScenario();
      int minuteToStart = (zonedDateTime.getMinute() + 1) % 60;
      scenario.setRecurrence(
          "0 "
              + minuteToStart
              + " "
              + zonedDateTime.getHour()
              + " * * *"); // Every day now + 1 minute
      scenario.setRecurrenceEnd(Instant.now().minus(0, ChronoUnit.DAYS));
      Scenario scenarioSaved = scenarioService.createScenario(scenario);
      SCENARIO_ID_3 = scenarioSaved.getId();

      // -- EXECUTE --
      job.execute(null);

      // -- ASSERT --
      List<Exercise> createdExercises =
          fromIterable(exerciseRepository.findAll()).stream()
              .filter(exercise -> exercise.getScenario() != null)
              .filter(exercise -> SCENARIO_ID_3.equals(exercise.getScenario().getId()))
              .toList();
      assertEquals(0, createdExercises.size());
    }

    @Nested
    @DisplayName("With recurring scenarios in multiple tenants")
    @WithMockUser
    class WithRecurringScenariosInMultipleTenants {
      @Test
      @DisplayName("Simulations are created in correct tenant")
      void simulationAreCreatedInCorrectTenant()
          throws DependenciesManagerException, JobExecutionException {
        Tenant tenantA = tenantIsolationTestHelper.createTenant("tenantA");
        Tenant tenantB = tenantIsolationTestHelper.createTenant("tenantB");

        ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));
        int minuteToStart = (zonedDateTime.getMinute() + 1) % 60;
        int hourToStart = zonedDateTime.getHour() + ((zonedDateTime.getMinute() + 1) / 60);
        hourToStart = hourToStart % 24;

        // save in tenant A
        tenantIsolationTestHelper.switchToTenantNoFlush(tenantA.getId());
        Scenario scenarioTempA = ScenarioFixture.getScenario();
        scenarioTempA.setRecurrence(
            "0 " + minuteToStart + " " + hourToStart + " * * *"); // Every day now + 1 minute
        Scenario recurringScenarioInTenantA = scenarioService.createScenario(scenarioTempA);

        tenantIsolationTestHelper.switchToTenantNoFlush(tenantB.getId());
        Scenario scenarioTempB = ScenarioFixture.getScenario();
        scenarioTempB.setRecurrence(
            "0 " + minuteToStart + " " + hourToStart + " * * *"); // Every day now + 1 minute
        Scenario recurringScenarioInTenantB = scenarioService.createScenario(scenarioTempB);

        tenantIsolationTestHelper.switchToTenantNoFlush(Tenant.DEFAULT_TENANT_UUID);

        // ACT
        job.execute(null);

        // ASSERT
        for (Scenario scenario : List.of(recurringScenarioInTenantA, recurringScenarioInTenantB)) {
          tenantIsolationTestHelper.switchToTenantNoFlush(scenario.getTenant().getId());
          List<Exercise> createdExercises =
              fromIterable(exerciseRepository.findAll()).stream()
                  .filter(
                      exercise -> exercise.getTenant().getId().equals(scenario.getTenant().getId()))
                  .toList();
          assertThat(createdExercises).isNotEmpty();
          assertThat(createdExercises)
              .allSatisfy(
                  exercise ->
                      assertThat(exercise.getScenario().getId()).isEqualTo(scenario.getId()));
        }
      }
    }
  }

  @Nested
  @DisplayName("When using ISO 8601 period-based recurrence")
  public class WhenUsingISO8601PeriodBasedRecurrence {
    private final Instant scenarioStartTime = Instant.parse("2022-05-27T10:43:22Z");

    private Scenario getScenario() {
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setRecurrenceStart(scenarioStartTime);
      scenario.setRecurrence("P1D");
      return scenario;
    }

    private void setMockedInstant(MockedStatic<Instant> mockedInstant, Instant target) {
      mockedInstant.when(Instant::now).thenReturn(target);
    }

    @Test
    @DisplayName("When next occurrence is set in the future, do not create simulation")
    void whenNextOccurrenceIsSetInTheFuture_doNotCreateSimulation() throws JobExecutionException {
      try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
        Scenario scenario = getScenario();

        setMockedInstant(mockedInstant, scenarioStartTime.plus(6, ChronoUnit.HOURS));

        ScenarioComposer.Composer scenarioWrapper =
            scenarioComposer.forScenario(scenario).persist();

        // -- EXECUTE --
        job.execute(null);

        // -- ASSERT --
        List<Exercise> createdExercises =
            fromIterable(exerciseRepository.findAll()).stream()
                .filter(exercise -> exercise.getScenario() != null)
                .filter(
                    exercise ->
                        scenarioWrapper.get().getId().equals(exercise.getScenario().getId()))
                .toList();
        assertThat(createdExercises).isEmpty();
      }
    }

    @Test
    @DisplayName("When next occurrence is due now, do create simulation")
    void whenNextOccurrenceIsDueNow_doCreateSimulation() throws JobExecutionException {
      try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
        Scenario scenario = getScenario();

        setMockedInstant(
            mockedInstant, scenarioStartTime.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES));

        ScenarioComposer.Composer scenarioWrapper =
            scenarioComposer.forScenario(scenario).persist();

        // -- EXECUTE --
        job.execute(null);

        // -- ASSERT --
        List<Exercise> createdExercises =
            fromIterable(exerciseRepository.findAll()).stream()
                .filter(exercise -> exercise.getScenario() != null)
                .filter(
                    exercise ->
                        scenarioWrapper.get().getId().equals(exercise.getScenario().getId()))
                .toList();
        assertThat(createdExercises)
            .singleElement()
            .satisfies(
                exercise ->
                    assertThat(exercise.getScenario().getId())
                        .isEqualTo(scenarioWrapper.get().getId()));
      }
    }

    @Test
    @DisplayName("When due simulation is already created, do not create it twice")
    void whenDueSimulationIsAlreadyCreated_doNotCreateItTwice() throws JobExecutionException {
      try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
        Scenario scenario = getScenario();

        setMockedInstant(
            mockedInstant, scenarioStartTime.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES));

        ScenarioComposer.Composer scenarioWrapper =
            scenarioComposer.forScenario(scenario).persist();

        // -- EXECUTE --
        job.execute(null);
        // twice
        job.execute(null);

        // -- ASSERT --
        List<Exercise> createdExercises =
            fromIterable(exerciseRepository.findAll()).stream()
                .filter(exercise -> exercise.getScenario() != null)
                .filter(
                    exercise ->
                        scenarioWrapper.get().getId().equals(exercise.getScenario().getId()))
                .toList();
        assertThat(createdExercises)
            .singleElement()
            .satisfies(
                exercise ->
                    assertThat(exercise.getScenario().getId())
                        .isEqualTo(scenarioWrapper.get().getId()));
      }
    }

    @Test
    @DisplayName("When recurrence end date is past, do not create simulation")
    void whenRecurrenceEndDateIsPast_doNotCreateSimulation() throws JobExecutionException {
      try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
        Scenario scenario = getScenario();
        scenario.setRecurrenceEnd(scenarioStartTime.plus(10, ChronoUnit.DAYS));

        setMockedInstant(
            mockedInstant,
            scenarioStartTime.plus(11, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES));

        ScenarioComposer.Composer scenarioWrapper =
            scenarioComposer.forScenario(scenario).persist();

        // -- EXECUTE --
        job.execute(null);

        // -- ASSERT --
        List<Exercise> createdExercises =
            fromIterable(exerciseRepository.findAll()).stream()
                .filter(exercise -> exercise.getScenario() != null)
                .filter(
                    exercise ->
                        scenarioWrapper.get().getId().equals(exercise.getScenario().getId()))
                .toList();
        assertThat(createdExercises).isEmpty();
      }
    }
  }

  @Test
  @DisplayName("When recurrence expression cannot be handled, do not create simulation")
  void whenRecurrenceExpressionCannotBeHandled_doNotCreateSimulation()
      throws JobExecutionException {
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setRecurrence("can not handle this expression!");
    ScenarioComposer.Composer scenarioWrapper = scenarioComposer.forScenario(scenario).persist();

    // -- EXECUTE --
    job.execute(null);

    // -- ASSERT --
    List<Exercise> createdExercises =
        fromIterable(exerciseRepository.findAll()).stream()
            .filter(exercise -> exercise.getScenario() != null)
            .filter(
                exercise -> scenarioWrapper.get().getId().equals(exercise.getScenario().getId()))
            .toList();
    assertThat(createdExercises).isEmpty();
  }
}
