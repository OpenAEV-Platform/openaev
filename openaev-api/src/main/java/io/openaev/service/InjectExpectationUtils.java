package io.openaev.service;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.*;
import static java.util.Optional.ofNullable;

import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.model.Expectation;
import io.openaev.model.expectation.*;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class InjectExpectationUtils {

  private InjectExpectationUtils() {}

  public static final double FAILED_SCORE_VALUE = 0.0;

  // -- SCORE --

  public static double computeScore(
      @NotNull final InjectExpectation expectation, @NotBlank final boolean success) {
    return success ? expectation.getExpectedScore() : FAILED_SCORE_VALUE;
  }

  // -- CONVERTER --

  public static InjectExpectation expectationConverter(
      @NotNull final ExecutableInject executableInject,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    InjectExpectation injectExpectation = new InjectExpectation();
    return expectationConverter(
        injectExpectation, executableInject, expectation, expectationPropertiesConfig);
  }

  public static InjectExpectation expectationConverter(
      @NotNull final Team team,
      @NotNull final ExecutableInject executableInject,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    InjectExpectation injectExpectation = new InjectExpectation();
    injectExpectation.setTeam(team);
    return expectationConverter(
        injectExpectation, executableInject, expectation, expectationPropertiesConfig);
  }

  public static InjectExpectation expectationConverter(
      @NotNull final Team team,
      @NotNull final User user,
      @NotNull final ExecutableInject executableInject,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    InjectExpectation injectExpectation = new InjectExpectation();
    injectExpectation.setTeam(team);
    injectExpectation.setUser(user);
    return expectationConverter(
        injectExpectation, executableInject, expectation, expectationPropertiesConfig);
  }

  private static InjectExpectation expectationConverter(
      @NotNull InjectExpectation injectExpectation,
      @NotNull final ExecutableInject executableInject,
      @NotNull final Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {

    injectExpectation.setExercise(executableInject.getInjection().getExercise());
    injectExpectation.setInject(executableInject.getInjection().getInject());
    injectExpectation.setExpectedScore(expectation.getScore());
    injectExpectation.setExpectationGroup(expectation.isExpectationGroup());
    injectExpectation.setName(expectation.getName());
    injectExpectation.setExpirationTime(
        ofNullable(expectation.getExpirationTime())
            .orElse(expectationPropertiesConfig.getExpirationTimeByType(expectation.type())));

    switch (expectation) {
      case ChannelExpectation e when expectation.type() == ARTICLE -> {
        injectExpectation.setArticle(e.getArticle());
      }
      case ChallengeExpectation e when expectation.type() == CHALLENGE -> {
        injectExpectation.setChallenge(e.getChallenge());
      }
      case Expectation ignored when expectation.type() == DOCUMENT -> {
        injectExpectation.setType(DOCUMENT);
      }
      case Expectation ignored when expectation.type() == TEXT -> {
        injectExpectation.setType(TEXT);
      }
      case DetectionExpectation e when expectation.type() == DETECTION -> {
        injectExpectation.setDetection(e.getAgent(), e.getAsset(), e.getAssetGroup());
        injectExpectation.setSignatures(e.getInjectExpectationSignatures());
      }
      case PreventionExpectation e when expectation.type() == PREVENTION -> {
        injectExpectation.setPrevention(e.getAgent(), e.getAsset(), e.getAssetGroup());
        injectExpectation.setSignatures(e.getInjectExpectationSignatures());
      }
      case VulnerabilityExpectation e when expectation.type() == VULNERABILITY -> {
        injectExpectation.setVulnerability(e.getAgent(), e.getAsset(), e.getAssetGroup());
        injectExpectation.setSignatures(e.getInjectExpectationSignatures());
      }
      case ManualExpectation e when expectation.type() == MANUAL -> {
        injectExpectation.setManual(e.getAgent(), e.getAsset(), e.getAssetGroup());
        injectExpectation.setDescription(e.getDescription());
      }
      default -> throw new IllegalStateException("Unexpected value: " + expectation);
    }
    return injectExpectation;
  }

  // -- RULES OF ENGAGEMENT --

  public static void computeScores(
      @NotNull final List<InjectExpectation> childrenExpectations,
      @NotNull final List<InjectExpectation> parentExpectations,
      @NotNull final InjectExpectation injectExpectation,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    @NotNull Double expectedScore = injectExpectation.getExpectedScore();
    boolean isGroup = injectExpectation.isExpectationGroup();

    final boolean noExpectationScore = noExpectationScore(childrenExpectations);
    final boolean allSuccess =
        allExpectationsMatch(childrenExpectations, score -> score >= expectedScore);
    final boolean anySuccess =
        anyExpectationsMatch(childrenExpectations, score -> score >= expectedScore);
    final boolean allError =
        allExpectationsMatch(childrenExpectations, score -> score < expectedScore);
    final boolean anyError =
        anyExpectationsMatch(childrenExpectations, score -> score < expectedScore);

    parentExpectations.forEach(
        expectation -> {
          if (noExpectationScore) {
            expectation.setScore(null);
            return;
          }

          Double score = null;
          if (isGroup) {
            if (anySuccess) {
              score = InjectExpectationUtils.computeScore(expectation, true);
            } else if (allError) {
              score = InjectExpectationUtils.computeScore(expectation, false);
            }
          } else {
            if (allSuccess) {
              score = InjectExpectationUtils.computeScore(expectation, true);
            } else if (anyError) {
              score = InjectExpectationUtils.computeScore(expectation, false);
            }
          }
          expectation.setScore(score);
          if (addResult != null) {
            expectation.getResults().add(addResult.apply(score));
          }
        });
  }

  private static boolean noExpectationScore(final List<InjectExpectation> expectations) {
    if (expectations == null || expectations.isEmpty()) {
      return true;
    }
    return expectations.stream().map(InjectExpectation::getScore).allMatch(Objects::isNull);
  }

  private static boolean allExpectationsMatch(
      final List<InjectExpectation> expectations, final Predicate<Double> predicate) {
    if (expectations == null || expectations.isEmpty()) {
      return false;
    }
    return expectations.stream()
        .map(InjectExpectation::getScore)
        .allMatch(score -> score != null && predicate.test(score));
  }

  private static boolean anyExpectationsMatch(
      final List<InjectExpectation> expectations, final Predicate<Double> predicate) {
    if (expectations == null || expectations.isEmpty()) {
      return false;
    }
    return expectations.stream()
        .map(InjectExpectation::getScore)
        .filter(Objects::nonNull)
        .anyMatch(predicate);
  }
}
