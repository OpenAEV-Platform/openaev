package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawFinishedExerciseWithInjects {
  Instant getExercise_end_date();

  Set<String> getInject_ids();
}
