package io.openaev.utils.fixtures;

import static java.time.Instant.now;

import io.openaev.database.model.Comcheck;
import io.openaev.database.model.Exercise;
import java.time.temporal.ChronoUnit;

public class ComcheckFixture {

  public static Comcheck createComcheck(Exercise exercise) {
    Comcheck comcheck = new Comcheck();
    comcheck.setExercise(exercise);
    comcheck.setName("Comcheck");
    comcheck.setSubject("Subject");
    comcheck.setMessage("Message");
    comcheck.setStart(now());
    comcheck.setEnd(now().plus(1, ChronoUnit.DAYS));
    return comcheck;
  }
}
