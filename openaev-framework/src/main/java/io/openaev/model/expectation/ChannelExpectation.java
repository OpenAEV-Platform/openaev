package io.openaev.model.expectation;

import io.openaev.database.model.Article;
import io.openaev.database.model.InjectExpectation;
import io.openaev.model.Expectation;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Expectation that requires targets to read an article or channel content.
 *
 * <p>Channel expectations are fulfilled when the target user reads or acknowledges the associated
 * article. This is commonly used to verify that important information has been communicated to
 * participants.
 *
 * @see Article
 * @see Expectation
 */
@Getter
@Setter
public class ChannelExpectation implements Expectation {

  /** The score value when this expectation is fulfilled (0-100). */
  private Double score;

  /** The article that must be read. */
  private Article article;

  /** Whether this expectation is part of a group evaluation. */
  private boolean expectationGroup;

  /** Display name for this expectation. */
  private String name;

  /** Time in seconds after which this expectation expires. */
  private Long expirationTime;

  /**
   * Creates a new channel expectation from a form expectation and article.
   *
   * @param expectation the form expectation containing configuration
   * @param article the article that must be read
   */
  public ChannelExpectation(io.openaev.model.inject.form.Expectation expectation, Article article) {
    setScore(Objects.requireNonNullElse(expectation.getScore(), 100.0));
    setArticle(article);
    setName(article.getName());
    setExpectationGroup(expectation.isExpectationGroup());
    setExpirationTime(expectation.getExpirationTime());
  }

  @Override
  public InjectExpectation.EXPECTATION_TYPE type() {
    return InjectExpectation.EXPECTATION_TYPE.ARTICLE;
  }
}
