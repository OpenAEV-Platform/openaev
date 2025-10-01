package io.openaev.utils.mapper;

import static java.util.Collections.emptyList;

import io.openaev.database.model.Article;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.raw.RawExerciseSimple;
import io.openaev.database.raw.RawInjectExpectation;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.document.form.RelatedEntityOutput;
import io.openaev.rest.exercise.form.ExerciseSimple;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.TargetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ExerciseMapper {

  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final TeamRepository teamRepository;
  private final InjectExpectationRepository injectExpectationRepository;

  private final ResultUtils resultUtils;
  private final InjectMapper injectMapper;
  private final InjectExpectationMapper injectExpectationMapper;

  // -- EXERCISE SIMPLE --
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

      List<TargetSimple> allTargets =
          Stream.concat(
                  injectMapper.toTargetSimple(teams, TargetType.TEAMS).stream(),
                  Stream.concat(
                      injectMapper.toTargetSimple(assets, TargetType.ASSETS).stream(),
                      injectMapper.toTargetSimple(assetGroups, TargetType.ASSETS_GROUPS).stream()))
              .toList();

      simple.getTargets().addAll(allTargets);
    }
    return simple;
  }

  // -- LIST OF EXERCISE SIMPLE --
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

    Map<String, List<RawInjectExpectation>> expectationMap =
        injectExpectationRepository.rawForComputeGlobalByExerciseIds(exerciseIds).stream()
            .collect(Collectors.groupingBy(RawInjectExpectation::getExercise_id));

    List<ExerciseSimple> exerciseSimples = new ArrayList<>();

    for (RawExerciseSimple exercise : exercises) {
      ExerciseSimple simple =
          getExerciseSimple(
              exercise,
              teamMap.getOrDefault(exercise.getExercise_id(), emptyList()),
              assetMap.getOrDefault(exercise.getExercise_id(), emptyList()),
              assetGroupMap.getOrDefault(exercise.getExercise_id(), emptyList()),
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
      List<RawInjectExpectation> expectations) {

    ExerciseSimple simple = fromRawExerciseSimple(rawExercise);

    if (rawExercise.getInject_ids() != null) {
      // -- GLOBAL SCORE ---
      simple.setExpectationResultByTypes(
          injectExpectationMapper.extractExpectationResultByTypesFromRaw(
              rawExercise.getInject_ids(), expectations));
      // -- TARGETS --
      List<TargetSimple> allTargets =
          Stream.concat(
                  injectMapper.toTargetSimple(teams, TargetType.TEAMS).stream(),
                  Stream.concat(
                      injectMapper.toTargetSimple(assets, TargetType.ASSETS).stream(),
                      injectMapper.toTargetSimple(assetGroups, TargetType.ASSETS_GROUPS).stream()))
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

  public static Set<RelatedEntityOutput> toRelatedEntityOutputs(Set<Exercise> exercises) {
    return exercises.stream()
        .map(exercise -> toRelatedEntityOutput(exercise))
        .collect(Collectors.toSet());
  }

  private static RelatedEntityOutput toRelatedEntityOutput(Exercise exercise) {
    return RelatedEntityOutput.builder().id(exercise.getId()).name(exercise.getName()).build();
  }

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
