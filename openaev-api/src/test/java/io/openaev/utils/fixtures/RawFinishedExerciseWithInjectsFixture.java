package io.openaev.utils.fixtures;

import io.openaev.database.raw.RawFinishedExerciseWithInjects;
import java.time.Instant;
import java.util.Set;

public class RawFinishedExerciseWithInjectsFixture {

  private record TestableRawFinishedExerciseWithInjects(Instant endDate, Set<String> injectIds)
      implements RawFinishedExerciseWithInjects {

    @Override
    public Instant getExercise_end_date() {
      return endDate;
    }

    @Override
    public Set<String> getInject_ids() {
      return injectIds;
    }
  }

  public static RawFinishedExerciseWithInjects createDefaultRawFinishedExerciseWithInjects(
      Instant endDate, Set<String> injectIds) {
    return new TestableRawFinishedExerciseWithInjects(endDate, injectIds);
  }
}
