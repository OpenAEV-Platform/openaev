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

@DisplayName("ChallengeBehavior")
class ChallengeBehaviorTest {

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("given_challenge_expectation_should_return_true")
    void given_challenge_expectation_should_return_true() {
      // Arrange
      ChallengeBehavior behavior = new ChallengeBehavior();
      ChallengeInjectExpectation expectation = new ChallengeInjectExpectation();

      // Act
      boolean supported = behavior.supports(expectation);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_non_challenge_expectation_should_return_false")
    void given_non_challenge_expectation_should_return_false() {
      // Arrange
      ChallengeBehavior behavior = new ChallengeBehavior();
      ArticleInjectExpectation expectation = new ArticleInjectExpectation();

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
    @DisplayName("given_expectation_with_user_should_not_change_results")
    void given_expectation_with_user_should_not_change_results() {
      // Arrange
      ChallengeBehavior behavior = new ChallengeBehavior();
      ChallengeInjectExpectation expectation = new ChallengeInjectExpectation();
      User user = UserFixture.getUser();
      expectation.setUser(user);
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

    @Test
    @DisplayName("given_expectation_without_user_should_keep_results_empty")
    void given_expectation_without_user_should_keep_results_empty() {
      // Arrange
      ChallengeBehavior behavior = new ChallengeBehavior();
      ChallengeInjectExpectation expectation = new ChallengeInjectExpectation();

      // Act
      behavior.initializeResults(expectation);

      // Assert
      assertThat(expectation.getResults()).isEmpty();
    }
  }
}
