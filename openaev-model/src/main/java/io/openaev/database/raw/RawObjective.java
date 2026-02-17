package io.openaev.database.raw;

/**
 * Spring Data projection interface for objective data.
 *
 * <p>This interface defines a lightweight projection for retrieving objective identifiers and their
 * exercise associations. Objectives represent goals to be achieved during exercise execution.
 *
 * @see io.openaev.database.model.Objective
 */
public interface RawObjective {

  /**
   * Returns the unique identifier of the objective.
   *
   * @return the objective ID
   */
  String getObjective_id();

  /**
   * Returns the ID of the exercise this objective belongs to.
   *
   * @return the exercise ID
   */
  String getObjective_exercise();
}
