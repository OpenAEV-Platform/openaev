package io.openaev.database.raw;

import java.time.Instant;

/**
 * Spring Data projection interface for exercise pause data.
 *
 * <p>This interface defines a projection for retrieving pause events during exercise execution.
 * Pauses represent interruptions in exercise flow, typically initiated by facilitators.
 *
 * @see io.openaev.database.model.Pause
 */
public interface RawPause {

  /**
   * Returns the unique identifier of the pause event.
   *
   * @return the pause ID
   */
  String getPause_id();

  /**
   * Returns the ID of the exercise that was paused.
   *
   * @return the exercise ID
   */
  String getPause_exercise();

  /**
   * Returns the timestamp when the pause was initiated.
   *
   * @return the pause start timestamp
   */
  Instant getPause_date();

  /**
   * Returns the duration of the pause in milliseconds.
   *
   * @return the pause duration in milliseconds
   */
  long getPause_duration();
}
