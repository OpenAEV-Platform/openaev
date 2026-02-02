package io.openaev.database.repository;

import io.openaev.database.model.Scenario;
import io.openaev.database.raw.RawExerciseSimple;
import io.openaev.database.raw.RawScenario;
import io.openaev.database.raw.RawScenarioSimple;
import io.openaev.utils.Constants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository interface for {@link Scenario} entities.
 *
 * <p>This repository provides data access operations for scenarios, which are reusable templates
 * for security exercises. Scenarios define collections of injects, team configurations, and
 * recurrence settings. It supports:
 *
 * <ul>
 *   <li>Standard CRUD operations via {@link JpaRepository}
 *   <li>Dynamic filtering via {@link JpaSpecificationExecutor}
 *   <li>Statistical queries via {@link StatisticRepository}
 *   <li>Access-controlled queries respecting user grants
 *   <li>Search engine indexing support
 *   <li>Category management and search
 *   <li>Team assignment operations
 * </ul>
 *
 * @see Scenario
 * @see io.openaev.database.model.Exercise
 * @see io.openaev.database.model.Inject
 */
@Repository
public interface ScenarioRepository
    extends JpaRepository<Scenario, String>,
        StatisticRepository,
        JpaSpecificationExecutor<Scenario> {

  @Query(
      value =
          "WITH scenario_data AS ("
              + "SELECT s.scenario_id, s.scenario_name, s.scenario_recurrence, s.scenario_created_at, "
              + "GREATEST(s.scenario_updated_at, max(inj.inject_updated_at), max(ic.injector_contract_updated_at)) as scenario_injects_updated_at, "
              + "array_agg(DISTINCT st.tag_id) FILTER (WHERE st.tag_id IS NOT NULL) as scenario_tags, "
              + "array_agg(DISTINCT ste.team_id) FILTER (WHERE ste.team_id IS NOT NULL) as scenario_teams, "
              + "array_agg(DISTINCT ia.asset_id) FILTER (WHERE ia.asset_id IS NOT NULL) as scenario_assets, "
              + "array_agg(DISTINCT iag.asset_group_id) FILTER (WHERE iag.asset_group_id IS NOT NULL) as scenario_asset_groups, "
              + "array_union_agg(ic.injector_contract_platforms) FILTER ( WHERE ic.injector_contract_platforms IS NOT NULL ) as scenario_platforms "
              + "FROM scenarios s "
              + "LEFT JOIN scenarios_tags st ON st.scenario_id = s.scenario_id "
              + "LEFT JOIN scenarios_teams ste ON ste.scenario_id = s.scenario_id "
              + "LEFT JOIN injects inj ON s.scenario_id = inj.inject_scenario "
              + "LEFT JOIN injects_assets ia ON ia.inject_id = inj.inject_id "
              + "LEFT JOIN injects_asset_groups iag ON iag.inject_id = inj.inject_id "
              + "LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = inj.inject_injector_contract "
              + "GROUP BY s.scenario_id, s.scenario_name, s.scenario_created_at, s.scenario_updated_at"
              + ") "
              + "SELECT * FROM scenario_data sd "
              + "WHERE sd.scenario_injects_updated_at > :from "
              + "ORDER BY sd.scenario_injects_updated_at ASC LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawScenarioSimple> findForIndexing(@Param("from") Instant from);

  @Query(
      value =
          "SELECT ex.exercise_id, "
              + "ex.exercise_status, "
              + "ex.exercise_start_date, "
              + "ex.exercise_created_at, "
              + "ex.exercise_updated_at, "
              + "ex.exercise_end_date, "
              + "ex.exercise_name, "
              + "ex.exercise_category, "
              + "ex.exercise_subtitle, "
              + " array_agg(distinct ie.inject_id) FILTER ( WHERE ie.inject_id IS NOT NULL ) as inject_ids, "
              + " array_agg(distinct et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as exercise_tags "
              + "FROM exercises ex "
              + "LEFT JOIN scenarios_exercises se ON se.exercise_id = ex.exercise_id "
              + "LEFT JOIN scenarios s ON se.scenario_id = s.scenario_id "
              + "LEFT JOIN exercises_tags et ON et.exercise_id = ex.exercise_id "
              + "LEFT JOIN injects_expectations ie ON ex.exercise_id = ie.exercise_id "
              + "WHERE s.scenario_external_reference = :externalReference "
              + "GROUP BY ex.exercise_id ;",
      nativeQuery = true)
  List<RawExerciseSimple> rawAllByExternalReference(
      @Param("externalReference") String externalReference);

  @Override
  @Query(
      "select count(distinct u) from User u "
          + "join u.teams as team "
          + "join team.scenarios as s "
          + "join s.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and u.createdAt > :creationDate")
  long userCount(String userId, Instant creationDate);

  @Override
  @Query("select count(distinct s) from Scenario s where s.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      value =
          "SELECT scenario_category, COUNT(*) AS category_count "
              + "FROM scenarios "
              + "GROUP BY scenario_category "
              + "ORDER BY category_count DESC "
              + "LIMIT :limit",
      nativeQuery = true)
  List<Object[]> findTopCategories(@Param("limit") @NotNull final int limit);

  @Query(
      value =
          "SELECT sce.scenario_id, sce.scenario_name, sce.scenario_subtitle, array_agg(sct.tag_id) FILTER (WHERE sct.tag_id IS NOT NULL) as scenario_tags "
              + "FROM scenarios sce "
              + "LEFT JOIN scenarios_tags sct ON sct.scenario_id = sce.scenario_id "
              + "INNER JOIN grants ON grants.grant_resource = sce.scenario_id AND grants.grant_resource_type = 'SCENARIO' "
              + "INNER JOIN groups ON grants.grant_group = groups.group_id "
              + "INNER JOIN users_groups ON groups.group_id = users_groups.group_id "
              + "WHERE users_groups.user_id = :userId "
              + "GROUP BY sce.scenario_id",
      nativeQuery = true)
  List<RawScenarioSimple> rawAllGranted(@Param("userId") String userId);

  @Query(
      value =
          "SELECT sce.scenario_id, sce.scenario_name, sce.scenario_subtitle, array_agg(sct.tag_id) FILTER (WHERE sct.tag_id IS NOT NULL) as scenario_tags "
              + "FROM scenarios sce "
              + "LEFT JOIN scenarios_tags sct ON sct.scenario_id = sce.scenario_id "
              + "INNER JOIN grants ON grants.grant_resource = sce.scenario_id AND grants.grant_resource_type = 'SCENARIO' "
              + "INNER JOIN groups ON grants.grant_group = groups.group_id "
              + "INNER JOIN users_groups ON groups.group_id = users_groups.group_id "
              + "WHERE users_groups.user_id = :userId "
              + "AND sce.scenario_id IN :scenarioIds "
              + "GROUP BY sce.scenario_id",
      nativeQuery = true)
  List<RawScenarioSimple> rawGrantedByScenarioIds(
      @Param("userId") String userId, @Param("scenarioIds") List<String> scenarioIds);

  @Query(
      value =
          "SELECT sce.scenario_id, sce.scenario_name, sce.scenario_subtitle, array_agg(sct.tag_id) FILTER (WHERE sct.tag_id IS NOT NULL) as scenario_tags "
              + "FROM scenarios sce "
              + "LEFT JOIN scenarios_tags sct ON sct.scenario_id = sce.scenario_id "
              + "GROUP BY sce.scenario_id",
      nativeQuery = true)
  List<RawScenarioSimple> rawAll();

  @Query(
      value =
          "SELECT sce.scenario_id, sce.scenario_name, sce.scenario_subtitle, array_agg(sct.tag_id) FILTER (WHERE sct.tag_id IS NOT NULL) as scenario_tags "
              + "FROM scenarios sce "
              + "LEFT JOIN scenarios_tags sct ON sct.scenario_id = sce.scenario_id "
              + "WHERE sce.scenario_id IN :scenarioIds "
              + "GROUP BY sce.scenario_id",
      nativeQuery = true)
  List<RawScenarioSimple> rawByScenarioIds(@Param("scenarioIds") List<String> scenarioIds);

  @Query(
      value =
          "WITH "
              + "all_users AS ( "
              + "  SELECT st.scenario_id, COUNT(DISTINCT ut.user_id) AS scenario_all_users_number "
              + "  FROM scenarios_teams st "
              + "  JOIN users_teams ut ON ut.team_id = st.team_id "
              + "  WHERE st.scenario_id = :scenarioId "
              + "  GROUP BY st.scenario_id "
              + "), "
              + "scenario_users AS ( "
              + "  SELECT scenario_id, "
              + "         COUNT(DISTINCT user_id) AS scenario_users_number, "
              + "         json_agg(DISTINCT stu.*) FILTER (WHERE stu IS NOT NULL) AS scenario_teams_users "
              + "  FROM scenarios_teams_users stu "
              + "  WHERE scenario_id = :scenarioId "
              + "  GROUP BY scenario_id "
              + "), "
              + "exercises AS ( "
              + "  SELECT scenario_id, "
              + "         array_agg(DISTINCT exercise_id) FILTER (WHERE exercise_id IS NOT NULL) AS scenario_exercises "
              + "  FROM scenarios_exercises "
              + "  WHERE scenario_id = :scenarioId "
              + "  GROUP BY scenario_id "
              + "), "
              + "kill_chain AS ( "
              + "  SELECT i.inject_scenario AS scenario_id, "
              + "         json_agg(DISTINCT kcp.*) FILTER (WHERE kcp IS NOT NULL) AS scenario_kill_chain_phases "
              + "  FROM injects i "
              + "  JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract "
              + "  JOIN injectors_contracts_attack_patterns icap ON ic.injector_contract_id = icap.injector_contract_id "
              + "  JOIN attack_patterns_kill_chain_phases apkcp ON icap.attack_pattern_id = apkcp.attack_pattern_id "
              + "  JOIN kill_chain_phases kcp ON kcp.phase_id = apkcp.phase_id "
              + "  WHERE i.inject_scenario = :scenarioId "
              + "  GROUP BY i.inject_scenario "
              + "), "
              + "platforms AS ( "
              + "  SELECT i.inject_scenario AS scenario_id, "
              + "         array_union_agg(ic.injector_contract_platforms) "
              + "           FILTER (WHERE ic.injector_contract_platforms IS NOT NULL) "
              + "         AS scenario_platforms "
              + "  FROM injects i "
              + "  JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract "
              + "  WHERE i.inject_scenario = :scenarioId "
              + "  GROUP BY i.inject_scenario "
              + "), "
              + "tags AS ( "
              + "  SELECT scenario_id, "
              + "         array_agg(DISTINCT tag_id) FILTER (WHERE tag_id IS NOT NULL) AS scenario_tags "
              + "  FROM scenarios_tags "
              + "  WHERE scenario_id = :scenarioId "
              + "  GROUP BY scenario_id "
              + ") "
              + "SELECT s.*, "
              + "       au.scenario_all_users_number, "
              + "       su.scenario_users_number, "
              + "       ex.scenario_exercises, "
              + "       kc.scenario_kill_chain_phases, "
              + "       pf.scenario_platforms, "
              + "       tg.scenario_tags, "
              + "       su.scenario_teams_users "
              + "FROM scenarios s "
              + "LEFT JOIN all_users au ON au.scenario_id = s.scenario_id "
              + "LEFT JOIN scenario_users su ON su.scenario_id = s.scenario_id "
              + "LEFT JOIN exercises ex ON ex.scenario_id = s.scenario_id "
              + "LEFT JOIN kill_chain kc ON kc.scenario_id = s.scenario_id "
              + "LEFT JOIN platforms pf ON pf.scenario_id = s.scenario_id "
              + "LEFT JOIN tags tg ON tg.scenario_id = s.scenario_id "
              + "WHERE s.scenario_id = :scenarioId",
      nativeQuery = true)
  RawScenario getScenarioById(@Param("scenarioId") final String scenarioId);

  // -- CATEGORY --

  @Query(
      "SELECT DISTINCT s.category FROM Scenario s WHERE LOWER(s.category) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
  List<String> findDistinctCategoriesBySearchTerm(
      @Param("searchTerm") final String searchTerm, Pageable pageable);

  // -- PAGINATION --

  @NotNull
  @EntityGraph(value = "Scenario.tags-injects", type = EntityGraph.EntityGraphType.LOAD)
  Page<Scenario> findAll(@NotNull Specification<Scenario> spec, @NotNull Pageable pageable);

  // -- TEAM --

  @Modifying
  @Query(
      value =
          "DELETE FROM scenarios_teams st WHERE st.scenario_id = :scenarioId AND st.team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void removeTeams(
      @Param("scenarioId") final String scenarioId, @Param("teamIds") final List<String> teamIds);

  Optional<Scenario> findByExercises_Id(String exerciseId);
}
