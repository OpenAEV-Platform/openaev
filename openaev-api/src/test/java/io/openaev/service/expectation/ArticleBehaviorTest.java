package io.openaev.service.expectation;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ArticleInjectExpectation;
import io.openaev.database.model.ChallengeInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.User;
import io.openaev.utils.fixtures.UserFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ArticleBehavior")
class ArticleBehaviorTest {

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("given_article_expectation_should_return_true")
    void given_article_expectation_should_return_true() {
      // Arrange
      ArticleBehavior behavior = new ArticleBehavior();
      ArticleInjectExpectation expectation = new ArticleInjectExpectation();

      // Act
      boolean supported = behavior.supports(expectation);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_non_article_expectation_should_return_false")
    void given_non_article_expectation_should_return_false() {
      // Arrange
      ArticleBehavior behavior = new ArticleBehavior();
      ChallengeInjectExpectation expectation = new ChallengeInjectExpectation();

      // Act
      boolean supported = behavior.supports(expectation);

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("initializeResults")
  class InitializeResults {

    @Test
    @DisplayName("given_expectation_with_user_should_set_default_article_result")
    void given_expectation_with_user_should_set_default_article_result() {
      // Arrange
      ArticleBehavior behavior = new ArticleBehavior();
      ArticleInjectExpectation expectation = new ArticleInjectExpectation();
      User user = UserFixture.getUser();
      expectation.setUser(user);

      // Act
      behavior.initializeResults(expectation);

      // Assert
      assertThat(expectation.getResults()).hasSize(1);
      InjectExpectationResult result = expectation.getResults().getFirst();
      assertThat(result.getSourceId()).isNotBlank();
      assertThat(result.getResult()).isNull();
      assertThat(result.getScore()).isNull();
    }

    @Test
    @DisplayName("given_expectation_without_user_should_not_change_results")
    void given_expectation_without_user_should_not_change_results() {
      // Arrange
      ArticleBehavior behavior = new ArticleBehavior();
      ArticleInjectExpectation expectation = new ArticleInjectExpectation();
      List<InjectExpectationResult> existingResults = new ArrayList<>();
      existingResults.add(
          InjectExpectationResult.builder().sourceId("existing").result("KO").build());
      expectation.setResults(existingResults);

      // Act
      behavior.initializeResults(expectation);

      // Assert
      assertThat(expectation.getResults()).isSameAs(existingResults);
      assertThat(expectation.getResults()).hasSize(1);
      assertThat(expectation.getResults().getFirst().getSourceId()).isEqualTo("existing");
      assertThat(expectation.getResults().getFirst().getResult()).isEqualTo("KO");
    }
  }
}
