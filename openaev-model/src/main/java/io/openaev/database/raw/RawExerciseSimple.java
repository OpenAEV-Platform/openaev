package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawExerciseSimple {

  String getExercise_id();

  String getExercise_status();

  Instant getExercise_start_date();

  Instant getExercise_created_at();

  Instant getExercise_updated_at();

  Instant getExercise_end_date();

  String getExercise_name();

  String getExercise_category();

  String getExercise_subtitle();

  Set<String> getExercise_tags();

  Set<String> getInject_ids();
}
