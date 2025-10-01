package io.openaev.utils.fixtures;

import static io.openaev.database.model.Scenario.SEVERITY.critical;

import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Team;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScenarioFixture {

  public static Scenario getScenario() {
    return getScenario(null, null);
  }

  public static Scenario getScenarioWithRecurrence(String cronExpression) {
    Scenario scenario = getScenario(null, null);
    scenario.setRecurrence(cronExpression);
    return scenario;
  }

  public static Scenario getScheduledScenario() {
    Scenario scenario = getScenario(null, null);
    scenario.setRecurrenceStart(Instant.now().plus(1, ChronoUnit.DAYS));
    return scenario;
  }

  public static Scenario getScenario(List<Team> scenarioTeams, Set<Inject> scenarioInjects) {
    Scenario scenario = new Scenario();
    scenario.setName("Crisis simulation");
    scenario.setDescription("A crisis simulation for my enterprise");
    scenario.setSubtitle("A crisis simulation");
    scenario.setFrom("simulation@mail.fr");
    if (scenarioTeams != null) {
      scenario.setTeams(scenarioTeams);
    }
    if (scenarioInjects != null) {
      scenario.setInjects(scenarioInjects);
    }
    scenario.setExercises(new ArrayList<>());
    return scenario;
  }

  public static Scenario createDefaultCrisisScenario() {
    Scenario scenario = new Scenario();
    scenario.setName("Crisis scenario");
    scenario.setDescription("A crisis scenario for my enterprise");
    scenario.setSubtitle("A crisis scenario");
    scenario.setFrom("scenario@mail.fr");
    scenario.setCategory("crisis-communication");
    scenario.setExercises(new ArrayList<>());
    return scenario;
  }

  public static Scenario createDefaultIncidentResponseScenario() {
    Scenario scenario = new Scenario();
    scenario.setName("Incident response scenario");
    scenario.setDescription("An incident response scenario for my enterprise");
    scenario.setSubtitle("An incident response scenario");
    scenario.setFrom("scenario@mail.fr");
    scenario.setCategory("incident-response");
    scenario.setSeverity(critical);
    scenario.setExercises(new ArrayList<>());
    return scenario;
  }
}
