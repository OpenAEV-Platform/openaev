package io.openaev.database.repository;

import io.openaev.database.model.Exercise;
import io.openaev.database.raw.*;
import io.openaev.utils.Constants;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ExerciseRepository
    extends JpaRepository<Exercise, String>,
        StatisticRepository,
        JpaSpecificationExecutor<Exercise> {

  @NotNull
  Optional<Exercise> findById(@NotNull String id);

  @Query(value = "select e from Exercise e where e.status = 'SCHEDULED' and e.start <= :start")
  List<Exercise> findAllShouldBeInRunningState(@Param("start") Instant start);

  @Override
  @Query(
      "select count(distinct e) from Exercise e "
          + "join e.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and e.createdAt > :creationDate")
  long userCount(@Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Override
  @Query("select count(distinct e) from Exercise e where e.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      value =
          "select e.*, se.scenario_id from exercises e "
              + "left join injects as inject on e.exercise_id = inject.inject_exercise and inject.inject_enabled = 'true' "
              + "left join injects_statuses as status on inject.inject_id = status.status_inject and status.status_name not in ('PENDING', 'QUEUING', 'EXECUTING')"
              + "left join scenarios_exercises as se on e.exercise_id = se.exercise_id "
              + "where e.exercise_status = 'RUNNING' group by e.exercise_id, se.scenario_id having count(status) = count(inject) "
              + "and count(inject) filter (where inject.inject_collect_status = 'COMPLETED' or status.status_name <> 'ERROR') = count(inject) filter (where status.status_name <> 'ERROR');",
      nativeQuery = true)
  List<Exercise> thatMustBeFinished();

  @Query(
      """
    SELECT e FROM Exercise e, Scenario s
    WHERE e.scenario.id = s.id AND s.id = :#{#exercise.scenario.id}
      AND e.launchOrder > :#{#exercise.launchOrder}
      AND e.id != :#{#exercise.id}
    ORDER BY e.launchOrder ASC LIMIT 1
    """)
  Optional<Exercise> following(@Param("exercise") Exercise exercise);

  /**
   * Get the raw version of the exercises
   *
   * @return the list of exercises
   */
  @Query(
      value =
          " SELECT ex.exercise_id, "
              + "ex.exercise_status, "
              + "ex.exercise_start_date, "
              + "ex.exercise_updated_at, "
              + "ex.exercise_end_date, "
              + "ex.exercise_name, "
              + "ex.exercise_category, "
              + "ex.exercise_subtitle, "
              + " array_agg(distinct ie.inject_id) FILTER ( WHERE ie.inject_id IS NOT NULL ) as inject_ids, "
              + " array_agg(distinct et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as exercise_tags "
              + "FROM exercises ex "
              + "LEFT JOIN injects_expectations ie ON ex.exercise_id = ie.exercise_id "
              + "LEFT JOIN exercises_tags et ON et.exercise_id = ex.exercise_id "
              + "GROUP BY ex.exercise_id ;",
      nativeQuery = true)
  List<RawExerciseSimple> rawAll();

  /**
   * Get the raw version of the exercises in the list in param
   *
   * @param exerciseIds the list of exercise ids
   * @return the list of exercises
   */
  @Query(
      value =
          " SELECT ex.exercise_id, "
              + "ex.exercise_status, "
              + "ex.exercise_start_date, "
              + "ex.exercise_updated_at, "
              + "ex.exercise_end_date, "
              + "ex.exercise_name, "
              + "ex.exercise_category, "
              + "ex.exercise_subtitle, "
              + " array_agg(distinct ie.inject_id) FILTER ( WHERE ie.inject_id IS NOT NULL ) as inject_ids, "
              + " array_agg(distinct et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as exercise_tags "
              + "FROM exercises ex "
              + "LEFT JOIN injects_expectations ie ON ex.exercise_id = ie.exercise_id "
              + "LEFT JOIN exercises_tags et ON et.exercise_id = ex.exercise_id "
              + "WHERE ex.exercise_id IN (:exerciseIds) "
              + "GROUP BY ex.exercise_id ;",
      nativeQuery = true)
  List<RawExerciseSimple> rawByExerciseIds(List<String> exerciseIds);

  /**
   * Get the raw version of the exercises a user can see
   *
   * @param userId the id of the user
   * @return the list of exercises
   */
  @Query(
      value =
          " SELECT ex.exercise_id, "
              + "ex.exercise_status, "
              + "ex.exercise_start_date, "
              + "ex.exercise_updated_at, "
              + "ex.exercise_end_date, "
              + "ex.exercise_name, "
              + "ex.exercise_category, "
              + "ex.exercise_subtitle, "
              + " array_agg(et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as exercise_tags, "
              + " array_agg(injects.inject_id) FILTER ( WHERE injects.inject_id IS NOT NULL ) as inject_ids "
              + "FROM exercises ex "
              + "LEFT JOIN injects_expectations ie ON ex.exercise_id = ie.exercise_id "
              + "LEFT JOIN injects ON ie.inject_id = injects.inject_id "
              + "LEFT JOIN exercises_tags et ON et.exercise_id = ex.exercise_id "
              + "INNER JOIN grants ON grants.grant_resource = ex.exercise_id AND grants.grant_resource_type = 'SIMULATION' "
              + "INNER JOIN groups ON grants.grant_group = groups.group_id "
              + "INNER JOIN users_groups ON groups.group_id = users_groups.group_id "
              + "WHERE users_groups.user_id = :userId "
              + "GROUP BY ex.exercise_id ;",
      nativeQuery = true)
  List<RawExerciseSimple> rawAllGranted(@Param("userId") String userId);

  /**
   * Get the raw version of the exercises a user can see by exercise ids
   *
   * @param userId the id of the user
   * @param exerciseIds the list of exercise ids
   * @return the list of exercises
   */
  @Query(
      value =
          " SELECT ex.exercise_id, "
              + "ex.exercise_status, "
              + "ex.exercise_start_date, "
              + "ex.exercise_updated_at, "
              + "ex.exercise_end_date, "
              + "ex.exercise_name, "
              + "ex.exercise_category, "
              + "ex.exercise_subtitle, "
              + " array_agg(et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as exercise_tags, "
              + " array_agg(injects.inject_id) FILTER ( WHERE injects.inject_id IS NOT NULL ) as inject_ids "
              + "FROM exercises ex "
              + "LEFT JOIN injects_expectations ie ON ex.exercise_id = ie.exercise_id "
              + "LEFT JOIN injects ON ie.inject_id = injects.inject_id "
              + "LEFT JOIN exercises_tags et ON et.exercise_id = ex.exercise_id "
              + "INNER JOIN grants ON grants.grant_resource = ex.exercise_id AND grants.grant_resource_type = 'SIMULATION' "
              + "INNER JOIN groups ON grants.grant_group = groups.group_id "
              + "INNER JOIN users_groups ON groups.group_id = users_groups.group_id "
              + "WHERE users_groups.user_id = :userId "
              + "AND ex.exercise_id IN (:exerciseIds) "
              + "GROUP BY ex.exercise_id ;",
      nativeQuery = true)
  List<RawExerciseSimple> rawGrantedByExerciseIds(
      @Param("userId") String userId, @Param("exerciseIds") List<String> exerciseIds);

  /**
   * Get the raw version of the exercises a user can see
   *
   * @param exerciseId the id of the user
   * @return the list of exercises
   */
  @Query(
      value =
          " SELECT ex.exercise_id, ex.exercise_name, ex.exercise_description, ex.exercise_status, ex.exercise_subtitle, "
              + "ex.exercise_category, ex.exercise_main_focus, ex.exercise_severity, ex.exercise_start_date, "
              + "ex.exercise_end_date, ex.exercise_message_header, ex.exercise_message_footer, ex.exercise_mail_from, "
              + "ex.exercise_lessons_anonymized, ex.exercise_custom_dashboard, ex.exercise_created_at, ex.exercise_updated_at, "
              + "se.scenario_id, inj.inject_scenario, "
              + " coalesce(array_agg(emrt.exercise_reply_to) FILTER ( WHERE emrt.exercise_reply_to IS NOT NULL ), '{}') as exercise_reply_to, "
              + " coalesce(array_agg(et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ), '{}') as exercise_tags, "
              + " coalesce(array_agg(ut.user_id) FILTER ( WHERE ut.user_id IS NOT NULL ), '{}') as exercise_users, "
              + " coalesce(array_agg(la.lessons_answer_id) FILTER ( WHERE la.lessons_answer_id IS NOT NULL ), '{}') as lessons_answers, "
              + " coalesce(array_agg(logs.log_id) FILTER ( WHERE logs.log_id IS NOT NULL ), '{}') as logs, "
              + " coalesce(array_agg(inj.inject_id) FILTER ( WHERE inj.inject_id IS NOT NULL ), '{}') as inject_ids "
              + "FROM exercises ex "
              + "LEFT JOIN scenarios_exercises se ON se.exercise_id = ex.exercise_id "
              + "LEFT JOIN exercise_mails_reply_to emrt ON emrt.exercise_id = ex.exercise_id "
              + "LEFT JOIN exercises_tags et ON et.exercise_id = ex.exercise_id "
              + "LEFT JOIN exercises_teams ext ON ext.exercise_id = ex.exercise_id "
              + "LEFT JOIN users_teams ut ON ext.team_id = ut.team_id "
              + "LEFT JOIN lessons_categories lc ON lc.lessons_category_exercise = ex.exercise_id "
              + "LEFT JOIN lessons_questions lq ON lq.lessons_question_category = lc.lessons_category_id "
              + "LEFT JOIN lessons_answers la ON la.lessons_answer_question = lq.lessons_question_id "
              + "LEFT JOIN logs ON logs.log_exercise = ex.exercise_id "
              + "LEFT JOIN injects inj ON ex.exercise_id = inj.inject_exercise "
              + "WHERE ex.exercise_id = :exerciseId "
              + "GROUP BY ex.exercise_id, inj.inject_scenario, se.scenario_id ;",
      nativeQuery = true)
  RawSimulation rawDetailsById(@Param("exerciseId") String exerciseId);

  @Query(
      value =
          " SELECT DISTINCT (ie.inject_id) "
              + "FROM exercises ex "
              + "LEFT JOIN injects_expectations ie ON ex.exercise_id = ie.exercise_id "
              + "WHERE ex.exercise_id = :exerciseId AND ie.inject_id IS NOT NULL;",
      nativeQuery = true)
  Set<String> findInjectsByExercise(@Param("exerciseId") String exerciseId);

  @Query(
      value =
          " SELECT ex.exercise_id, "
              + "ex.exercise_status, "
              + "ex.exercise_start_date, "
              + "ex.exercise_updated_at, "
              + "ex.exercise_end_date, "
              + "ex.exercise_name, "
              + "ex.exercise_category, "
              + "ex.exercise_subtitle, "
              + " array_agg(distinct ie.inject_id) FILTER ( WHERE ie.inject_id IS NOT NULL ) as inject_ids, "
              + " array_agg(distinct et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as exercise_tags "
              + "FROM exercises ex "
              + "LEFT JOIN scenarios_exercises s ON s.exercise_id = ex.exercise_id "
              + "LEFT JOIN exercises_tags et ON et.exercise_id = ex.exercise_id "
              + "LEFT JOIN injects_expectations ie ON ex.exercise_id = ie.exercise_id "
              + "WHERE s.scenario_id IN (:scenarioIds) "
              + "GROUP BY ex.exercise_id ;",
      nativeQuery = true)
  List<RawExerciseSimple> rawAllByScenarioIds(@Param("scenarioIds") List<String> scenarioIds);

  // -- TEAM --

  @Modifying
  @Query(
      value =
          "DELETE FROM exercises_teams et WHERE et.exercise_id = :exerciseId AND et.team_id in :teamIds",
      nativeQuery = true)
  @Transactional
  void removeTeams(
      @Param("exerciseId") final String exerciseId, @Param("teamIds") final List<String> teamIds);

  @Query(
      value =
          " SELECT ex.exercise_id, ex.exercise_end_date, "
              + " array_agg(distinct ie.inject_id) FILTER ( WHERE ie.inject_id IS NOT NULL ) as inject_ids "
              + "FROM exercises ex "
              + "LEFT JOIN scenarios_exercises s ON s.exercise_id = ex.exercise_id "
              + "LEFT JOIN injects_expectations ie ON ex.exercise_id = ie.exercise_id "
              + "WHERE s.scenario_id = :scenarioId "
              + "AND ex.exercise_status = 'FINISHED' "
              + "AND ex.exercise_end_date IS NOT NULL "
              + "GROUP BY ex.exercise_id, ex.exercise_end_date "
              + "ORDER BY ex.exercise_end_date DESC "
              + "LIMIT 10 ;",
      nativeQuery = true)
  List<RawFinishedExerciseWithInjects> rawLatestFinishedExercisesWithInjectsByScenarioId(
      @Param("scenarioId") String scenarioId);

  @Query(
      value =
          """
        SELECT DISTINCT e.exercise_id AS id, e.exercise_name AS label, e.exercise_created_at
        FROM injects i
        INNER JOIN findings f ON f.finding_inject_id = i.inject_id
        INNER JOIN exercises e ON i.inject_exercise = e.exercise_id
        WHERE (:name IS NULL OR LOWER(e.exercise_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')))
        ORDER BY e.exercise_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllOptionByNameLinkedToFindings(@Param("name") String name, Pageable pageable);

  @Query(
      value =
          """
        SELECT DISTINCT e.exercise_id AS id, e.exercise_name AS label, e.exercise_created_at
        FROM injects i
        INNER JOIN findings f ON f.finding_inject_id = i.inject_id
        LEFT JOIN findings_assets fa ON fa.finding_id = f.finding_id
        LEFT JOIN exercises e ON i.inject_exercise = e.exercise_id
        LEFT JOIN scenarios_exercises se ON se.exercise_id = e.exercise_id
        WHERE (se.scenario_id = :sourceId OR fa.asset_id = :sourceId)
        AND (:name IS NULL OR LOWER(e.exercise_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')))
        ORDER BY e.exercise_created_at DESC;
    """,
      nativeQuery = true)
  List<Object[]> findAllOptionByNameLinkedToFindingsWithContext(
      @Param("sourceId") String sourceId, @Param("name") String name, Pageable pageable);

  // -- INDEXING --

  @Query(
      value =
          "WITH exercise_data AS ("
              + "SELECT ex.exercise_id, ex.exercise_name, ex.exercise_status, ex.exercise_start_date, ex.exercise_created_at, MAX(se.scenario_id) AS scenario_id, " // MAX here is used to get 1 element and not a list because we know that 1 exercise is linked to only 1 scenario
              + "GREATEST(ex.exercise_updated_at, max(inj.inject_updated_at), max(ic.injector_contract_updated_at)) as exercise_injects_updated_at, "
              + "array_agg(DISTINCT et.tag_id) FILTER ( WHERE et.tag_id IS NOT NULL ) as exercise_tags, "
              + "array_agg(DISTINCT ete.team_id) FILTER ( WHERE ete.team_id IS NOT NULL ) as exercise_teams, "
              + "array_agg(DISTINCT ia.asset_id) FILTER ( WHERE ia.asset_id IS NOT NULL ) as exercise_assets, "
              + "array_agg(DISTINCT iag.asset_group_id) FILTER ( WHERE iag.asset_group_id IS NOT NULL ) as exercise_asset_groups, "
              + "array_union_agg(ic.injector_contract_platforms) FILTER ( WHERE ic.injector_contract_platforms IS NOT NULL ) as exercise_platforms "
              + "FROM exercises ex "
              + "LEFT JOIN exercises_tags et ON et.exercise_id = ex.exercise_id "
              + "LEFT JOIN exercises_teams ete ON ete.exercise_id = ex.exercise_id "
              + "LEFT JOIN injects inj ON ex.exercise_id = inj.inject_exercise "
              + "LEFT JOIN injects_assets ia ON ia.inject_id = inj.inject_id "
              + "LEFT JOIN injects_asset_groups iag ON iag.inject_id = inj.inject_id "
              + "LEFT JOIN scenarios_exercises se ON ex.exercise_id = se.exercise_id "
              + "LEFT JOIN injectors_contracts ic ON ic.injector_contract_id = inj.inject_injector_contract "
              + "GROUP BY ex.exercise_id, ex.exercise_name, ex.exercise_created_at, ex.exercise_updated_at"
              + ") "
              + "SELECT * FROM exercise_data ed "
              + "WHERE ed.exercise_injects_updated_at > :from "
              + "ORDER BY ed.exercise_injects_updated_at ASC LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawSimulation> findForIndexing(@Param("from") Instant from);
}
