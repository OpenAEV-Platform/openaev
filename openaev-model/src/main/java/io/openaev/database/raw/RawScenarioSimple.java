package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for simplified scenario data.
 *
 * <p>This interface defines a lightweight projection for retrieving essential scenario information
 * without loading full entity relationships. Used for list views and summary displays.
 *
 * @see io.openaev.database.model.Scenario
 * @see RawScenario
 */
public interface RawScenarioSimple {

  /**
   * Returns the unique identifier of the scenario.
   *
   * @return the scenario ID
   */
  String getScenario_id();

  /**
   * Returns the display name of the scenario.
   *
   * @return the scenario name
   */
  String getScenario_name();

  /**
   * Returns the cron expression for scenario recurrence.
   *
   * @return the recurrence cron expression, or {@code null} if not recurring
   */
  String getScenario_recurrence();

  /**
   * Returns the creation timestamp of the scenario.
   *
   * @return the creation timestamp
   */
  Instant getScenario_created_at();

  /**
   * Returns the timestamp when injects were last modified.
   *
   * @return the injects update timestamp
   */
  Instant getScenario_injects_updated_at();

  /**
   * Returns the subtitle of the scenario.
   *
   * @return the scenario subtitle
   */
  String getScenario_subtitle();

  /**
   * Returns the set of tag IDs associated with this scenario.
   *
   * @return set of tag IDs
   */
  Set<String> getScenario_tags();

  /**
   * Returns the set of asset IDs targeted by this scenario.
   *
   * @return set of asset IDs
   */
  Set<String> getScenario_assets();

  /**
   * Returns the set of asset group IDs targeted by this scenario.
   *
   * @return set of asset group IDs
   */
  Set<String> getScenario_asset_groups();

  /**
   * Returns the set of team IDs participating in this scenario.
   *
   * @return set of team IDs
   */
  Set<String> getScenario_teams();

  /**
   * Returns the set of inject IDs in this scenario.
   *
   * @return set of inject IDs
   */
  Set<String> getScenario_injects();

  /**
   * Returns the set of platforms targeted by this scenario.
   *
   * @return set of platform types
   */
  Set<String> getScenario_platforms();
}
