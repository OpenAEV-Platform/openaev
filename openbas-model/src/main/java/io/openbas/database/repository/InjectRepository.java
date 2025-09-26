package io.openbas.database.repository;

import io.openbas.database.model.Inject;
import io.openbas.database.raw.RawInject;
import io.openbas.database.raw.RawInjectIndexing;
import io.openbas.utils.Constants;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface InjectRepository
    extends JpaRepository<Inject, String>, JpaSpecificationExecutor<Inject>, StatisticRepository {

  @NotNull
  Optional<Inject> findById(@NotNull String id);

  List<Inject> findByExerciseId(@NotNull String exerciseId);

  Set<Inject> findByScenarioId(@NotNull String scenarioId);

  @NotNull
  Optional<Inject> findWithStatusById(@NotNull String id);

  @Query(
      value =
          "SELECT f.inject_id, f.inject_title, f.inject_scenario, f.inject_exercise, f.inject_created_at, f.inject_updated_at, f.inject_injector_contract, ic.injector_contract_updated_at, ins.tracking_sent_date, "
              + "array_union_agg(ic.injector_contract_platforms) FILTER ( WHERE ic.injector_contract_platforms IS NOT NULL ) as inject_platforms, "
              + "array_agg(icap.attack_pattern_id) FILTER ( WHERE icap.attack_pattern_id IS NOT NULL ) as inject_attack_patterns, "
              + "array_agg(ap.phase_id) FILTER ( WHERE ap.phase_id IS NOT NULL ) as inject_kill_chain_phases, "
              + "array_agg(idp.inject_children_id) FILTER ( WHERE idp.inject_children_id IS NOT NULL ) as inject_children, "
              + "array_agg(idp.inject_children_id) FILTER ( WHERE idp.inject_children_id IS NOT NULL ) as attack_pattern_children, "
              + "array_agg(icap_children.attack_pattern_id) FILTER (WHERE icap_children.attack_pattern_id IS NOT NULL) AS attack_patterns_children,"
              + "MAX(ins.status_name) as inject_status_name, "
              + "array_agg(it.tag_id) FILTER ( WHERE it.tag_id IS NOT NULL ) as inject_tags, "
              + "array_agg(ia.asset_id) FILTER ( WHERE ia.asset_id IS NOT NULL ) as inject_assets, "
              + "array_agg(iag.asset_group_id) FILTER ( WHERE iag.asset_group_id IS NOT NULL ) as inject_asset_groups, "
              + "array_agg(ite.team_id) FILTER ( WHERE ite.team_id IS NOT NULL ) || "
              + "array_agg(et.team_id) FILTER ( WHERE et.team_id IS NOT NULL ) || "
              + "array_agg(st.team_id) FILTER ( WHERE st.team_id IS NOT NULL ) as inject_teams " // The deduplication is not done here but in the Set<String> of RawInjectIndexing
              + "FROM injects f "
              + "LEFT JOIN injects_statuses ins ON ins.status_inject = f.inject_id "
              + "LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = f.inject_injector_contract "
              + "LEFT JOIN injectors_contracts_attack_patterns icap ON icap.injector_contract_id = ic.injector_contract_id "
              + "LEFT JOIN attack_patterns_kill_chain_phases ap ON ap.attack_pattern_id = icap.attack_pattern_id "
              + "LEFT JOIN injects_dependencies idp ON idp.inject_parent_id = f.inject_id "
              + "LEFT JOIN injects inject_children ON inject_children.inject_id = idp.inject_children_id "
              + "LEFT JOIN injectors_contracts ic_children ON ic_children.injector_contract_id = inject_children.inject_injector_contract "
              + "LEFT JOIN injectors_contracts_attack_patterns icap_children ON icap_children.injector_contract_id = ic_children.injector_contract_id "
              + "LEFT JOIN injects_tags it ON it.inject_id = f.inject_id "
              + "LEFT JOIN injects_assets ia ON ia.inject_id = f.inject_id "
              + "LEFT JOIN injects_asset_groups iag ON iag.inject_id = f.inject_id "
              + "LEFT JOIN injects_teams ite ON ite.inject_id = f.inject_id "
              + "LEFT JOIN exercises_teams et ON et.exercise_id = f.inject_exercise AND f.inject_all_teams "
              + "LEFT JOIN scenarios_teams st ON st.scenario_id = f.inject_scenario AND f.inject_all_teams "
              + "WHERE f.inject_updated_at > :from "
              + "OR ic.injector_contract_updated_at > :from  "
              + "OR EXISTS ("
              + "    SELECT 1 "
              + "    FROM injects_dependencies sub_idp "
              + "    WHERE sub_idp.inject_parent_id = f.inject_id "
              + "      AND sub_idp.dependency_updated_at > :from"
              + ")"
              + "OR EXISTS ("
              + "    SELECT 1 "
              + "    FROM injectors_contracts sub_ic "
              + "    WHERE sub_ic.injector_contract_id = inject_children.inject_injector_contract "
              + "      AND sub_ic.injector_contract_updated_at > :from "
              + ")"
              + "GROUP BY f.inject_id, f.inject_updated_at, ic.injector_contract_updated_at, ins.tracking_sent_date ORDER BY GREATEST(f.inject_updated_at, ic.injector_contract_updated_at) ASC LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawInjectIndexing> findForIndexing(@Param("from") Instant from);

  @Query(
      value =
          "select i.* from injects i where i.inject_injector_contract = '49229430-b5b5-431f-ba5b-f36f599b0233'"
              + " and i.inject_content like :challengeId",
      nativeQuery = true)
  List<Inject> findAllForChallengeId(@Param("challengeId") String challengeId);

  @Query(
      value =
          "select i from Inject i "
              + "join i.documents as doc_rel "
              + "join doc_rel.document as doc "
              + "where doc.id = :documentId and i.exercise.id = :exerciseId")
  List<Inject> findAllForExerciseAndDoc(
      @Param("exerciseId") String exerciseId, @Param("documentId") String documentId);

  @Query(
      value =
          "select i from Inject i "
              + "join i.documents as doc_rel "
              + "join doc_rel.document as doc "
              + "where doc.id = :documentId and i.scenario.id = :scenarioId")
  List<Inject> findAllForScenarioAndDoc(
      @Param("scenarioId") String scenarioId, @Param("documentId") String documentId);

  @Modifying
  @Query(
      value =
          "insert into injects (inject_id, inject_title, inject_description, inject_country, inject_city,"
              + "inject_injector_contract, inject_all_teams, inject_enabled, inject_exercise, "
              + "inject_depends_duration, inject_content) "
              + "values (:id, :title, :description, :country, :city, :contract, :allTeams, :enabled, :exercise, :dependsDuration, :content)",
      nativeQuery = true)
  void importSaveForExercise(
      @Param("id") String id,
      @Param("title") String title,
      @Param("description") String description,
      @Param("country") String country,
      @Param("city") String city,
      @Param("contract") String contract,
      @Param("allTeams") boolean allTeams,
      @Param("enabled") boolean enabled,
      @Param("exercise") String exerciseId,
      @Param("dependsDuration") Long dependsDuration,
      @Param("content") String content);

  @Modifying
  @Query(
      value =
          "insert into injects (inject_id, inject_title, inject_description, inject_country, inject_city,"
              + "inject_injector_contract, inject_all_teams, inject_enabled, inject_scenario, "
              + "inject_depends_duration, inject_content) "
              + "values (:id, :title, :description, :country, :city, :contract, :allTeams, :enabled, :scenario, :dependsDuration, :content)",
      nativeQuery = true)
  void importSaveForScenario(
      @Param("id") String id,
      @Param("title") String title,
      @Param("description") String description,
      @Param("country") String country,
      @Param("city") String city,
      @Param("contract") String contract,
      @Param("allTeams") boolean allTeams,
      @Param("enabled") boolean enabled,
      @Param("scenario") String scenarioId,
      @Param("dependsDuration") Long dependsDuration,
      @Param("content") String content);

  @Modifying
  @Query(
      value =
          "insert into injects (inject_id, inject_title, inject_description, inject_country, inject_city,"
              + "inject_injector_contract, inject_all_teams, inject_enabled, "
              + "inject_depends_duration, inject_content) "
              + "values (:id, :title, :description, :country, :city, :contract, :allTeams, :enabled, :dependsDuration, :content)",
      nativeQuery = true)
  void importSaveStandAlone(
      @Param("id") String id,
      @Param("title") String title,
      @Param("description") String description,
      @Param("country") String country,
      @Param("city") String city,
      @Param("contract") String contract,
      @Param("allTeams") boolean allTeams,
      @Param("enabled") boolean enabled,
      @Param("dependsDuration") Long dependsDuration,
      @Param("content") String content);

  @Modifying
  @Query(
      value = "insert into injects_tags (inject_id, tag_id) values (:injectId, :tagId)",
      nativeQuery = true)
  void addTag(@Param("injectId") String injectId, @Param("tagId") String tagId);

  @Modifying
  @Query(
      value = "insert into injects_teams (inject_id, team_id) values (:injectId, :teamId)",
      nativeQuery = true)
  void addTeam(@Param("injectId") String injectId, @Param("teamId") String teamId);

  @Override
  @Query(
      "select count(distinct i) from Inject i "
          + "join i.exercise as e "
          + "join e.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and i.createdAt > :creationDate")
  long userCount(@Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Override
  @Query("select count(distinct i) from Inject i where i.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      value =
          "select icap.attack_pattern_id, count(distinct i) as countInjects from injects i "
              + "join injectors_contracts_attack_patterns icap ON icap.injector_contract_id = i.inject_injector_contract "
              + "join exercises e ON e.exercise_id = i.inject_exercise "
              + "join injects_statuses injectStatus ON injectStatus.status_inject = i.inject_id "
              + "where i.inject_created_at > :creationDate and i.inject_exercise is not null and e.exercise_start_date is not null and icap.injector_contract_id is not null and injectStatus.status_name = 'SUCCESS'"
              + "group by icap.attack_pattern_id order by countInjects DESC LIMIT 5",
      nativeQuery = true)
  List<Object[]> globalCountGroupByAttackPatternInExercise(
      @Param("creationDate") Instant creationDate);

  @Query(
      value =
          "select icap.attack_pattern_id, count(distinct i) as countInjects from injects i "
              + "join injectors_contracts_attack_patterns icap ON icap.injector_contract_id = i.inject_injector_contract "
              + "join exercises e on e.exercise_id = i.inject_exercise "
              + "inner join grants ON grants.grant_resource = e.exercise_id AND grants.grant_resource_type = 'SIMULATION' "
              + "inner join groups ON grants.grant_group = groups.group_id "
              + "inner join users_groups ON groups.group_id = users_groups.group_id "
              + "join injects_statuses injectStatus ON injectStatus.status_inject = i.inject_id "
              + "where users_groups.user_id = :userId and i.inject_created_at > :creationDate and i.inject_exercise is not null and e.exercise_start_date is not null and icap.injector_contract_id is not null and injectStatus.status_name = 'SUCCESS'"
              + "group by icap.attack_pattern_id order by countInjects DESC LIMIT 5",
      nativeQuery = true)
  List<Object[]> userCountGroupByAttackPatternInExercise(
      @Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Query(
      value =
          " SELECT injects.inject_id, ins.status_name, injects.inject_scenario, "
              + "coalesce(array_agg(it.team_id) FILTER ( WHERE it.team_id IS NOT NULL ), '{}') as inject_teams, "
              + "coalesce(array_agg(assets.asset_id) FILTER ( WHERE assets.asset_id IS NOT NULL ), '{}') as inject_assets, "
              + "coalesce(array_agg(iag.asset_group_id) FILTER ( WHERE iag.asset_group_id IS NOT NULL ), '{}') as inject_asset_groups, "
              + "coalesce(array_agg(ie.inject_expectation_id) FILTER ( WHERE ie.inject_expectation_id IS NOT NULL ), '{}') as inject_expectations, "
              + "coalesce(array_agg(com.communication_id) FILTER ( WHERE com.communication_id IS NOT NULL ), '{}') as inject_communications, "
              + "coalesce(array_agg(apkcp.phase_id) FILTER ( WHERE apkcp.phase_id IS NOT NULL ), '{}') as inject_kill_chain_phases, "
              + "coalesce(array_union_agg(injcon.injector_contract_platforms) FILTER ( WHERE injcon.injector_contract_platforms IS NOT NULL ), '{}') as inject_platforms "
              + "FROM injects "
              + "LEFT JOIN injects_teams it ON injects.inject_id = it.inject_id "
              + "LEFT JOIN injects_assets ia ON injects.inject_id = ia.inject_id "
              + "LEFT JOIN injects_asset_groups iag ON injects.inject_id = iag.inject_id "
              + "LEFT JOIN asset_groups_assets aga ON aga.asset_group_id = iag.asset_group_id "
              + "LEFT JOIN assets ON assets.asset_id = ia.asset_id OR aga.asset_id = assets.asset_id "
              + "LEFT JOIN communications com ON com.communication_inject = injects.inject_id "
              + "LEFT JOIN injects_expectations ie ON injects.inject_id = ie.inject_id "
              + "LEFT JOIN injectors_contracts_attack_patterns icap ON icap.injector_contract_id = injects.inject_injector_contract "
              + "LEFT JOIN injectors_contracts injcon ON injcon.injector_contract_id = injects.inject_injector_contract "
              + "LEFT JOIN attack_patterns_kill_chain_phases apkcp ON apkcp.attack_pattern_id = icap.attack_pattern_id "
              + "LEFT JOIN injects_statuses ins ON ins.status_inject = injects.inject_id "
              + "WHERE injects.inject_id IN :ids "
              + "GROUP BY injects.inject_id, ins.status_name;",
      nativeQuery = true)
  List<RawInject> findRawByIds(@Param("ids") List<String> ids);

  @Query(
      value =
          " SELECT injects.inject_id, "
              + "coalesce(array_agg(it.team_id) FILTER ( WHERE it.team_id IS NOT NULL ), '{}') as inject_teams "
              + "FROM injects "
              + "LEFT JOIN injects_teams it ON injects.inject_id = it.inject_id "
              + "WHERE injects.inject_id IN :ids AND it.team_id = :teamId "
              + "GROUP BY injects.inject_id",
      nativeQuery = true)
  Set<RawInject> findRawInjectTeams(
      @Param("ids") Collection<String> ids, @Param("teamId") String teamId);

  @Query(
      value =
          "SELECT org.*, "
              + "array_agg(DISTINCT org_tags.tag_id) FILTER (WHERE org_tags.tag_id IS NOT NULL) AS organization_tags, "
              + "array_agg(DISTINCT injects.inject_id) FILTER (WHERE injects.inject_id IS NOT NULL) AS organization_injects, "
              + "coalesce(array_length(array_agg(DISTINCT injects.inject_id) FILTER (WHERE injects.inject_id IS NOT NULL), 1), 0) AS organization_injects_number "
              + "FROM organizations org "
              + "LEFT JOIN organizations_tags org_tags ON org.organization_id = org_tags.organization_id "
              + "LEFT JOIN users ON users.user_organization = org.organization_id "
              + "LEFT JOIN users_teams ON users.user_id = users_teams.user_id "
              + "LEFT JOIN injects_teams ON injects_teams.team_id = users_teams.team_id "
              + "LEFT JOIN injects ON injects.inject_id = injects_teams.inject_id OR injects.inject_all_teams "
              + "GROUP BY org.organization_id",
      nativeQuery = true)
  List<RawInject> rawAll();

  // -- TEAM --

  @Modifying
  @Query(
      value =
          "DELETE FROM injects_teams it "
              + "WHERE it.team_id IN :teamIds "
              + "AND EXISTS (SELECT 1 FROM injects i WHERE it.inject_id = i.inject_id AND i.inject_exercise = :exerciseId)",
      nativeQuery = true)
  @Transactional
  void removeTeamsForExercise(
      @Param("exerciseId") final String exerciseId, @Param("teamIds") final List<String> teamIds);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects_teams it "
              + "WHERE it.team_id IN :teamIds "
              + "AND EXISTS (SELECT 1 FROM injects i WHERE it.inject_id = i.inject_id AND i.inject_scenario = :scenarioId)",
      nativeQuery = true)
  @Transactional
  void removeTeamsForScenario(
      @Param("scenarioId") final String scenarioId, @Param("teamIds") final List<String> teamIds);

  @Query(
      value =
          """
    SELECT DISTINCT i.inject_id AS id, i.inject_title AS label, i.inject_created_at
    FROM injects i
    INNER JOIN findings f ON f.finding_inject_id = i.inject_id
    WHERE (:title IS NULL OR LOWER(i.inject_title) LIKE LOWER(CONCAT('%', COALESCE(:title, ''), '%')))
      ORDER BY i.inject_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllByTitleLinkedToFindings(@Param("title") String title, Pageable pageable);

  @Query(
      value =
          """
    SELECT DISTINCT i.inject_id AS id, i.inject_title AS label, i.inject_created_at
    FROM injects i
    INNER JOIN findings f ON f.finding_inject_id = i.inject_id
    LEFT JOIN findings_assets fa ON fa.finding_id = f.finding_id
    LEFT JOIN scenarios_exercises se ON se.exercise_id = i.inject_exercise
    WHERE (i.inject_exercise = :sourceId OR se.scenario_id = :sourceId OR fa.asset_id = :sourceId)
      AND (:title IS NULL OR LOWER(i.inject_title) LIKE LOWER(CONCAT('%', COALESCE(:title, ''), '%')))
      ORDER BY i.inject_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllByTitleLinkedToFindingsWithContext(
      @Param("sourceId") String sourceId, @Param("title") String title, Pageable pageable);

  @Query(
      value = "SELECT i.inject_content FROM injects i WHERE i.inject_id IN :injectIds",
      nativeQuery = true)
  List<String> findContentsByInjectIds(@NotBlank Set<String> injectIds);

  /**
   * Check if an Inject exists by its ID without loading the entity. This is useful for because of
   * the cascade configuration
   *
   * @param id the ID of the Inject to check
   * @return true if the Inject exists, false otherwise
   */
  @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Inject i WHERE i.id = :id")
  boolean existsByIdWithoutLoading(@Param("id") String id);

  /**
   * Check if an Inject exists by its ID, where the Inject is an atomic testing.
   *
   * @param id ID of the inject to check
   * @return true if the Inject exists and is an atomic testing, false otherwise
   */
  boolean existsByIdAndScenarioIsNullAndExerciseIsNull(String id);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects i "
              + "USING injectors_contracts ic, injectors_contracts_vulnerabilities icv "
              + "WHERE i.inject_injector_contract = ic.injector_contract_id "
              + "AND ic.injector_contract_id = icv.injector_contract_id "
              + "AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  void deleteAllInjectsWithVulnerableContractsByScenarioId(@Param("scenarioId") String scenarioId);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects i "
              + "USING injectors_contracts ic, injectors_contracts_attack_patterns icap "
              + "WHERE i.inject_injector_contract = ic.injector_contract_id "
              + "AND ic.injector_contract_id = icap.injector_contract_id "
              + "AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  void deleteAllInjectsWithAttackPatternContractsByScenarioId(
      @Param("scenarioId") String scenarioId);

  @Modifying
  @Query(
      value =
          "DELETE FROM injects i WHERE i.inject_injector_contract = :injectorContract AND i.inject_scenario = :scenarioId",
      nativeQuery = true)
  void deleteAllByScenarioIdAndInjectorContract(String injectorContract, String scenarioId);
}
