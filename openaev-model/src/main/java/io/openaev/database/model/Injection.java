package io.openaev.database.model;

import java.time.Instant;
import java.util.Optional;

public interface Injection {
  String getId();

  Exercise getExercise();

  Scenario getScenario();

  Optional<Instant> getDate();

  Inject getInject();
}
