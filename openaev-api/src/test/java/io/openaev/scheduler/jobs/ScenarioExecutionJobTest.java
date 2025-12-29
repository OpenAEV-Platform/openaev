package io.openaev.scheduler.jobs;

import static io.openaev.helper.StreamHelper.fromIterable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.fixtures.ScenarioFixture;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.*;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScenarioExecutionJobTest extends IntegrationTest {

  @Autowired private ScenarioExecutionJob job;

  @Autowired private ScenarioService scenarioService;
  @Autowired private ExerciseRepository exerciseRepository;

  static String SCENARIO_ID_1;
  static String SCENARIO_ID_2;
  static String SCENARIO_ID_3;
  static String EXERCISE_ID;

  @DisplayName("Not create simulation based on recurring scenario in one hour")
  @Test
  void given_cron_in_one_hour_should_not_create_simulation() throws JobExecutionException {
    // -- PREPARE --
    ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));
    int hourToStart = (zonedDateTime.getHour() + 1) % 24;

    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setRecurrence(
        "0 " + zonedDateTime.getMinute() + " " + hourToStart + " * * *"); // Every day now + 1 hour
    Scenario scenarioSaved = this.scenarioService.createScenario(scenario);
    SCENARIO_ID_1 = scenarioSaved.getId();

    // -- EXECUTE --
    this.job.execute(null);

    // -- ASSERT --
    List<Exercise> createdExercises =
        fromIterable(this.exerciseRepository.findAll()).stream()
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
    Scenario scenarioSaved = this.scenarioService.createScenario(scenario);
    SCENARIO_ID_2 = scenarioSaved.getId();

    // -- EXECUTE --
    this.job.execute(null);

    // -- ASSERT --
    List<Exercise> createdExercises =
        fromIterable(this.exerciseRepository.findAll()).stream()
            .filter(exercise -> exercise.getScenario() != null)
            .filter(exercise -> SCENARIO_ID_2.equals(exercise.getScenario().getId()))
            .toList();
    assertEquals(1, createdExercises.size());
    Exercise createdExercise = createdExercises.getFirst();
    assertNotNull(createdExercise.getStart());

    EXERCISE_ID = createdExercise.getId();
  }

  @DisplayName("Already created simulation based on recurring scenario")
  @Test
  void given_cron_in_one_minute_should_not_create_second_simulation() throws JobExecutionException {
    // -- PREPARE --
    ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));

    Scenario scenario = ScenarioFixture.getScenario();
    int minuteToStart = (zonedDateTime.getMinute() + 1) % 60;
    int hourToStart = zonedDateTime.getHour() + ((zonedDateTime.getMinute() + 1) / 60);
    hourToStart = hourToStart % 24;

    scenario.setRecurrence(
        "0 " + minuteToStart + " " + hourToStart + " * * *"); // Every day now + 1 minute
    Scenario scenarioSaved = this.scenarioService.createScenario(scenario);
    SCENARIO_ID_2 = scenarioSaved.getId();

    // -- EXECUTE --
    this.job.execute(null);

    // -- EXECUTE AGAIN --
    this.job.execute(null);

    // -- ASSERT --
    List<Exercise> createdExercises =
        fromIterable(this.exerciseRepository.findAll()).stream()
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
    Scenario scenarioSaved = this.scenarioService.createScenario(scenario);
    SCENARIO_ID_3 = scenarioSaved.getId();

    // -- EXECUTE --
    this.job.execute(null);

    // -- ASSERT --
    List<Exercise> createdExercises =
        fromIterable(this.exerciseRepository.findAll()).stream()
            .filter(exercise -> exercise.getScenario() != null)
            .filter(exercise -> SCENARIO_ID_3.equals(exercise.getScenario().getId()))
            .toList();
    assertEquals(0, createdExercises.size());
  }
}
