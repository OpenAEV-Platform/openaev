package io.openaev.model.expectation;

import io.openaev.database.model.Challenge;
import io.openaev.database.model.InjectExpectation;
import io.openaev.model.Expectation;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Expectation that requires targets to complete a challenge.
 *
 * <p>Challenge expectations are fulfilled when the target user successfully completes the
 * associated challenge. This is commonly used in training scenarios where users must demonstrate
 * specific skills or knowledge.
 *
 * @see Challenge
 * @see Expectation
 */
@Getter
@Setter
public class ChallengeExpectation implements Expectation {

  /** The score value when this expectation is fulfilled (0-100). */
  private Double score;

  /** The challenge that must be completed. */
  private Challenge challenge;

  /** Whether this expectation is part of a group evaluation. */
  private boolean expectationGroup;

  /** Display name for this expectation. */
  private String name;

  /** Time in seconds after which this expectation expires. */
  private Long expirationTime;

  /**
   * Creates a new challenge expectation from a form expectation and challenge.
   *
   * @param expectation the form expectation containing configuration
   * @param challenge the challenge that must be completed
   */
  public ChallengeExpectation(
      io.openaev.model.inject.form.Expectation expectation, Challenge challenge) {
    setScore(Objects.requireNonNullElse(expectation.getScore(), 100.0));
    setChallenge(challenge);
    setName(challenge.getName());
    setExpectationGroup(expectation.isExpectationGroup());
    setExpirationTime(expectation.getExpirationTime());
  }

  @Override
  public InjectExpectation.EXPECTATION_TYPE type() {
    return InjectExpectation.EXPECTATION_TYPE.CHALLENGE;
  }
}
