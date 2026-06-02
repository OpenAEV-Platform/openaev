package io.openaev.api.expectations.mapper;

import io.openaev.api.expectations.dto.InjectExpectationOutput;
import io.openaev.database.model.InjectExpectation;
import java.util.List;
import java.util.Objects;

public final class InjectExpectationMapper {

  private InjectExpectationMapper() {}

  public static InjectExpectationOutput toOutput(InjectExpectation expectation) {
    Objects.requireNonNull(expectation, "expectation must not be null");

    return new InjectExpectationOutput(
        expectation.getId(),
        expectation.getType(),
        expectation.getName(),
        expectation.getDescription(),
        expectation.getScore(),
        expectation.getExpectedScore(),
        expectation.getExpirationTime(),
        expectation.isExpectationGroup(),
        expectation.getResponse(),
        expectation.getCreatedAt(),
        expectation.getUpdatedAt(),
        expectation.getSignatures(),
        expectation.getResults(),
        expectation.getTraces(),
        expectation.getExercise() != null ? expectation.getExercise().getId() : null,
        expectation.getInject() != null ? expectation.getInject().getId() : null,
        expectation.getUser() != null ? expectation.getUser().getId() : null,
        expectation.getTeam() != null ? expectation.getTeam().getId() : null,
        expectation.getAgent() != null ? expectation.getAgent().getId() : null,
        expectation.getAsset() != null ? expectation.getAsset().getId() : null,
        expectation.getAssetGroup() != null ? expectation.getAssetGroup().getId() : null,
        expectation.getArticle() != null ? expectation.getArticle().getId() : null,
        expectation.getChallenge() != null ? expectation.getChallenge().getId() : null,
        resolveTargetId(expectation));
  }

  public static List<InjectExpectationOutput> toOutputs(List<InjectExpectation> expectations) {
    Objects.requireNonNull(expectations, "expectations must not be null");
    return expectations.stream().map(InjectExpectationMapper::toOutput).toList();
  }

  private static String resolveTargetId(InjectExpectation expectation) {
    if (expectation.getUser() != null) {
      return expectation.getUser().getId();
    }
    if (expectation.getTeam() != null) {
      return expectation.getTeam().getId();
    }
    if (expectation.getAgent() != null) {
      return expectation.getAgent().getId();
    }
    if (expectation.getAsset() != null) {
      return expectation.getAsset().getId();
    }
    if (expectation.getAssetGroup() != null) {
      return expectation.getAssetGroup().getId();
    }
    throw new IllegalStateException(
        "InjectExpectation must have at least one target (user, team, agent, asset, or assetGroup)");
  }
}
