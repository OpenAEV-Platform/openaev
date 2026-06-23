package io.openaev.service.expectation;

import static io.openaev.service.InjectExpectationUtils.computeScores;
import static io.openaev.utils.ExpectationUtils.getExpectationTeams;
import static io.openaev.utils.ExpectationUtils.getExpectationsPlayersForTeam;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.addResult;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForPlayerManualValidation;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForTeamManualValidation;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.computeScore;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import java.util.List;

/**
 * Shared behavior for table-top style expectations (manual/challenge/article).
 *
 * <p>This class mirrors legacy human-response logic from {@code InjectExpectationService} but stays
 * intentionally disconnected for now.
 */
public abstract class AbstractTableTopBehavior implements ExpectationBehavior {

  @Override
  public void applyResultToLeaves(BaseInjectExpectation expectation, ExpectationUpdateInput input) {
    String result =
        ExpectationType.label(
            expectation.getType(), expectation.getExpectedScore(), input.getScore());
    expectation.getResults().clear();
    addResult(expectation, input, result);
    expectation.setScore(computeScore(expectation.getResults(), expectation));
  }

  @Override
  public void initializeResults(BaseInjectExpectation expectation) {
    // Default no-op. Concrete table-top behaviors can set specific defaults.
  }

  @Override
  public List<BaseInjectExpectation> propagate(BaseInjectExpectation expectation) {
    String result =
        expectation.getResults().isEmpty() ? null : expectation.getResults().getLast().getResult();
    if (expectation.getUser() != null) {
      return propagateToTeam(expectation, result);
    }
    return propagateToPlayers(expectation, result);
  }

  protected List<BaseInjectExpectation> propagateToPlayers(
      BaseInjectExpectation expectation, String result) {
    List<BaseInjectExpectation> expectationsForPlayers = getExpectationsPlayersForTeam(expectation);
    for (BaseInjectExpectation expectationForPlayer : expectationsForPlayers) {
      expectationForPlayer.getResults().clear();
      if (result != null) {
        expectationForPlayer
            .getResults()
            .add(buildForTeamManualValidation(result, expectation.getScore()));
      }
      expectationForPlayer.setScore(expectation.getScore());
    }
    return expectationsForPlayers;
  }

  protected List<BaseInjectExpectation> propagateToTeam(
      BaseInjectExpectation expectation, String result) {
    List<BaseInjectExpectation> expectationsForPlayers = getExpectationsPlayersForTeam(expectation);
    List<BaseInjectExpectation> expectationForTeams = getExpectationTeams(expectation);
    computeScores(
        expectationsForPlayers,
        expectationForTeams,
        expectation,
        score -> buildForPlayerManualValidation(result, score));
    return expectationForTeams;
  }
}
