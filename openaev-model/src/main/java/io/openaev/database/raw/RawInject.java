package io.openaev.database.raw;

import java.util.List;

/**
 * Spring Data projection interface for an inject's team linkage.
 *
 * <p>Declares only what the queries returning it actually project: the inject id and its team ids.
 * A projection getter has no backing column unless some query selects it under that alias, so an
 * interface wider than its queries is a trap rather than documentation.
 *
 * @see io.openaev.database.model.Inject
 */
public interface RawInject {

  /**
   * Returns the unique identifier of the inject.
   *
   * @return the inject ID
   */
  String getInject_id();

  /**
   * Returns the list of team IDs targeted by this inject.
   *
   * @return list of team IDs
   */
  List<String> getInject_teams();
}
