package io.openaev.api.expectations.mapper;

import io.openaev.api.expectations.dto.InjectExpectationOutput;
import io.openaev.database.model.*;
import java.util.List;
import java.util.Objects;

public final class InjectExpectationMapper {

  private InjectExpectationMapper() {}

  public static InjectExpectationOutput toOutput(BaseInjectExpectation expectation) {
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
        expectation.getOrder(),
        expectation.getResponse(),
        expectation.getCreatedAt(),
        expectation.getUpdatedAt(),
        // Signatures are a LAZY collection since they moved to a dedicated table: copy it while
        // the session is still open, otherwise Hibernate6Module serializes the uninitialized
        // PersistentBag as null and collectors can never match any expectation.
        List.copyOf(expectation.getSignatures()),
        // The results JSONB column can be SQL NULL on legacy rows: normalize to an empty list.
        expectation.getResults() != null ? expectation.getResults() : List.of(),
        expectation.getTraces(),
        expectation.getExercise() != null ? expectation.getExercise().getId() : null,
        expectation.getInject() != null ? expectation.getInject().getId() : null,
        expectation instanceof TableTopInjectExpectation tableTopInjectExpectation
                && tableTopInjectExpectation.getUser() != null
            ? tableTopInjectExpectation.getUser().getId()
            : null,
        expectation instanceof TableTopInjectExpectation tableTopInjectExpectation
                && tableTopInjectExpectation.getTeam() != null
            ? tableTopInjectExpectation.getTeam().getId()
            : null,
        expectation instanceof TechnicalInjectExpectation technicalInjectExpectation
                && technicalInjectExpectation.getAgent() != null
            ? technicalInjectExpectation.getAgent().getId()
            : null,
        expectation instanceof TechnicalInjectExpectation technicalInjectExpectation
                && technicalInjectExpectation.getAsset() != null
            ? technicalInjectExpectation.getAsset().getId()
            : null,
        expectation instanceof TechnicalInjectExpectation technicalInjectExpectation
                && technicalInjectExpectation.getAssetGroup() != null
            ? technicalInjectExpectation.getAssetGroup().getId()
            : null,
        expectation instanceof ArticleInjectExpectation articleInjectExpectation
                && articleInjectExpectation.getArticle() != null
            ? articleInjectExpectation.getArticle().getId()
            : null,
        expectation instanceof ChallengeInjectExpectation challengeInjectExpectation
                && challengeInjectExpectation.getChallenge() != null
            ? challengeInjectExpectation.getChallenge().getId()
            : null,
        resolveTargetId(expectation),
        expectation instanceof TechnicalInjectExpectation technicalInjectExpectation
            ? technicalInjectExpectation.getExpectedSecurityPlatforms()
            : List.of());
  }

  public static List<InjectExpectationOutput> toOutputs(
      List<? extends BaseInjectExpectation> expectations) {
    Objects.requireNonNull(expectations, "expectations must not be null");
    return expectations.stream().map(InjectExpectationMapper::toOutput).toList();
  }

  private static String resolveTargetId(BaseInjectExpectation expectation) {
    // A target is not guaranteed: agentless DETECTION / PREVENTION expectations (e.g. AI
    // adversarial injects, or seeded detection/prevention expectations validated by a security
    // platform / collector) are correlated by source + inject marker rather than by a specific
    // asset / agent / group, so they legitimately carry no target. Return null in that case
    // instead of throwing - the DTO's target_id is nullable - so the output mapper never 500s.
    return switch (expectation) {
      case TableTopInjectExpectation e when e.getUser() != null -> e.getUser().getId();
      case TableTopInjectExpectation e when e.getTeam() != null -> e.getTeam().getId();
      case TechnicalInjectExpectation e when e.getAgent() != null -> e.getAgent().getId();
      case TechnicalInjectExpectation e when e.getAsset() != null -> e.getAsset().getId();
      case TechnicalInjectExpectation e when e.getAssetGroup() != null -> e.getAssetGroup().getId();
      default -> null;
    };
  }
}
