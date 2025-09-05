package io.openbas.database.repository;

import io.openbas.database.model.Document;
import io.openbas.database.raw.RawDocument;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository
    extends CrudRepository<Document, String>, JpaSpecificationExecutor<Document> {

  @NotNull
  Optional<Document> findById(@NotNull String id);

  List<Document> removeById(@NotNull String id);

  @NotNull
  Optional<Document> findByTarget(@NotNull String target);

  @NotNull
  Optional<Document> findByName(@NotNull String name);

  @Query(
      "select d from Document d "
          + "join d.exercises as exercise "
          + "join exercise.grants as grant "
          + "join grant.group as g "
          + "join g.users as user "
          + "where d.id = :id and user.id = :userId")
  Optional<Document> findByIdGranted(
      @Param("id") String documentId, @Param("userId") String userId);

  @Query(
      value =
          "select d.*, "
              + "array_remove(array_agg(tg.tag_id), NULL) as document_tags, "
              + "array_remove(array_agg(ex.exercise_id), NULL) as document_exercises, "
              + "array_remove(array_agg(sc.scenario_id), NULL) as document_scenarios "
              + "from documents d left join exercises_documents exdoc on d.document_id = exdoc.document_id "
              + "left join exercises ex on ex.exercise_id = exdoc.exercise_id "
              + "left join scenarios_documents scdoc on d.document_id = scdoc.document_id "
              + "left join scenarios sc on sc.scenario_id = scdoc.scenario_id "
              + "left join documents_tags tagdoc on d.document_id = tagdoc.document_id "
              + "left join tags tg on tg.tag_id = tagdoc.tag_id "
              + "group by d.document_id "
              + "order by document_id desc ",
      nativeQuery = true)
  List<RawDocument> rawAllDocuments();

  @Query(
      value =
          """
        select d.*,
               array_remove(array_agg(tg.tag_id), NULL) as document_tags,
               array_remove(array_agg(ex.exercise_id), NULL) as document_exercises,
               array_remove(array_agg(sc.scenario_id), NULL) as document_scenarios
        from documents d
        left join exercises_documents exdoc on d.document_id = exdoc.document_id
        left join exercises ex on ex.exercise_id = exdoc.exercise_id
        left join scenarios_documents scdoc on d.document_id = scdoc.document_id
        left join scenarios sc on sc.scenario_id = scdoc.scenario_id
        left join documents_tags tagdoc on d.document_id = tagdoc.document_id
        left join tags tg on tg.tag_id = tagdoc.tag_id
        left join channels chl_light on d.document_id = chl_light.channel_logo_light
        left join channels chl_dark  on d.document_id = chl_dark.channel_logo_dark
        where chl_light.channel_id = :channelId
           or chl_dark.channel_id  = :channelId
        group by d.document_id
        order by d.document_id desc
        """,
      nativeQuery = true)
  List<RawDocument> rawAllDocumentsByChannelId(@Param("channelId") String channelId);

  @Query(
      value =
          """
                select d.*,
                       array_remove(array_agg(tg.tag_id), NULL) as document_tags,
                       array_remove(array_agg(ex.exercise_id), NULL) as document_exercises,
                       array_remove(array_agg(sc.scenario_id), NULL) as document_scenarios
                from documents d
                left join exercises_documents exdoc on d.document_id = exdoc.document_id
                left join exercises ex on ex.exercise_id = exdoc.exercise_id
                left join scenarios_documents scdoc on d.document_id = scdoc.document_id
                left join scenarios sc on sc.scenario_id = scdoc.scenario_id
                left join documents_tags tagdoc on d.document_id = tagdoc.document_id
                left join tags tg on tg.tag_id = tagdoc.tag_id
                left join assets sp_light on d.document_id = sp_light.security_platform_logo_light
                left join assets sp_dark  on d.document_id = sp_dark.security_platform_logo_dark
                where sp_light.asset_id = :securityPlatformId
                   or sp_dark.asset_id  = :securityPlatformId
                group by d.document_id
                order by d.document_id desc
                """,
      nativeQuery = true)
  List<RawDocument> rawAllDocumentsBySecurityPlatformId(
      @Param("securityPlatformId") String securityPlatformId);

  @Query(
      value =
          """
                        select d.*,
                               array_remove(array_agg(tg.tag_id), NULL) as document_tags,
                               array_remove(array_agg(ex.exercise_id), NULL) as document_exercises,
                               array_remove(array_agg(sc.scenario_id), NULL) as document_scenarios
                        from documents d
                        left join exercises_documents exdoc on d.document_id = exdoc.document_id
                        left join exercises ex on ex.exercise_id = exdoc.exercise_id
                        left join scenarios_documents scdoc on d.document_id = scdoc.document_id
                        left join scenarios sc on sc.scenario_id = scdoc.scenario_id
                        left join documents_tags tagdoc on d.document_id = tagdoc.document_id
                        left join tags tg on tg.tag_id = tagdoc.tag_id
                        left join challenges_documents chdoc on d.document_id = chdoc.document_id
                        where chdoc.challenge_id = :challengeId
                        group by d.document_id
                        order by d.document_id desc
                        """,
      nativeQuery = true)
  List<RawDocument> rawAllDocumentsByChallengeId(@Param("challengeId") String challengeId);

  @Query(
      value =
          """
                                select d.*,
                                       array_remove(array_agg(tg.tag_id), NULL) as document_tags,
                                       array_remove(array_agg(ex.exercise_id), NULL) as document_exercises,
                                       array_remove(array_agg(sc.scenario_id), NULL) as document_scenarios
                                from documents d
                                left join exercises_documents exdoc on d.document_id = exdoc.document_id
                                left join exercises ex on ex.exercise_id = exdoc.exercise_id
                                left join scenarios_documents scdoc on d.document_id = scdoc.document_id
                                left join scenarios sc on sc.scenario_id = scdoc.scenario_id
                                left join documents_tags tagdoc on d.document_id = tagdoc.document_id
                                left join tags tg on tg.tag_id = tagdoc.tag_id
                                left join payloads pa on (d.document_id = pa.file_drop_file or d.document_id = pa.executable_file)
                                where pa.payload_id = :payloadId
                                group by d.document_id
                                order by d.document_id desc
                                """,
      nativeQuery = true)
  List<RawDocument> rawAllDocumentsByPayloadId(@Param("payloadId") String payloadId);

  @Query(
      value =
          "select d.*, "
              + "array_remove(array_agg(tg.tag_id), NULL) as document_tags, "
              + "array_remove(array_agg(ex.exercise_id), NULL) as document_exercises, "
              + "array_remove(array_agg(sc.scenario_id), NULL) as document_scenarios "
              + "from documents d left join exercises_documents exdoc on d.document_id = exdoc.document_id "
              + "left join exercises ex on ex.exercise_id = exdoc.exercise_id "
              + "left join scenarios_documents scdoc on d.document_id = scdoc.document_id "
              + "left join scenarios sc on sc.scenario_id = scdoc.scenario_id "
              + "left join documents_tags tagdoc on d.document_id = tagdoc.document_id "
              + "left join tags tg on tg.tag_id = tagdoc.tag_id "
              + "left join grants grt ON grt.grant_resource = exdoc.exercise_id AND grt.grant_resource_type = 'SIMULATION' "
              + "left join groups grp on grt.grant_group = grp.group_id "
              + "left join users_groups usgrp on grp.group_id = usgrp.group_id "
              + "left outer join users u on usgrp.user_id = u.user_id "
              + "where u.user_id = :userId "
              + "group by d.document_id "
              + "order by d.document_id desc ",
      nativeQuery = true)
  List<RawDocument> rawAllDocumentsByAccessLevel(@Param("userId") String userId);

  // -- PAGINATION --

  @NotNull
  @EntityGraph(value = "Document.tags-scenarios-exercises", type = EntityGraph.EntityGraphType.LOAD)
  Page<Document> findAll(@NotNull Specification<Document> spec, @NotNull Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT d.* FROM documents d "
              + "WHERE d.document_id IN ("
              + "  SELECT ad.document_id FROM articles_documents ad "
              + "  JOIN articles a ON a.article_id = ad.article_id "
              + "  WHERE a.article_scenario = :scenarioId "
              + "  UNION "
              + "  SELECT id.document_id FROM injects_documents id "
              + "  JOIN injects i ON i.inject_id = id.inject_id "
              + "  WHERE i.inject_scenario = :scenarioId "
              + "  UNION "
              + "  SELECT cd.document_id FROM challenges_documents cd "
              + "  WHERE cd.challenge_id IN ("
              + "    SELECT DISTINCT challenge_id FROM ("
              + "      SELECT jsonb_array_elements_text(i.inject_content::jsonb->'challenges')::varchar as challenge_id "
              + "      FROM injects i "
              + "      WHERE i.inject_scenario = :scenarioId "
              + "      AND i.inject_content IS NOT NULL "
              + "      AND (i.inject_content::jsonb->'challenges') IS NOT NULL " // Check if key
              // exists
              + "      AND jsonb_typeof(i.inject_content::jsonb->'challenges') = 'array'"
              + "    ) challenges"
              + "  )"
              + ")",
      nativeQuery = true)
  List<Document> findAllDistinctByScenarioId(@Param("scenarioId") String scenarioId);

  @Query(
      value =
          "SELECT DISTINCT d.* FROM documents d "
              + "WHERE d.document_id IN ("
              + "  SELECT ad.document_id FROM articles_documents ad "
              + "  JOIN articles a ON a.article_id = ad.article_id "
              + "  WHERE a.article_exercise = :simulationId "
              + "  UNION "
              + "  SELECT id.document_id FROM injects_documents id "
              + "  JOIN injects i ON i.inject_id = id.inject_id "
              + "  WHERE i.inject_exercise = :simulationId "
              + "  UNION "
              + "  SELECT cd.document_id FROM challenges_documents cd "
              + "  WHERE cd.challenge_id IN ("
              + "    SELECT DISTINCT challenge_id FROM ("
              + "      SELECT jsonb_array_elements_text(i.inject_content::jsonb->'challenges')::varchar as challenge_id "
              + "      FROM injects i "
              + "      WHERE i.inject_exercise = :simulationId "
              + "      AND i.inject_content IS NOT NULL "
              + "      AND (i.inject_content::jsonb->'challenges') IS NOT NULL " // Check if key
              // exists
              + "      AND jsonb_typeof(i.inject_content::jsonb->'challenges') = 'array'"
              + "    ) challenges"
              + "  )"
              + ")",
      nativeQuery = true)
  List<Document> findAllDistinctBySimulationId(@Param("simulationId") String simulationId);
}
