package io.openaev.utils.fixtures;

import static java.time.temporal.ChronoUnit.MINUTES;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Team;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ExerciseFixture {

  public static final String EXERCISE_NAME = "Exercise test";

  public static Exercise getExercise() {
    return getExercise(null);
  }

  public static Exercise getExercise(List<Team> exerciseTeams) {
    Exercise exercise = new Exercise();
    exercise.setName(EXERCISE_NAME);
    if (exerciseTeams != null) {
      exercise.setTeams(exerciseTeams);
    }
    return exercise;
  }

  public static Exercise createDefaultExercise() {
    Exercise exercise = createDefaultExerciseWithDefaultName();
    exercise.setDescription("A default test exercise");
    exercise.setSubtitle("Default test exercise");
    exercise.setFrom("default_exercise@mail.fr");
    exercise.setCategory("crisis-communication");
    return exercise;
  }

  public static Exercise createDefaultCrisisExercise() {
    Exercise exercise = createDefaultExerciseWithName("Crisis exercise");
    exercise.setDescription("A crisis exercise for my enterprise");
    exercise.setSubtitle("A crisis exercise");
    exercise.setFrom("exercise@mail.fr");
    exercise.setCategory("crisis-communication");
    return exercise;
  }

  public static Exercise createDefaultIncidentResponseExercise() {
    return createDefaultIncidentResponseExercise(Instant.now());
  }

  public static Exercise createDefaultIncidentResponseExercise(Instant startTime) {
    Exercise exercise = createDefaultExerciseWithName("Incident response exercise");
    exercise.setDescription("An incident response exercise for my enterprise");
    exercise.setSubtitle("An incident response exercise");
    exercise.setFrom("exercise@mail.fr");
    exercise.setCategory("incident-response");
    exercise.setStatus(ExerciseStatus.SCHEDULED);
    exercise.setStart(startTime);
    return exercise;
  }

  public static Exercise createDefaultAttackExercise(Instant startTime) {
    Exercise exercise = createDefaultExerciseWithName("Draft incident response exercise");
    exercise.setDescription("An incident response exercise for my enterprise");
    exercise.setSubtitle("An incident response exercise");
    exercise.setFrom("exercise@mail.fr");
    exercise.setCategory("attack-scenario");
    exercise.setMainFocus("incident-response");
    exercise.setStatus(ExerciseStatus.SCHEDULED);
    exercise.setStart(startTime);
    return exercise;
  }

  public static Exercise createRunningAttackExercise() {
    return createRunningAttackExercise(Instant.now());
  }

  public static Exercise createRunningAttackExercise(Instant startTime) {
    Exercise exercise = createDefaultExerciseWithName("Draft incident response exercise");
    exercise.setDescription("An incident response exercise for my enterprise");
    exercise.setSubtitle("An incident response exercise");
    exercise.setFrom("exercise@mail.fr");
    exercise.setCategory("attack-scenario");
    exercise.setMainFocus("incident-response");
    exercise.setStatus(ExerciseStatus.RUNNING);
    exercise.setStart(startTime);
    return exercise;
  }

  public static Exercise createCanceledAttackExercise(Instant startTime) {
    Exercise exercise = createDefaultExerciseWithName("Draft incident response exercise");
    exercise.setDescription("An incident response exercise for my enterprise");
    exercise.setSubtitle("An incident response exercise");
    exercise.setFrom("exercise@mail.fr");
    exercise.setCategory("attack-scenario");
    exercise.setMainFocus("incident-response");
    exercise.setStatus(ExerciseStatus.CANCELED);
    exercise.setStart(startTime);
    return exercise;
  }

  public static Exercise createFinishedAttackExercise() {
    return createFinishedAttackExercise(Instant.now());
  }

  public static Exercise createFinishedAttackExercise(Instant startTime) {
    Exercise exercise = createDefaultExerciseWithName("Draft incident response exercise");
    exercise.setDescription("An incident response exercise for my enterprise");
    exercise.setSubtitle("An incident response exercise");
    exercise.setFrom("exercise@mail.fr");
    exercise.setCategory("attack-scenario");
    exercise.setMainFocus("incident-response");
    exercise.setStatus(ExerciseStatus.FINISHED);
    exercise.setStart(startTime);
    return exercise;
  }

  public static Exercise createPausedAttackExercise(Instant startTime) {
    Exercise exercise = createDefaultExerciseWithName("Draft incident response exercise");
    exercise.setCurrentPause(startTime.truncatedTo(MINUTES).minus(1, MINUTES));
    exercise.setDescription("An incident response exercise for my enterprise");
    exercise.setSubtitle("An incident response exercise");
    exercise.setFrom("exercise@mail.fr");
    exercise.setCategory("attack-scenario");
    exercise.setMainFocus("incident-response");
    exercise.setStatus(ExerciseStatus.PAUSED);
    exercise.setStart(startTime);
    return exercise;
  }

  private static Exercise createDefaultExerciseWithDefaultName() {
    return createDefaultExerciseWithName(null);
  }

  private static Exercise createDefaultExerciseWithName(String name) {
    String new_name = name == null ? "exercise-%s".formatted(UUID.randomUUID()) : name;
    Exercise exercise = new Exercise();
    exercise.setName(new_name);
    return exercise;
  }
}
