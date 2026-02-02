package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for inject expectation data.
 *
 * <p>This interface defines a projection for retrieving inject expectation information including
 * scoring, target assignments, and evaluation results. Expectations define what outcomes are
 * expected from inject execution and track whether those outcomes were achieved.
 *
 * @see io.openaev.database.model.InjectExpectation
 */
public interface RawInjectExpectation {

  /**
   * Returns the unique identifier of the inject expectation.
   *
   * @return the expectation ID
   */
  String getInject_expectation_id();

  /**
   * Returns the display name of the expectation.
   *
   * @return the expectation name
   */
  String getInject_expectation_name();

  /**
   * Returns the title of the associated inject.
   *
   * @return the inject title
   */
  String getInject_title();

  /**
   * Returns the description of what is expected.
   *
   * @return the expectation description
   */
  String getInject_expectation_description();

  /**
   * Returns the type of expectation.
   *
   * @return the expectation type (e.g., "DETECTION", "PREVENTION", "MANUAL")
   */
  String getInject_expectation_type();

  /**
   * Returns the evaluation results as a serialized string.
   *
   * @return the results data
   */
  String getInject_expectation_results();

  /**
   * Returns the actual score achieved for this expectation.
   *
   * @return the achieved score, or {@code null} if not yet evaluated
   */
  Double getInject_expectation_score();

  /**
   * Returns the expected/target score for this expectation.
   *
   * @return the expected score
   */
  Double getInject_expectation_expected_score();

  /**
   * Returns the expiration time in milliseconds for this expectation.
   *
   * @return the expiration time
   */
  Long getInject_expiration_time();

  /**
   * Returns whether this is a group-level expectation.
   *
   * @return {@code true} if this is a group expectation, {@code false} for individual expectations
   */
  Boolean getInject_expectation_group();

  /**
   * Returns the creation timestamp of the expectation.
   *
   * @return the creation timestamp
   */
  Instant getInject_expectation_created_at();

  /**
   * Returns the last update timestamp of the expectation.
   *
   * @return the update timestamp
   */
  Instant getInject_expectation_updated_at();

  /**
   * Returns the ID of the exercise this expectation belongs to.
   *
   * @return the exercise ID, or {@code null} if part of a scenario only
   */
  String getExercise_id();

  /**
   * Returns the ID of the inject this expectation is for.
   *
   * @return the inject ID
   */
  String getInject_id();

  /**
   * Returns the ID of the user this expectation targets.
   *
   * @return the user ID, or {@code null} if not targeting a specific user
   */
  String getUser_id();

  /**
   * Returns the ID of the team this expectation targets.
   *
   * @return the team ID, or {@code null} if not targeting a specific team
   */
  String getTeam_id();

  /**
   * Returns the ID of the agent this expectation targets.
   *
   * @return the agent ID, or {@code null} if not targeting a specific agent
   */
  String getAgent_id();

  /**
   * Returns the ID of the asset this expectation targets.
   *
   * @return the asset ID, or {@code null} if not targeting a specific asset
   */
  String getAsset_id();

  /**
   * Returns the ID of the asset group this expectation targets.
   *
   * @return the asset group ID, or {@code null} if not targeting an asset group
   */
  String getAsset_group_id();

  /**
   * Returns the set of attack pattern IDs associated with this expectation.
   *
   * @return set of attack pattern IDs
   */
  Set<String> getAttack_pattern_ids();

  /**
   * Returns the set of security platform IDs expected to detect/prevent the inject.
   *
   * @return set of security platform IDs
   */
  Set<String> getSecurity_platform_ids();

  /**
   * Returns the set of security domain IDs expected to detect/prevent the inject.
   *
   * @return set of security domain IDs
   */
  Set<String> getDomain_ids();

  /**
   * Returns the ID of the scenario this expectation belongs to.
   *
   * @return the scenario ID, or {@code null} if part of an exercise only
   */
  String getScenario_id();

  /**
   * Returns the timestamp when the inject was sent/executed.
   *
   * @return the tracking sent date
   */
  Instant getTracking_sent_date();
}
