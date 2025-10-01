package io.openaev.utils.fixtures;

import io.openaev.database.model.Objective;
import java.util.UUID;

public class ObjectiveFixture {

  public static final String OBJECTIVE_NAME = "My Objective";

  public static Objective getDefaultObjective() {
    return createObjectiveWithDefaultTitle();
  }

  public static Objective getObjective() {
    return createObjectiveWithTitle(OBJECTIVE_NAME);
  }

  private static Objective createObjectiveWithDefaultTitle() {
    return createObjectiveWithTitle(null);
  }

  private static Objective createObjectiveWithTitle(String title) {
    String new_title = title == null ? "objective-%s".formatted(UUID.randomUUID()) : title;
    Objective objective = new Objective();
    objective.setTitle(new_title);
    return objective;
  }
}
