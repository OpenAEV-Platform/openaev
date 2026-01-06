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
          "SELECT sce.scenario_id, "
              + "coalesce(array_agg(inj.inject_id) FILTER (WHERE inj.inject_id IS NOT NULL), '{}') as scenario_injects "
              + "FROM scenarios sce "
              + "LEFT JOIN injects inj ON inj.inject_scenario = sce.scenario_id "
              + "WHERE sce.scenario_id IN :ids "
              + "GROUP BY sce.scenario_id",
      nativeQuery = true)
  List<RawScenarioSimple> rawInjectsFromScenarios(@Param("ids") List<String> ids);

  @Query(
      value =
          "SELECT s.*, COUNT(DISTINCT ut.*) as scenario_all_users_number, COUNT(DISTINCT stu.*) as scenario_users_number, "
              + "array_agg(DISTINCT se.exercise_id) FILTER (WHERE se.exercise_id IS NOT NULL) as scenario_exercises, "
              + "json_agg(DISTINCT kcp.*) FILTER (WHERE kcp.phase_id IS NOT NULL) as scenario_kill_chain_phases, "
              + "array_union_agg(ic.injector_contract_platforms) FILTER ( WHERE ic.injector_contract_platforms IS NOT NULL ) as scenario_platforms, "
              + "array_agg(DISTINCT sta.tag_id) FILTER (WHERE sta.tag_id IS NOT NULL) as scenario_tags, "
              + "json_agg(DISTINCT stu.*) FILTER (WHERE stu.scenario_id IS NOT NULL) as scenario_teams_users "
              + "FROM scenarios s "
              + "LEFT JOIN scenarios_teams st ON st.scenario_id = s.scenario_id "
              + "LEFT JOIN users_teams ut ON st.team_id = ut.team_id "
              + "LEFT JOIN scenarios_exercises se ON se.scenario_id = s.scenario_id "
              + "LEFT JOIN injects i ON s.scenario_id = i.inject_scenario "
              + "LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = i.inject_injector_contract "
              + "LEFT JOIN injectors_contracts_attack_patterns icap ON ic.injector_contract_id = icap.injector_contract_id "
              + "LEFT JOIN attack_patterns_kill_chain_phases apkcp ON icap.attack_pattern_id = apkcp.attack_pattern_id "
              + "LEFT JOIN kill_chain_phases kcp ON kcp.phase_id = apkcp.phase_id "
              + "LEFT JOIN scenarios_tags sta ON sta.scenario_id = s.scenario_id "
              + "LEFT JOIN scenarios_teams_users stu ON stu.scenario_id = s.scenario_id "
              + "WHERE s.scenario_id = :scenarioId "
              + "GROUP BY s.scenario_id",
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
