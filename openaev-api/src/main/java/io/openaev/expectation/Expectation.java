package io.openaev.expectation;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.SecurityPlatform;
import java.util.List;

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
 * @see io.openaev.expectation.DetectionExpectation
 * @see io.openaev.expectation.PreventionExpectation
 * @see io.openaev.expectation.ManualExpectation
 * @see io.openaev.expectation.VulnerabilityExpectation
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
   * Returns the optional display order of this expectation within its inject, ascending.
   *
   * <p>Lets an injector contract declare the logical sequence of its expectations (e.g. phishing
   * orders its human steps email {@literal ->} link {@literal ->} submission) so both the chain
   * timeline and the results list render them in that order rather than an incidental alphabetical
   * one. {@code null} means unordered (the default for every expectation that does not set it), and
   * readers fall back to name / id, leaving all other contracts unaffected.
   *
   * @return the display order, or {@code null} when unordered
   */
  default Integer getOrder() {
    return null;
  }

  /**
   * Returns the time after which this expectation automatically expires.
   *
   * @return expiration time in seconds from creation, or null if no expiration
   */
  Long getExpirationTime();

  /**
   * Returns the security platform types expected to fulfil this expectation.
   *
   * <p>When non-empty, the platform focuses detection/prevention on collectors of those types only.
   * An empty list means "any security platform" (legacy behaviour), and is typical for human /
   * manual expectations.
   *
   * @return the expected security platform types, never null
   */
  default List<SecurityPlatform.SECURITY_PLATFORM_TYPE> getExpectedSecurityPlatformTypes() {
    return List.of();
  }
}
