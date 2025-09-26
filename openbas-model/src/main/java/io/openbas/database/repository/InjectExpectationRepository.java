package io.openbas.database.repository;

import io.openbas.database.model.InjectExpectation;
import io.openbas.database.raw.RawInjectExpectation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectExpectationRepository
    extends CrudRepository<InjectExpectation, String>, JpaSpecificationExecutor<InjectExpectation> {

  @NotNull
  Optional<InjectExpectation> findById(@NotNull String id);

  @Query(value = "select i from InjectExpectation i where i.exercise.id = :exerciseId")
  List<InjectExpectation> findAllForExercise(@Param("exerciseId") String exerciseId);

  @Query(
      value =
          "select i from InjectExpectation i where i.exercise.id = :exerciseId and i.inject.id = :injectId")
  List<InjectExpectation> findAllForExerciseAndInject(
      @Param("exerciseId") @NotBlank final String exerciseId,
      @Param("injectId") @NotBlank final String injectId);

  @Query(
      value =
          "select i from InjectExpectation i where i.exercise.id = :exerciseId "
              + "and i.type = 'CHALLENGE' and i.user.id = :userId ")
  List<InjectExpectation> findChallengeExpectationsByExerciseAndUser(
      @Param("exerciseId") String exerciseId, @Param("userId") String userId);

  @Query(
      value =
          "select i from InjectExpectation i where i.user.id = :userId and i.exercise.id = :exerciseId "
              + "and i.challenge.id = :challengeId and i.type = 'CHALLENGE' ")
  List<InjectExpectation> findByUserAndExerciseAndChallenge(
      @Param("userId") String userId,
      @Param("exerciseId") String exerciseId,
      @Param("challengeId") String challengeId);

  @Query(
      value =
          "select i from InjectExpectation i where i.inject.id in (:injectIds) "
              + "and i.article.id in (:articlesIds) and i.team.id in (:teamIds) and i.type = 'ARTICLE'")
  List<InjectExpectation> findChannelExpectations(
      @Param("injectIds") List<String> injectIds,
      @Param("teamIds") List<String> teamIds,
      @Param("articlesIds") List<String> articlesIds);

  // -- BY TARGET TYPE

  @Query(
      value =
          "select i from InjectExpectation i "
              + "where i.inject.id = :injectId "
              + "and i.team.id = :teamId "
              + "and i.user.id = :playerId "
              + "ORDER BY i.type, i.createdAt")
  List<InjectExpectation> findAllByInjectAndTeamAndPlayer(
      @Param("injectId") @NotBlank final String injectId,
      @Param("teamId") @NotBlank final String teamId,
      @Param("playerId") @NotBlank final String playerId);

  @Query(
      value =
          "select i from InjectExpectation i "
              + "where i.inject.id = :injectId "
              + "and i.user.id = :playerId "
              + "ORDER BY i.type, i.createdAt")
  List<InjectExpectation> findAllByInjectAndPlayer(
      @Param("injectId") @NotBlank final String injectId,
      @Param("playerId") @NotBlank final String playerId);

  @Query(
      "select ie from InjectExpectation ie "
          + "where ie.inject.id = :injectId "
          + "and ie.team.id = :teamId "
          + "and ie.name = :expectationName "
          + "ORDER BY ie.type, ie.createdAt")
  List<InjectExpectation> findAllByInjectAndTeamAndExpectationName(
      final String injectId, final String teamId, final String expectationName);

  @Query(
      "select ie from InjectExpectation ie "
          + "where ie.inject.id = :injectId "
          + "and ie.team.id = :teamId "
          + "and ie.name = :expectationName "
          + "and ie.user is not null")
  List<InjectExpectation> findAllByInjectAndTeamAndExpectationNameAndUserIsNotNull(
      final String injectId, final String teamId, final String expectationName);

  // -- RETRIEVE EXPECTATIONS FOR TEAM AND NOT FOR PLAYERS
  @Query(
      value =
          "select i from InjectExpectation i where i.inject.id = :injectId and i.team.id = :teamId and i.user is null")
  List<InjectExpectation> findAllByInjectAndTeam(
      @Param("injectId") @NotBlank final String injectId,
      @Param("teamId") @NotBlank final String teamId);

  // -- INJECT EXPECTATION TECHNICAL --

  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.agent.id = :agentId "
              + "ORDER BY i.type, i.createdAt")
  List<InjectExpectation> findAllByInjectAndAgent(
      @Param("injectId") @NotBlank String injectId, @Param("agentId") @NotBlank String agentId);

  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.asset.id = :assetId "
              + "AND i.agent IS NULL "
              + "ORDER BY i.type, i.createdAt")
  List<InjectExpectation> findAllByInjectAndAsset(
      @Param("injectId") @NotBlank String injectId, @Param("assetId") @NotBlank String assetId);

  @Query(
      value =
          "SELECT i FROM InjectExpectation i "
              + "WHERE i.inject.id = :injectId "
              + "AND i.assetGroup.id = :assetGroupId "
              + "AND i.asset IS NULL "
              + "AND i.agent IS NULL ")
  List<InjectExpectation> findAllByInjectAndAssetGroup(
      @Param("injectId") @NotBlank final String injectId,
      @Param("assetGroupId") @NotBlank final String assetGroupId);

  @Query(
      value =
          "SELECT "
              + "i.inject_expectation_id AS inject_expectation_id, "
              + "i.inject_id AS inject_id, "
              + "i.exercise_id AS exercise_id, "
              + "i.team_id AS team_id, "
              + "i.agent_id AS agent_id, "
              + "i.asset_id AS asset_id, "
              + "i.asset_group_id AS asset_group_id, "
              + "i.inject_expectation_type AS inject_expectation_type, "
              + "i.user_id AS user_id, "
              + "i.inject_expectation_score AS inject_expectation_score, "
              + "i.inject_expectation_results AS inject_expectation_results, "
              + "i.inject_expectation_expected_score AS inject_expectation_expected_score, "
              + "i.inject_expectation_group AS inject_expectation_group "
              + "FROM injects_expectations i "
              + "WHERE i.inject_id IN (:injectIds) "
              + "AND i.user_id is null "
              + "AND i.agent_id is null ;",
      nativeQuery = true)
  // We don't include expectations for players, only for the team, neither for agents, if applicable
  List<RawInjectExpectation> rawForComputeGlobalByInjectIds(
      @Param("injectIds") Set<String> injectIds);

  @Query(
      value =
          "SELECT "
              + "i.inject_expectation_id AS inject_expectation_id, "
              + "i.inject_id AS inject_id, "
              + "i.exercise_id AS exercise_id, "
              + "i.team_id AS team_id, "
              + "i.agent_id AS agent_id, "
              + "i.asset_id AS asset_id, "
              + "i.asset_group_id AS asset_group_id, "
              + "i.inject_expectation_type AS inject_expectation_type, "
              + "i.user_id AS user_id, "
              + "i.inject_expectation_score AS inject_expectation_score, "
              + "i.inject_expectation_expected_score AS inject_expectation_expected_score, "
              + "i.inject_expectation_group AS inject_expectation_group "
              + "FROM injects_expectations i "
              + "WHERE i.exercise_id IN (:exerciseIds) "
              + "AND i.user_id is null "
              + "AND i.agent_id is null ;",
      nativeQuery = true)
  // We don't include expectations for players, only for the team, if applicable
  List<RawInjectExpectation> rawForComputeGlobalByExerciseIds(
      @Param("exerciseIds") Set<String> exerciseIds);

  @Query(
      value =
          "select i from InjectExpectation i where i.inject.id in :injectIds and i.agent is null and i.user is null")
  List<InjectExpectation> findAllForGlobalScoreByInjects(@Param("injectIds") Set<String> injectIds);

  // -- INDEXING --

  @Query(
      value =
          """
    WITH inject_expectation_data AS (
      SELECT
      ie.inject_expectation_id,
      ie.inject_expectation_name,
      ie.inject_expectation_description,
      ie.inject_expectation_type,
      ie.inject_expectation_results,
      ie.inject_expectation_score,
      ie.inject_expectation_expected_score,
      ie.inject_expiration_time,
      ie.inject_expectation_group,
      ie.inject_expectation_created_at,
      GREATEST(ie.inject_expectation_updated_at, max(i.inject_updated_at), max(ic.injector_contract_updated_at)) as inject_expectation_updated_at,
      ie.exercise_id,
      ie.inject_id,
      ie.user_id,
      ie.team_id,
      ie.agent_id,
      ie.asset_id,
      ie.asset_group_id,
      i.inject_title as inject_title,
      array_agg(DISTINCT ap.attack_pattern_id) FILTER ( WHERE ap.attack_pattern_id IS NOT NULL ) AS attack_pattern_ids,
      MAX(se.scenario_id) AS scenario_id,
      array_agg(DISTINCT c.collector_security_platform) FILTER ( WHERE c.collector_security_platform IS NOT NULL ) ||
      array_agg(DISTINCT a.asset_id) FILTER ( WHERE a.asset_id IS NOT NULL ) AS security_platform_ids
    FROM injects_expectations ie
    LEFT JOIN exercises ex ON ex.exercise_id = ie.exercise_id
    LEFT JOIN injects i ON i.inject_id = ie.inject_id
    LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract
    LEFT JOIN injectors_contracts_attack_patterns ic_ap ON ic_ap.injector_contract_id = ic.injector_contract_id
    LEFT JOIN attack_patterns ap ON ap.attack_pattern_id = ic_ap.attack_pattern_id
    LEFT JOIN users u ON u.user_id = ie.user_id
    LEFT JOIN teams t ON t.team_id = ie.team_id
    LEFT JOIN assets asset ON asset.asset_id = ie.asset_id
    LEFT JOIN asset_groups ag ON ag.asset_group_id = ie.asset_group_id
    LEFT JOIN scenarios_exercises se ON se.exercise_id = ie.exercise_id
    LEFT JOIN LATERAL jsonb_array_elements(ie.inject_expectation_results::jsonb) AS r(elem) ON true
    LEFT JOIN collectors c ON r.elem->>'sourceId' = c.collector_id::text
    LEFT JOIN assets a ON r.elem->>'sourceId' = a.asset_id::text
    GROUP BY
      ie.inject_expectation_id,
      ic.injector_contract_id,
      i.inject_title
    )
    SELECT * FROM inject_expectation_data ied
    WHERE ied.inject_expectation_updated_at > :from AND ied.agent_id IS NULL
    ORDER BY ied.inject_expectation_updated_at ASC
    LIMIT 500
    """,
      nativeQuery = true)
  List<RawInjectExpectation> findForIndexing(@Param("from") Instant from);
}
