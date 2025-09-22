package io.openbas.utils.fixtures;

import io.openbas.database.raw.RawInjectExpectation;
import java.time.Instant;
import java.util.Set;

public class RawInjectExpectationFixture {

  private record TestableRawInjectExpectation(
      String injectExpectationId,
      String injectExpectationName,
      String injectTitle,
      String injectExpectationDescription,
      String expectationType,
      String expectationResults,
      Double expectationScore,
      Double expectationExpectedScore,
      Long injectExpirationTime,
      Boolean expectationGroup,
      Instant createdAt,
      Instant updatedAt,
      String exerciseId,
      String injectId,
      String userId,
      String teamId,
      String agentId,
      String assetId,
      String assetGroupId,
      Set<String> attackPatternIds,
      String scenarioId,
      Set<String> securityPlatformIds)
      implements RawInjectExpectation {

    @Override
    public String getInject_expectation_id() {
      return injectExpectationId;
    }

    @Override
    public String getInject_expectation_name() {
      return injectExpectationName;
    }

    @Override
    public String getInject_title() {
      return injectExpectationName;
    }

    @Override
    public String getInject_expectation_description() {
      return injectExpectationDescription;
    }

    @Override
    public String getInject_expectation_type() {
      return expectationType;
    }

    @Override
    public String getInject_expectation_results() {
      return expectationResults;
    }

    @Override
    public Double getInject_expectation_score() {
      return expectationScore;
    }

    @Override
    public Double getInject_expectation_expected_score() {
      return expectationExpectedScore;
    }

    @Override
    public Long getInject_expiration_time() {
      return injectExpirationTime;
    }

    @Override
    public Boolean getInject_expectation_group() {
      return expectationGroup;
    }

    @Override
    public Instant getInject_expectation_created_at() {
      return createdAt;
    }

    @Override
    public Instant getInject_expectation_updated_at() {
      return updatedAt;
    }

    @Override
    public String getExercise_id() {
      return exerciseId;
    }

    @Override
    public String getInject_id() {
      return injectId;
    }

    @Override
    public String getUser_id() {
      return userId;
    }

    @Override
    public String getTeam_id() {
      return teamId;
    }

    @Override
    public String getAgent_id() {
      return "";
    }

    @Override
    public String getAsset_id() {
      return assetId;
    }

    @Override
    public String getAsset_group_id() {
      return assetGroupId;
    }

    @Override
    public Set<String> getAttack_pattern_ids() {
      return attackPatternIds;
    }

    @Override
    public Set<String> getSecurity_platform_ids() {
      return securityPlatformIds;
    }

    @Override
    public String getScenario_id() {
      return scenarioId;
    }
  }

  public static RawInjectExpectation createDefaultInjectExpectation(
      String expectationType, Double expectationScore, Double expectationExpectedScore) {
    return new TestableRawInjectExpectation(
        null,
        null,
        null,
        null,
        expectationType,
        null,
        expectationScore,
        expectationExpectedScore,
        null,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
