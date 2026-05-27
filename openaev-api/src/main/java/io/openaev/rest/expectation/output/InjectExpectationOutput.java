package io.openaev.rest.expectation.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectation.EXPECTATION_STATUS;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectExpectationSignature;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InjectExpectationOutput {

  @JsonProperty("inject_expectation_id")
  private String id;

  @JsonProperty("inject_expectation_type")
  private EXPECTATION_TYPE type;

  @JsonProperty("inject_expectation_name")
  private String name;

  @JsonProperty("inject_expectation_description")
  private String description;

  @JsonProperty("inject_expectation_score")
  private Double score;

  @JsonProperty("inject_expectation_expected_score")
  private Double expectedScore;

  @JsonProperty("inject_expiration_time")
  private Long expirationTime;

  @JsonProperty("inject_expectation_group")
  private boolean expectationGroup;

  @JsonProperty("inject_expectation_status")
  private EXPECTATION_STATUS status;

  @JsonProperty("inject_expectation_created_at")
  private Instant createdAt;

  @JsonProperty("inject_expectation_updated_at")
  private Instant updatedAt;

  @JsonProperty("inject_expectation_signatures")
  private List<InjectExpectationSignature> signatures;

  @JsonProperty("inject_expectation_results")
  private List<InjectExpectationResult> results;

  // Relations — exposed as IDs only
  @JsonProperty("inject_expectation_exercise")
  private String exerciseId;

  @JsonProperty("inject_expectation_inject")
  private String injectId;

  @JsonProperty("inject_expectation_user")
  private String userId;

  @JsonProperty("inject_expectation_team")
  private String teamId;

  @JsonProperty("inject_expectation_agent")
  private String agentId;

  @JsonProperty("inject_expectation_asset")
  private String assetId;

  @JsonProperty("inject_expectation_asset_group")
  private String assetGroupId;

  @JsonProperty("inject_expectation_article")
  private String articleId;

  @JsonProperty("inject_expectation_challenge")
  private String challengeId;

  @JsonProperty("target_id")
  private String targetId;

  // -- MAPPER --

  public static InjectExpectationOutput toOutput(InjectExpectation e) {
    return InjectExpectationOutput.builder()
        .id(e.getId())
        .type(e.getType())
        .name(e.getName())
        .description(e.getDescription())
        .score(e.getScore())
        .expectedScore(e.getExpectedScore())
        .expirationTime(e.getExpirationTime())
        .expectationGroup(e.isExpectationGroup())
        .status(e.getResponse())
        .createdAt(e.getCreatedAt())
        .updatedAt(e.getUpdatedAt())
        .signatures(e.getSignatures())
        .results(e.getResults())
        .exerciseId(e.getExercise() != null ? e.getExercise().getId() : null)
        .injectId(e.getInject() != null ? e.getInject().getId() : null)
        .userId(e.getUser() != null ? e.getUser().getId() : null)
        .teamId(e.getTeam() != null ? e.getTeam().getId() : null)
        .agentId(e.getAgent() != null ? e.getAgent().getId() : null)
        .assetId(e.getAsset() != null ? e.getAsset().getId() : null)
        .assetGroupId(e.getAssetGroup() != null ? e.getAssetGroup().getId() : null)
        .articleId(e.getArticle() != null ? e.getArticle().getId() : null)
        .challengeId(e.getChallenge() != null ? e.getChallenge().getId() : null)
        .targetId(e.getTargetId())
        .build();
  }
}
