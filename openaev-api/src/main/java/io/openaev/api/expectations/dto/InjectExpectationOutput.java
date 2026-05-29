package io.openaev.api.expectations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.InjectExpectation.EXPECTATION_STATUS;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectExpectationSignature;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record InjectExpectationOutput(
    @Schema(description = "ID of the inject expectation")
    @JsonProperty("inject_expectation_id")
    String id,

    @Schema(description = "Type of the inject expectation")
    @JsonProperty("inject_expectation_type")
    EXPECTATION_TYPE type,

    @Schema(description = "Name of the inject expectation")
    @JsonProperty("inject_expectation_name")
    String name,

    @Schema(description = "Description of the inject expectation")
    @JsonProperty("inject_expectation_description")
    String description,

    @Schema(description = "Current score of the inject expectation")
    @JsonProperty("inject_expectation_score")
    Double score,

    @Schema(description = "Expected score of the inject expectation")
    @JsonProperty("inject_expectation_expected_score")
    Double expectedScore,

    @Schema(description = "Expiration time in seconds")
    @JsonProperty("inject_expiration_time")
    Long expirationTime,

    @Schema(description = "Whether this expectation is a group expectation")
    @JsonProperty("inject_expectation_group")
    boolean expectationGroup,

    @Schema(description = "Computed status of the inject expectation")
    @JsonProperty("inject_expectation_status")
    EXPECTATION_STATUS status,

    @Schema(description = "Creation date of the inject expectation")
    @JsonProperty("inject_expectation_created_at")
    Instant createdAt,

    @Schema(description = "Last update date of the inject expectation")
    @JsonProperty("inject_expectation_updated_at")
    Instant updatedAt,

    @Schema(description = "Signatures associated with the inject expectation")
    @JsonProperty("inject_expectation_signatures")
    List<InjectExpectationSignature> signatures,

    @Schema(description = "Results associated with the inject expectation")
    @JsonProperty("inject_expectation_results")
    List<InjectExpectationResult> results,

    @Schema(description = "Exercise ID associated with the inject expectation")
    @JsonProperty("inject_expectation_exercise")
    String exerciseId,

    @Schema(description = "Inject ID associated with the inject expectation")
    @JsonProperty("inject_expectation_inject")
    String injectId,

    @Schema(description = "User ID associated with the inject expectation")
    @JsonProperty("inject_expectation_user")
    String userId,

    @Schema(description = "Team ID associated with the inject expectation")
    @JsonProperty("inject_expectation_team")
    String teamId,

    @Schema(description = "Agent ID associated with the inject expectation")
    @JsonProperty("inject_expectation_agent")
    String agentId,

    @Schema(description = "Asset ID associated with the inject expectation")
    @JsonProperty("inject_expectation_asset")
    String assetId,

    @Schema(description = "Asset group ID associated with the inject expectation")
    @JsonProperty("inject_expectation_asset_group")
    String assetGroupId,

    @Schema(description = "Article ID associated with the inject expectation")
    @JsonProperty("inject_expectation_article")
    String articleId,

    @Schema(description = "Challenge ID associated with the inject expectation")
    @JsonProperty("inject_expectation_challenge")
    String challengeId,

    @Schema(description = "Target ID resolved from user, team, agent, asset, or asset group")
    @JsonProperty("target_id")
    String targetId) {}
