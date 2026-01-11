package io.openaev.model;

import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;

/**
 * Interface representing an expectation that must be fulfilled during an injection.
 *
 * <p>Expectations define measurable outcomes that indicate whether an injection achieved its
 * intended effect. Different expectation types support different verification mechanisms:
 *
 * <ul>
 *   <li><b>DETECTION</b> - Expects security tools to detect the activity
 *   <li><b>PREVENTION</b> - Expects security controls to prevent the activity
 *   <li><b>VULNERABILITY</b> - Expects vulnerability identification
 *   <li><b>MANUAL</b> - Requires manual verification by an operator
 *   <li><b>CHALLENGE</b> - Expects users to complete a challenge
 *   <li><b>ARTICLE</b> - Expects users to read an article/channel content
 *   <li><b>DOCUMENT</b> - Expects document submission
 *   <li><b>TEXT</b> - Simple text-based expectation
 * </ul>
 *
 * <p>All expectation implementations should provide immutable instances through factory methods.
 *
 * @see io.openaev.model.expectation.DetectionExpectation
 * @see io.openaev.model.expectation.PreventionExpectation
 * @see io.openaev.model.expectation.ManualExpectation
 * @see io.openaev.model.expectation.VulnerabilityExpectation
 */
public interface Expectation {

  /**
   * Returns the type of this expectation.
   *
   * @return the expectation type
   */
  EXPECTATION_TYPE type();

  /**
   * Returns the score value for this expectation when fulfilled.
   *
   * @return the score (typically 0-100), or null if not set
   */
  Double getScore();

  /**
   * Indicates whether this expectation belongs to a group of expectations.
   *
   * <p>Grouped expectations are typically evaluated together, for example when targeting an asset
   * group where success is measured across all assets.
   *
   * @return true if this is part of a group, false otherwise
   */
  default boolean isExpectationGroup() {
    return false;
  }

  /**
   * Returns the display name for this expectation.
   *
   * @return the expectation name
   */
  String getName();

  /**
   * Returns the time after which this expectation automatically expires.
   *
   * @return expiration time in seconds from creation, or null if no expiration
   */
  Long getExpirationTime();
}
