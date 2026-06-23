package io.openaev.service.expectation;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.ManualInjectExpectation;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.utils.fixtures.UserFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AbstractTableTopBehavior")
class AbstractTableTopBehaviorTest {

  @Nested
  @DisplayName("applyResultToLeaves")
  class ApplyResultToLeaves {

    @Test
    @DisplayName("given_existing_results_should_replace_and_compute_score")
    void given_existing_results_should_replace_and_compute_score() {
      // Arrange
      TestTableTopBehavior behavior = new TestTableTopBehavior();
      ManualInjectExpectation expectation = new ManualInjectExpectation();
      expectation.setExpectedScore(100.0);
      expectation.setResults(
          new ArrayList<>(
              List.of(
                  InjectExpectationResult.builder().sourceId("old-source").result("KO").build())));
      ExpectationUpdateInput input =
          ExpectationUpdateInput.builder()
              .sourceId("source-1")
              .sourceType("manual")
              .sourceName("Manual source")
              .sourcePlatform("N/A")
              .score(42.0)
              .build();

      // Act
      behavior.applyResultToLeaves(expectation, input);

      // Assert
      assertThat(expectation.getResults()).hasSize(1);
      assertThat(expectation.getResults().getFirst().getSourceId()).isEqualTo("source-1");
      assertThat(expectation.getResults().getFirst().getResult()).isNotBlank();
      assertThat(expectation.getScore()).isEqualTo(42.0);
    }
  }

  @Nested
  @DisplayName("propagate")
  class Propagate {

    @Test
    @DisplayName("given_expectation_with_user_should_delegate_to_team")
    void given_expectation_with_user_should_delegate_to_team() {
      // Arrange
      TestTableTopBehavior behavior = new TestTableTopBehavior();
      BaseInjectExpectation expectation = new ManualInjectExpectation();
      expectation.setUser(UserFixture.getUser());
      expectation.setResults(
          new ArrayList<>(
              List.of(InjectExpectationResult.builder().sourceId("s").result("SUCCESS").build())));

      // Act
      List<BaseInjectExpectation> propagated = behavior.propagate(expectation);

      // Assert
      assertThat(propagated).isSameAs(behavior.teamReturn);
      assertThat(behavior.lastPropagationPath).isEqualTo("team");
      assertThat(behavior.lastResult).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName(
        "given_expectation_without_user_and_empty_results_should_delegate_to_players_with_null_result")
    void
        given_expectation_without_user_and_empty_results_should_delegate_to_players_with_null_result() {
      // Arrange
      TestTableTopBehavior behavior = new TestTableTopBehavior();
      BaseInjectExpectation expectation = new ManualInjectExpectation();

      // Act
      List<BaseInjectExpectation> propagated = behavior.propagate(expectation);

      // Assert
      assertThat(propagated).isSameAs(behavior.playersReturn);
      assertThat(behavior.lastPropagationPath).isEqualTo("players");
      assertThat(behavior.lastResult).isNull();
    }
  }

  private static final class TestTableTopBehavior extends AbstractTableTopBehavior {

    private final List<BaseInjectExpectation> teamReturn = List.of(new ManualInjectExpectation());
    private final List<BaseInjectExpectation> playersReturn =
        List.of(new ManualInjectExpectation());
    private String lastPropagationPath;
    private String lastResult;

    @Override
    public boolean supports(BaseInjectExpectation expectation) {
      return true;
    }

    @Override
    protected List<BaseInjectExpectation> propagateToPlayers(
        BaseInjectExpectation expectation, String result) {
      this.lastPropagationPath = "players";
      this.lastResult = result;
      return playersReturn;
    }

    @Override
    protected List<BaseInjectExpectation> propagateToTeam(
        BaseInjectExpectation expectation, String result) {
      this.lastPropagationPath = "team";
      this.lastResult = result;
      return teamReturn;
    }
  }
}
