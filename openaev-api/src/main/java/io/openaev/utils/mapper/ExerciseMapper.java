package io.openaev.utils.mapper;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Article;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.raw.RawExerciseSimple;
import io.openaev.database.raw.RawGlobalScoreExpectation;
import io.openaev.database.repository.AiTargetRepository;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.document.form.RelatedEntityOutput;
import io.openaev.rest.exercise.form.ExerciseSimple;
import io.openaev.utils.InjectContentUtils;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.TargetType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting Exercise entities to output DTOs.
 *
 * <p>Provides methods for transforming exercise domain objects and raw database results into API
 * response objects, including target resolution and expectation result aggregation.
 *
 * @see io.openaev.database.model.Exercise
 * @see io.openaev.rest.exercise.form.ExerciseSimple
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class ExerciseMapper {

  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final TeamRepository teamRepository;
  private final InjectRepository injectRepository;
  private final AiTargetRepository aiTargetRepository;
  private final InjectExpectationRepository injectExpectationRepository;

  private final ResultUtils resultUtils;
  private final InjectMapper injectMapper;
  private final InjectExpectationMapper injectExpectationMapper;
  private final ObjectMapper objectMapper;

  // -- EXERCISE SIMPLE --

  /**
   * Converts a raw exercise to a simplified exercise DTO with full target and score resolution.
   *
   * <p>Performs additional database queries to resolve teams, assets, and asset groups, then
   * computes global expectation results.
   *
   * @param rawExercise the raw exercise data from database
   * @return the exercise simple DTO with resolved targets and scores
   */
  public ExerciseSimple getExerciseSimple(RawExerciseSimple rawExercise) {

    ExerciseSimple simple = fromRawExerciseSimple(rawExercise);

    if (rawExercise.getInject_ids() != null) {
      // -- GLOBAL SCORE ---
      simple.setExpectationResultByTypes(
          resultUtils.computeGlobalExpectationResults(rawExercise.getInject_ids()));

      // -- TARGETS --
      List<Object[]> teams =
          teamRepository.teamsByExerciseIds(Set.of(rawExercise.getExercise_id()));
      List<Object[]> assets =
          assetRepository.assetsByExerciseIds(Set.of(rawExercise.getExercise_id()));
      List<Object[]> assetGroups =
          assetGroupRepository.assetGroupsByExerciseIds(Set.of(rawExercise.getExercise_id()));
      ContentTargetsByExerciseIds contentTargets =
          contentTargetsByExerciseIds(Set.of(rawExercise.getExercise_id()));

      List<TargetSimple> allTargets =
          Stream.of(
                  injectMapper.toTargetSimple(teams, TargetType.TEAMS),
                  injectMapper.toTargetSimple(assets, TargetType.ASSETS),
                  injectMapper.toTargetSimple(assetGroups, TargetType.ASSETS_GROUPS),
                  injectMapper.toTargetSimple(
                      contentTargets
                          .aiTargets()
                          .getOrDefault(rawExercise.getExercise_id(), emptyList()),
                      TargetType.AI_TARGETS),
                  injectMapper.toTargetSimple(
                      contentTargets
                          .manualTargets()
                          .getOrDefault(rawExercise.getExercise_id(), emptyList()),
                      TargetType.MANUAL))
              .flatMap(List::stream)
              .toList();

      simple.getTargets().addAll(allTargets);
    }
    return simple;
  }

  /**
   * Content-based targets keyed by exercise id, as rows consumable by {@link
   * InjectMapper#toTargetSimple}: {@code aiTargets} rows are {@code [exerciseId, assetId,
   * assetName, category]}, {@code manualTargets} rows are {@code [exerciseId, value, value]}.
   */
  public record ContentTargetsByExerciseIds(
      Map<String, List<Object[]>> aiTargets, Map<String, List<Object[]>> manualTargets) {

    public static ContentTargetsByExerciseIds empty() {
      return new ContentTargetsByExerciseIds(new HashMap<>(), new HashMap<>());
    }
  }

  /**
   * Resolve the content-based targets of each exercise inject: the AI target referenced from the
   * content ({@code ai_target} key) and the raw manual target ({@code target_selector = "manual"}).
   * These targets are content references (not JPA relations), so the injects_assets join powering
   * the asset chips never surfaces them and the simulation list "Target" column would otherwise
   * stay empty for such injects. Mirrors InjectSearchService#buildAiTargetMap /
   * #buildManualTargetMap which fixed the same gap on the atomic-testing list.
   */
  public ContentTargetsByExerciseIds contentTargetsByExerciseIds(Set<String> exerciseIds) {
    if (exerciseIds == null || exerciseIds.isEmpty()) {
      return ContentTargetsByExerciseIds.empty();
    }

    // exerciseId -> referenced AI target ids / manual target values (from injects content)
    Map<String, Set<String>> exerciseToAiTargetIds = new HashMap<>();
    Map<String, Set<String>> exerciseToManualTargets = new HashMap<>();
    for (Object[] row : injectRepository.findContentTargetContentsByExerciseIds(exerciseIds)) {
      String exerciseId = (String) row[0];
      String rawContent = (String) row[1];
      if (exerciseId == null || rawContent == null) {
        continue;
      }
      try {
        JsonNode content = objectMapper.readTree(rawContent);
        if (content instanceof ObjectNode objectContent) {
          InjectContentUtils.contentAiTargetId(objectContent)
              .ifPresent(
                  aiTargetId ->
                      exerciseToAiTargetIds
                          .computeIfAbsent(exerciseId, key -> new HashSet<>())
                          .add(aiTargetId));
          InjectContentUtils.contentManualTarget(objectContent)
              .ifPresent(
                  manualTarget ->
                      exerciseToManualTargets
                          .computeIfAbsent(exerciseId, key -> new HashSet<>())
                          .add(manualTarget));
        }
      } catch (Exception e) {
        // A single unreadable content must never break the whole simulation list.
        log.warn("Unparseable inject content while resolving content-based targets", e);
      }
    }

    // Manual targets: the raw value is both the id and the display name.
    Map<String, List<Object[]>> manualTargets = new HashMap<>();
    exerciseToManualTargets.forEach(
        (exerciseId, values) ->
            manualTargets.put(
                exerciseId,
                values.stream()
                    .map(value -> new Object[] {exerciseId, value, value})
                    .collect(Collectors.toList())));

    if (exerciseToAiTargetIds.isEmpty()) {
      return new ContentTargetsByExerciseIds(new HashMap<>(), manualTargets);
    }

    // Single category-scoped lookup for every referenced AI target, then map id -> name.
    Map<String, String> aiTargetNameById =
        aiTargetRepository
            .findAiTargetsByIds(
                List.copyOf(
                    exerciseToAiTargetIds.values().stream()
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet())))
            .stream()
            .collect(Collectors.toMap(Asset::getId, Asset::getName));

    Map<String, List<Object[]>> aiTargets = new HashMap<>();
    exerciseToAiTargetIds.forEach(
        (exerciseId, aiTargetIds) -> {
          List<Object[]> rows = new ArrayList<>();
          for (String aiTargetId : aiTargetIds) {
            String name = aiTargetNameById.get(aiTargetId);
            if (name != null) {
              rows.add(new Object[] {exerciseId, aiTargetId, name, AssetCategory.AI_TARGET.name()});
            }
          }
          if (!rows.isEmpty()) {
            aiTargets.put(exerciseId, rows);
          }
        });
    return new ContentTargetsByExerciseIds(aiTargets, manualTargets);
  }

  // -- LIST OF EXERCISE SIMPLE --

  /**
   * Converts a list of raw exercises to simplified DTOs with batched target resolution.
   *
   * <p>Optimizes database access by batching target queries across all exercises rather than
   * querying for each exercise individually.
   *
   * @param exercises the list of raw exercise data
   * @return list of exercise simple DTOs with resolved targets and scores
   */
  public List<ExerciseSimple> getExerciseSimples(List<RawExerciseSimple> exercises) {
    // -- MAP TO GENERATE TARGETSIMPLEs
    Set<String> exerciseIds =
        exercises.stream().map(RawExerciseSimple::getExercise_id).collect(Collectors.toSet());

    Map<String, List<Object[]>> teamMap =
        teamRepository.teamsByExerciseIds(exerciseIds).stream()
            .collect(Collectors.groupingBy(row -> (String) row[0]));

    Map<String, List<Object[]>> assetMap =
        assetRepository.assetsByExerciseIds(exerciseIds).stream()
            .collect(Collectors.groupingBy(row -> (String) row[0]));

    Map<String, List<Object[]>> assetGroupMap =
        assetGroupRepository.assetGroupsByExerciseIds(exerciseIds).stream()
            .collect(Collectors.groupingBy(row -> (String) row[0]));

    ContentTargetsByExerciseIds contentTargets = contentTargetsByExerciseIds(exerciseIds);

    Map<String, List<RawGlobalScoreExpectation>> expectationMap =
        injectExpectationRepository.rawForComputeGlobalByExerciseIds(exerciseIds).stream()
            .collect(Collectors.groupingBy(RawGlobalScoreExpectation::getExercise_id));

    List<ExerciseSimple> exerciseSimples = new ArrayList<>();

    for (RawExerciseSimple exercise : exercises) {
      ExerciseSimple simple =
          getExerciseSimple(
              exercise,
              teamMap.getOrDefault(exercise.getExercise_id(), emptyList()),
              assetMap.getOrDefault(exercise.getExercise_id(), emptyList()),
              assetGroupMap.getOrDefault(exercise.getExercise_id(), emptyList()),
              contentTargets.aiTargets().getOrDefault(exercise.getExercise_id(), emptyList()),
              contentTargets.manualTargets().getOrDefault(exercise.getExercise_id(), emptyList()),
              expectationMap.getOrDefault(exercise.getExercise_id(), emptyList()));
      exerciseSimples.add(simple);
    }

    return exerciseSimples;
  }

  private ExerciseSimple getExerciseSimple(
      RawExerciseSimple rawExercise,
      List<Object[]> teams,
      List<Object[]> assets,
      List<Object[]> assetGroups,
      List<Object[]> aiTargets,
      List<Object[]> manualTargets,
      List<RawGlobalScoreExpectation> expectations) {

    ExerciseSimple simple = fromRawExerciseSimple(rawExercise);

    if (rawExercise.getInject_ids() != null) {
      // -- GLOBAL SCORE ---
      simple.setExpectationResultByTypes(
          injectExpectationMapper.extractExpectationResultByTypesFromRaw(
              rawExercise.getInject_ids(), expectations));
      // -- TARGETS --
      List<TargetSimple> allTargets =
          Stream.of(
                  injectMapper.toTargetSimple(teams, TargetType.TEAMS),
                  injectMapper.toTargetSimple(assets, TargetType.ASSETS),
                  injectMapper.toTargetSimple(assetGroups, TargetType.ASSETS_GROUPS),
                  injectMapper.toTargetSimple(aiTargets, TargetType.AI_TARGETS),
                  injectMapper.toTargetSimple(manualTargets, TargetType.MANUAL))
              .flatMap(List::stream)
              .toList();

      simple.getTargets().addAll(allTargets);
    }

    return simple;
  }

  // -- RAWEXERCISESIMPLE to EXERCISESIMPLE --
  private ExerciseSimple fromRawExerciseSimple(RawExerciseSimple rawExercise) {
    ExerciseSimple simple = new ExerciseSimple();
    simple.setId(rawExercise.getExercise_id());
    simple.setName(rawExercise.getExercise_name());
    simple.setTagIds(rawExercise.getExercise_tags());
    simple.setCategory(rawExercise.getExercise_category());
    simple.setSubtitle(rawExercise.getExercise_subtitle());
    simple.setStatus(ExerciseStatus.valueOf(rawExercise.getExercise_status()));
    simple.setStart(rawExercise.getExercise_start_date());
    simple.setUpdatedAt(rawExercise.getExercise_updated_at());

    return simple;
  }

  /**
   * Converts an exercise entity to a simplified DTO.
   *
   * <p>Maps basic exercise properties without resolving targets or computing scores.
   *
   * @param exercise the exercise entity
   * @return the simplified exercise DTO
   */
  public ExerciseSimple toExerciseSimple(Exercise exercise) {
    ExerciseSimple simple = new ExerciseSimple();
    simple.setId(exercise.getId());
    simple.setName(exercise.getName());
    simple.setTagIds(
        exercise.getTags().stream().map(tag -> tag.getId()).collect(Collectors.toSet()));
    simple.setCategory(exercise.getCategory());
    simple.setSubtitle(exercise.getSubtitle());
    simple.setStatus(exercise.getStatus());
    simple.setUpdatedAt(exercise.getUpdatedAt());

    return simple;
  }

  /**
   * Converts a set of exercises to related entity outputs.
   *
   * <p>Used for showing exercise references in document or other entity contexts.
   *
   * @param exercises the exercises to convert
   * @return set of related entity output DTOs
   */
  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(Set<Exercise> exercises) {
    return exercises.stream()
        .map(exercise -> toRelatedEntityOutput(exercise))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(Exercise exercise) {
    return RelatedEntityOutput.builder().id(exercise.getId()).name(exercise.getName()).build();
  }

  /**
   * Converts a set of articles to related entity outputs with simulation context.
   *
   * @param articles the articles to convert
   * @return set of related entity output DTOs including exercise context
   */
  public static Set<RelatedEntityOutput> toSimulationArticles(Set<Article> articles) {
    return articles.stream()
        .map(article -> toSimulationArticle(article))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toSimulationArticle(Article article) {
    return RelatedEntityOutput.builder()
        .id(article.getId())
        .name(article.getName())
        .context(article.getExercise().getId())
        .build();
  }

  /**
   * Converts a set of injects to related entity outputs with simulation context.
   *
   * @param injects the injects to convert
   * @return set of related entity output DTOs including exercise context
   */
  public static Set<RelatedEntityOutput> toSimulationInjects(Set<Inject> injects) {
    return injects.stream().map(inject -> toSimulationInject(inject)).collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toSimulationInject(Inject inject) {
    return RelatedEntityOutput.builder()
        .id(inject.getId())
        .name(inject.getTitle())
        .context(inject.getExercise().getId())
        .build();
  }
}
