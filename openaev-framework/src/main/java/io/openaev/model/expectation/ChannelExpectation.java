package io.openaev.model.expectation;

import io.openaev.database.model.Article;
import io.openaev.database.model.InjectExpectation;
import io.openaev.model.Expectation;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelExpectation implements Expectation {

  private Double score;
  private Article article;
  private boolean expectationGroup;
  private String name;
  private Long expirationTime;

  public ChannelExpectation(io.openaev.model.inject.form.Expectation expectation, Article article) {
    setScore(Objects.requireNonNullElse(score, 100.0));
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
