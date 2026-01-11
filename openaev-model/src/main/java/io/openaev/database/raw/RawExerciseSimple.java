package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for simplified exercise data.
 *
 * <p>This interface defines a lightweight projection for retrieving essential exercise information
 * without loading full entity relationships. Used for list views and summary displays.
 *
 * @see io.openaev.database.model.Exercise
 * @see RawSimulation
 */
public interface RawExerciseSimple {

  /**
   * Returns the unique identifier of the exercise.
   *
   * @return the exercise ID
   */
  String getExercise_id();

  /**
   * Returns the current status of the exercise.
   *
   * @return the status (e.g., "SCHEDULED", "RUNNING", "FINISHED")
   */
  String getExercise_status();

  /**
   * Returns the scheduled start date of the exercise.
   *
   * @return the start date
   */
  Instant getExercise_start_date();

  /**
   * Returns the creation timestamp of the exercise.
   *
   * @return the creation timestamp
   */
  Instant getExercise_created_at();

  /**
   * Returns the last update timestamp of the exercise.
   *
   * @return the update timestamp
   */
  Instant getExercise_updated_at();

  /**
   * Returns the end date of the exercise.
   *
   * @return the end date, or {@code null} if not yet finished
   */
  Instant getExercise_end_date();

  /**
   * Returns the display name of the exercise.
   *
   * @return the exercise name
   */
  String getExercise_name();

  /**
   * Returns the category of the exercise.
   *
   * @return the category name
   */
  String getExercise_category();

  /**
   * Returns the subtitle of the exercise.
   *
   * @return the exercise subtitle
   */
  String getExercise_subtitle();

  /**
   * Returns the set of tag IDs associated with this exercise.
   *
   * @return set of tag IDs
   */
  Set<String> getExercise_tags();

  /**
   * Returns the set of inject IDs in this exercise.
   *
   * @return set of inject IDs
   */
  Set<String> getInject_ids();
}
