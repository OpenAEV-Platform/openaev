package io.openaev.utils.fixtures;

import io.openaev.database.model.Evaluation;
import io.openaev.database.model.Objective;
import io.openaev.database.model.User;

public class EvaluationFixture {

  public static Evaluation createEvaluation(Objective objective, User user) {
    Evaluation evaluation = new Evaluation();
    evaluation.setObjective(objective);
    evaluation.setUser(user);
    evaluation.setScore(3L);
    return evaluation;
  }
}
