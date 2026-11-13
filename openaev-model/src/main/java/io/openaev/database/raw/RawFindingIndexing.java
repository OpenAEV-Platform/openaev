package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for finding data.
 *
 * <p>This interface defines a projection for retrieving findings discovered during inject
 * execution. Findings represent artifacts, indicators, or other data collected from target systems
 * during attack simulations.
 *
 * @see io.openaev.database.model.Finding
 */
public interface RawFindingIndexing extends RawTenant {

  /**
   * Returns the unique identifier of the finding.
   *
   * @return the finding ID
   */
  String getFinding_id();

  /**
   * Returns the value of the finding.
   *
   * @return the finding value (e.g., file hash, IP address, process name)
   */
  String getFinding_value();

  /**
   * Returns the value as the platform displays it, masked when the finding type holds secret
   * material. This is what the search index stores.
   *
   * @return the displayed value, or {@code null} on a row written before the column was filled
   */
  String getFinding_value_display();

  /**
   * Returns the field names under which the finding's occurrences were reported.
   *
   * @return the field names
   */
  Set<String> getFinding_fields();

  /**
   * Returns the creation timestamp of the finding.
   *
   * @return the creation timestamp
   */
  Instant getFinding_created_at();

  /**
   * Returns the last update timestamp of the finding.
   *
   * @return the update timestamp
   */
  Instant getFinding_updated_at();

  /**
   * Returns the type of finding.
   *
   * @return the finding type
   */
  String getFinding_type();

  /**
   * Returns the IDs of the injects where this finding occurred.
   *
   * @return the inject IDs
   */
  Set<String> getInject_ids();

  /**
   * Returns the IDs of the simulations where this finding occurred.
   *
   * @return the simulation IDs
   */
  Set<String> getSimulation_ids();

  /**
   * Returns the IDs of the scenarios where this finding occurred.
   *
   * @return the scenario IDs
   */
  Set<String> getScenario_ids();

  /** Returns the asset of the finding's location, which deletes the finding with it. */
  String getFinding_location_asset_id();

  /** Returns the user of the finding's location, which deletes the finding with it. */
  String getFinding_location_user_id();
}
