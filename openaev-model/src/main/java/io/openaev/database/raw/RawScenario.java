package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for scenario data.
 *
 * <p>This interface defines a comprehensive projection for retrieving scenario information
 * including configuration, scheduling, messaging settings, and associated relationships such as
 * exercises, tags, and platforms.
 *
 * @see io.openaev.database.model.Scenario
 */
public interface RawScenario {

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
   * Returns the category classification of the scenario.
   *
   * @return the category name, or {@code null} if not categorized
   */
  String getScenario_category();

  /**
   * Returns the creation timestamp of the scenario.
   *
   * @return the creation timestamp
   */
  Instant getScenario_created_at();

  /**
   * Returns the last update timestamp of the scenario.
   *
   * @return the update timestamp
   */
  Instant getScenario_updated_at();

  /**
   * Returns the ID of the custom dashboard associated with this scenario.
   *
   * @return the custom dashboard ID, or {@code null} if using default dashboard
   */
  String getScenario_custom_dashboard();

  /**
   * Returns the detailed description of the scenario.
   *
   * @return the scenario description
   */
  String getScenario_description();

  /**
   * Returns the external URL reference for this scenario.
   *
   * @return the external URL, or {@code null} if not set
   */
  String getScenario_external_url();

  /**
   * Returns whether lessons learned responses should be anonymized.
   *
   * @return {@code true} if lessons are anonymized, {@code false} otherwise
   */
  boolean getScenario_lessons_anonymized();

  /**
   * Returns the email address used as the sender for scenario communications.
   *
   * @return the "from" email address
   */
  String getScenario_mail_from();

  /**
   * Returns the main focus area of the scenario.
   *
   * @return the main focus (e.g., "incident-response", "endpoint-protection")
   */
  String getScenario_main_focus();

  /**
   * Returns the footer text for scenario messages.
   *
   * @return the message footer
   */
  String getScenario_message_footer();

  /**
   * Returns the header text for scenario messages.
   *
   * @return the message header
   */
  String getScenario_message_header();

  /**
   * Returns the cron expression for scenario recurrence scheduling.
   *
   * @return the recurrence cron expression, or {@code null} if not recurring
   */
  String getScenario_recurrence();

  /**
   * Returns the start timestamp for recurrence scheduling.
   *
   * @return the recurrence start timestamp, or {@code null} if not set
   */
  Instant getScenario_recurrence_start();

  /**
   * Returns the end timestamp for recurrence scheduling.
   *
   * @return the recurrence end timestamp, or {@code null} if open-ended
   */
  Instant getScenario_recurrence_end();

  /**
   * Returns the subtitle of the scenario.
   *
   * @return the scenario subtitle
   */
  String getScenario_subtitle();

  /**
   * Returns the set of dependency identifiers for this scenario.
   *
   * @return set of dependency identifiers (e.g., "STARTERPACK")
   */
  Set<String> getScenario_dependencies();

  /**
   * Returns the severity level of the scenario.
   *
   * @return the severity level (e.g., "low", "medium", "high", "critical")
   */
  String getScenario_severity();

  String getScenario_type_affinity();

  /**
   * Returns the set of exercise IDs created from this scenario.
   *
   * @return set of exercise IDs
   */
  Set<String> getScenario_exercises();

  /**
   * Returns the kill chain phases as a serialized string.
   *
   * @return the kill chain phases data
   */
  String getScenario_kill_chain_phases();

  /**
   * Returns the set of platforms targeted by this scenario.
   *
   * @return set of platform types (e.g., "Linux", "Windows", "macOS")
   */
  Set<String> getScenario_platforms();

  /**
   * Returns the set of tag IDs associated with this scenario.
   *
   * @return set of tag IDs
   */
  Set<String> getScenario_tags();

  /**
   * Returns the team-user associations as a serialized string.
   *
   * @return the team users data
   */
  String getScenario_teams_users();

  /**
   * Returns the count of users specifically assigned to this scenario.
   *
   * @return the count of directly assigned users
   */
  Long getScenario_users_number();

  /**
   * Returns the total count of users across all teams in this scenario.
   *
   * @return the total user count
   */
  Long getScenario_all_users_number();
}
