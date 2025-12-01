package io.openaev.rest.exercise.service;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.database.specification.ExerciseSpecification.*;
import static io.openaev.database.specification.TeamSpecification.fromIds;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.JpaUtils.arrayAggOnId;
import static io.openaev.utils.StringUtils.duplicateString;
import static io.openaev.utils.constants.Constants.ARTICLES;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilderWithNullHandling;
import static java.time.Instant.now;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawExerciseSimple;
import io.openaev.database.raw.RawInjectExpectation;
import io.openaev.database.raw.RawSimulation;
import io.openaev.database.repository.*;
import io.openaev.ee.Ee;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.form.ExerciseSimple;
import io.openaev.rest.exercise.form.ExercisesGlobalScoresInput;
import io.openaev.rest.exercise.response.ExercisesGlobalScoresOutput;
import io.openaev.rest.inject.form.InjectExpectationResultsByAttackPattern;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.scenario.service.ScenarioStatisticService;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.TagRuleService;
import io.openaev.service.TeamService;
import io.openaev.service.UserService;
import io.openaev.service.VariableService;
import io.openaev.service.cron.CronService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.TargetType;
import io.openaev.utils.mapper.ExerciseMapper;
import io.openaev.utils.mapper.InjectExpectationMapper;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.pagination.SortUtilsCriteriaBuilder;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotBlank;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Validated
@Service
public class ExerciseService {

  @PersistenceContext private EntityManager entityManager;

  private final Ee eeService;
  private final InjectDuplicateService injectDuplicateService;
  private final TeamService teamService;
  private final VariableService variableService;
  private final TagRuleService tagRuleService;
  private final DocumentService documentService;
  private final InjectService injectService;
  private final CronService cronService;
  private final UserService userService;

  private final ExerciseMapper exerciseMapper;
  private final InjectMapper injectMapper;
  private final ResultUtils resultUtils;
  private final ActionMetricCollector actionMetricCollector;
  private final LicenseCacheManager licenseCacheManager;

  private final AssetRepository assetRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final InjectExpectationRepository injectExpectationRepository;
  private final ArticleRepository articleRepository;
  private final ExerciseRepository exerciseRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final ExerciseTeamUserRepository exerciseTeamUserRepository;
  private final InjectRepository injectRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;

  private final InjectExpectationMapper injectExpectationMapper;

  // region properties
  @Value("${openaev.mail.imap.enabled}")
  private boolean imapEnabled;

  @Value("${openaev.mail.imap.username}")
  private String imapUsername;

  @Resource private OpenAEVConfig openAEVConfig;

  // endregion

  // -- CRUD --

  // -- CREATION --
  @Transactional(rollbackFor = Exception.class)
  public Exercise createExercise(@NotNull final Exercise exercise) {
    if (!StringUtils.hasText(exercise.getFrom())) {
      if (imapEnabled) {
        exercise.setFrom(imapUsername);
        exercise.setReplyTos(List.of(imapUsername));
      } else {
        exercise.setFrom(openAEVConfig.getDefaultMailer());
        exercise.setReplyTos(new ArrayList<>(List.of(openAEVConfig.getDefaultReplyTo())));
      }
    }

    actionMetricCollector.addSimulationCreatedCount();
    return exerciseRepository.save(exercise);
  }

  // -- READ --
  public Exercise exercise(@NotBlank final String exerciseId) {
    return this.exerciseRepository
        .findById(exerciseId)
        .orElseThrow(() -> new ElementNotFoundException("Exercise not found"));
  }

  public RawSimulation rawSimulation(@NotBlank final String simulationId) {
    RawSimulation rawSimulation = exerciseRepository.rawDetailsById(simulationId);
    if (rawSimulation == null) {
      throw new ElementNotFoundException("Simulation not found");
    }
    return rawSimulation;
  }

  public List<ExerciseSimple> exercises(final List<String> exerciseIds) {

    User currentUser = userService.currentUser();
    List<RawExerciseSimple> exercises =
        currentUser.isAdminOrBypass()
                || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)
            ? exerciseRepository.rawByExerciseIds(exerciseIds)
            : exerciseRepository.rawGrantedByExerciseIds(currentUser().getId(), exerciseIds);
    return exerciseMapper.getExerciseSimples(exercises);
  }

  // -- UPDATE --
  public Exercise updateExercise(@NotNull final Exercise exercise) {
    exercise.setUpdatedAt(now());
    return this.exerciseRepository.save(exercise);
  }

  // -- DUPLICATION --
  @Transactional
  public Exercise getDuplicateExercise(@NotBlank String exerciseId) {
    Exercise exerciseOrigin = exerciseRepository.findById(exerciseId).orElseThrow();
    Exercise exercise = copyExercice(exerciseOrigin);
    Exercise exerciseDuplicate = exerciseRepository.save(exercise);
    actionMetricCollector.addSimulationCreatedCount();
    getListOfDuplicatedInjects(exerciseDuplicate, exerciseOrigin);
    getListOfExerciseTeams(exerciseDuplicate, exerciseOrigin);
    getListOfArticles(exerciseDuplicate, exerciseOrigin);
    getListOfVariables(exerciseDuplicate, exerciseOrigin);
    getObjectives(exerciseDuplicate, exerciseOrigin);
    getLessonsCategories(exerciseDuplicate, exerciseOrigin);
    return exerciseRepository.save(exercise);
  }

  private Exercise copyExercice(Exercise exerciseOrigin) {
    Exercise exerciseDuplicate = new Exercise();
    exerciseDuplicate.setName(duplicateString(exerciseOrigin.getName()));
    exerciseDuplicate.setCategory(exerciseOrigin.getCategory());
    exerciseDuplicate.setDescription(exerciseOrigin.getDescription());
    exerciseDuplicate.setFrom(exerciseOrigin.getFrom());
    exerciseDuplicate.setFooter(exerciseOrigin.getFooter());
    exerciseDuplicate.setScenario(exerciseOrigin.getScenario());
    exerciseDuplicate.setHeader(exerciseOrigin.getHeader());
    exerciseDuplicate.setMainFocus(exerciseOrigin.getMainFocus());
    exerciseDuplicate.setSeverity(exerciseOrigin.getSeverity());
    exerciseDuplicate.setSubtitle(exerciseOrigin.getSubtitle());
    exerciseDuplicate.setLogoDark(exerciseOrigin.getLogoDark());
    exerciseDuplicate.setLogoLight(exerciseOrigin.getLogoLight());
    exerciseDuplicate.setGrants(new ArrayList<>(exerciseOrigin.getGrants()));
    exerciseDuplicate.setTags(new HashSet<>(exerciseOrigin.getTags()));
    exerciseDuplicate.setTeamUsers(new ArrayList<>(exerciseOrigin.getTeamUsers()));
    exerciseDuplicate.setReplyTos(new ArrayList<>(exerciseOrigin.getReplyTos()));
    exerciseDuplicate.setDocuments(new ArrayList<>(exerciseOrigin.getDocuments()));
    exerciseDuplicate.setLessonsAnonymized(exerciseOrigin.isLessonsAnonymized());
    exerciseDuplicate.setCustomDashboard(exerciseOrigin.getCustomDashboard());
    return exerciseDuplicate;
  }

  public List<Document> getExercisePlayerDocuments(Exercise exercise) {
    List<Article> articles = exercise.getArticles();
    List<Inject> injects = exercise.getInjects();
    return documentService.getPlayerDocuments(articles, injects);
  }

  public Optional<Exercise> getFollowingSimulation(Exercise exercise) {
    return exercise.getScenario() != null
        ? exerciseRepository.following(exercise)
        : Optional.empty();
  }

  public Optional<Instant> getLatestValidityDate(Exercise exercise) {
    Optional<Exercise> follower = this.getFollowingSimulation(exercise);
    if (follower.isPresent()) {
      return follower.get().getStart();
    }

    return exercise.getStart().isPresent() && exercise.getScenario() != null
        ? cronService.getNextExecutionFromInstant(
            exercise.getStart().get(), ZoneId.of("UTC"), exercise.getScenario().getRecurrence())
        : Optional.empty();
  }

  private void getListOfExerciseTeams(
      @NotNull Exercise exercise, @NotNull Exercise exerciseOrigin) {
    Map<String, Team> contextualTeams = new HashMap<>();
    List<Team> exerciseTeams = new ArrayList<>();
    exerciseOrigin
        .getTeams()
        .forEach(
            scenarioTeam -> {
              if (scenarioTeam.getContextual()) {
                Team team = teamService.copyContextualTeam(scenarioTeam);
                Team teamSaved = this.teamRepository.save(team);
                exerciseTeams.add(teamSaved);
                contextualTeams.put(scenarioTeam.getId(), teamSaved);
              } else {
                exerciseTeams.add(scenarioTeam);
              }
            });
    exercise.setTeams(new ArrayList<>(exerciseTeams));

    exercise
        .getInjects()
        .forEach(
            inject -> {
              List<Team> teams = new ArrayList<>();
              inject
                  .getTeams()
                  .forEach(
                      team -> {
                        if (team.getContextual()) {
                          teams.add(contextualTeams.get(team.getId()));
                        } else {
                          teams.add(team);
                        }
                      });
              inject.setTeams(teams);
            });
  }

  private void getListOfDuplicatedInjects(Exercise exercise, Exercise exerciseOrigin) {
    List<Inject> injectListForExercise =
        exerciseOrigin.getInjects().stream()
            .map(inject -> injectDuplicateService.duplicateInjectForExercise(exercise, inject))
            .toList();
    exercise.setInjects(new ArrayList<>(injectListForExercise));
  }

  private void getListOfArticles(Exercise exercise, Exercise exerciseOrigin) {
    List<Article> articleList = new ArrayList<>();
    Map<String, String> mapIdArticleOriginNew = new HashMap<>();
    exerciseOrigin
        .getArticles()
        .forEach(
            article -> {
              Article exerciceArticle = new Article();
              exerciceArticle.setName(article.getName());
              exerciceArticle.setContent(article.getContent());
              exerciceArticle.setAuthor(article.getAuthor());
              exerciceArticle.setShares(article.getShares());
              exerciceArticle.setLikes(article.getLikes());
              exerciceArticle.setComments(article.getComments());
              exerciceArticle.setChannel(article.getChannel());
              exerciceArticle.setDocuments(new ArrayList<>(article.getDocuments()));
              exerciceArticle.setExercise(exercise);
              Article save = articleRepository.save(exerciceArticle);
              articleList.add(save);
              mapIdArticleOriginNew.put(article.getId(), save.getId());
            });
    exercise.setArticles(articleList);
    for (Inject inject : exercise.getInjects()) {
      if (ofNullable(inject.getContent()).map(c -> c.has(ARTICLES)).orElse(Boolean.FALSE)) {
        List<String> articleNode = new ArrayList<>();
        JsonNode articles = inject.getContent().findValue(ARTICLES);
        if (articles.isArray()) {
          for (final JsonNode node : articles) {
            if (mapIdArticleOriginNew.containsKey(node.textValue())) {
              articleNode.add(mapIdArticleOriginNew.get(node.textValue()));
            }
          }
        }
        inject.getContent().remove(ARTICLES);
        ArrayNode arrayNode = inject.getContent().putArray(ARTICLES);
        articleNode.forEach(arrayNode::add);
      }
    }
  }

  private void getListOfVariables(Exercise exercise, Exercise exerciseOrigin) {
    List<Variable> variables = variableService.variablesFromExercise(exerciseOrigin.getId());
    List<Variable> variableList =
        variables.stream()
            .map(
                variable -> {
                  Variable variable1 = new Variable();
                  variable1.setKey(variable.getKey());
                  variable1.setDescription(variable.getDescription());
                  variable1.setValue(variable.getValue());
                  variable1.setType(variable.getType());
                  variable1.setExercise(exercise);
                  return variable1;
                })
            .toList();
    variableService.createVariables(variableList);
  }

  private void getLessonsCategories(Exercise duplicatedExercise, Exercise originalExercise) {
    List<LessonsCategory> duplicatedCategories = new ArrayList<>();
    for (LessonsCategory originalCategory : originalExercise.getLessonsCategories()) {
      LessonsCategory duplicatedCategory = new LessonsCategory();
      duplicatedCategory.setName(originalCategory.getName());
      duplicatedCategory.setDescription(originalCategory.getDescription());
      duplicatedCategory.setOrder(originalCategory.getOrder());
      duplicatedCategory.setExercise(duplicatedExercise);
      duplicatedCategory.setTeams(new ArrayList<>(originalCategory.getTeams()));

      List<LessonsQuestion> duplicatedQuestions = new ArrayList<>();
      for (LessonsQuestion originalQuestion : originalCategory.getQuestions()) {
        LessonsQuestion duplicatedQuestion = new LessonsQuestion();
        duplicatedQuestion.setCategory(originalQuestion.getCategory());
        duplicatedQuestion.setContent(originalQuestion.getContent());
        duplicatedQuestion.setExplanation(originalQuestion.getExplanation());
        duplicatedQuestion.setOrder(originalQuestion.getOrder());
        duplicatedQuestion.setCategory(duplicatedCategory);

        List<LessonsAnswer> duplicatedAnswers = new ArrayList<>();
        for (LessonsAnswer originalAnswer : originalQuestion.getAnswers()) {
          LessonsAnswer duplicatedAnswer = new LessonsAnswer();
          duplicatedAnswer.setUser(originalAnswer.getUser());
          duplicatedAnswer.setScore(originalAnswer.getScore());
          duplicatedAnswer.setPositive(originalAnswer.getPositive());
          duplicatedAnswer.setNegative(originalAnswer.getNegative());
          duplicatedAnswer.setQuestion(duplicatedQuestion);
          duplicatedAnswers.add(duplicatedAnswer);
        }
        duplicatedQuestion.setAnswers(duplicatedAnswers);
        duplicatedQuestions.add(duplicatedQuestion);
      }
      duplicatedCategory.setQuestions(duplicatedQuestions);
      duplicatedCategories.add(duplicatedCategory);
    }
    duplicatedExercise.setLessonsCategories(duplicatedCategories);
  }

  private void getObjectives(Exercise duplicatedExercise, Exercise originalExercise) {
    List<Objective> duplicatedObjectives = new ArrayList<>();
    for (Objective originalObjective : originalExercise.getObjectives()) {
      Objective duplicatedObjective = new Objective();
      duplicatedObjective.setTitle(originalObjective.getTitle());
      duplicatedObjective.setDescription(originalObjective.getDescription());
      duplicatedObjective.setPriority(originalObjective.getPriority());
      List<Evaluation> duplicatedEvaluations = new ArrayList<>();
      for (Evaluation originalEvaluation : originalObjective.getEvaluations()) {
        Evaluation duplicatedEvaluation = new Evaluation();
        duplicatedEvaluation.setScore(originalEvaluation.getScore());
        duplicatedEvaluation.setUser(originalEvaluation.getUser());
        duplicatedEvaluation.setObjective(duplicatedObjective);
        duplicatedEvaluations.add(duplicatedEvaluation);
      }
      duplicatedObjective.setEvaluations(duplicatedEvaluations);
      duplicatedObjective.setExercise(duplicatedExercise);
      duplicatedObjectives.add(duplicatedObjective);
    }
    duplicatedExercise.setObjectives(duplicatedObjectives);
  }

  // -- EXERCISES --
  public List<ExerciseSimple> exercises() {
    // We get the exercises depending on whether or not we are granted or have the capa
    User currentUser = userService.currentUser();
    List<RawExerciseSimple> exercises =
        currentUser.isAdminOrBypass()
                || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)
            ? exerciseRepository.rawAll()
            : exerciseRepository.rawAllGranted(currentUser().getId());
    return exerciseMapper.getExerciseSimples(exercises);
  }

  public Page<ExerciseSimple> exercises(
      Specification<Exercise> specification,
      Specification<Exercise> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilderAndExercises result =
        getCriteriaBuilderAndExercises(specification, pageable, joinMap);

    setComputedAttributes(result.exercises());

    return getExerciseSimples(specificationCount, pageable, result);
  }

  public Page<ExerciseSimple> exercisesWithEmptyGlobalScore(
      Specification<Exercise> specification,
      Specification<Exercise> specificationCount,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilderAndExercises result =
        getCriteriaBuilderAndExercises(specification, pageable, joinMap);

    setComputedAttributesWithEmptyGlobalScore(result.exercises());

    return getExerciseSimples(specificationCount, pageable, result);
  }

  public void throwIfExerciseNotLaunchable(Exercise exercise) {
    if (eeService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      return;
    }
    exercise.getInjects().forEach(injectService::throwIfInjectNotLaunchable);
  }

  public boolean checkIfTagRulesApplies(
      @NotNull final Exercise exercise, @NotNull final List<String> newTags) {
    return tagRuleService.checkIfRulesApply(
        exercise.getTags().stream().map(Tag::getId).toList(), newTags);
  }

  private PageImpl<ExerciseSimple> getExerciseSimples(
      Specification<Exercise> specificationCount,
      Pageable pageable,
      CriteriaBuilderAndExercises result) {
    // -- Count Query --
    Long total = countQuery(result.cb(), this.entityManager, Exercise.class, specificationCount);

    return new PageImpl<>(result.exercises(), pageable, total);
  }

  private CriteriaBuilderAndExercises getCriteriaBuilderAndExercises(
      Specification<Exercise> specification,
      Pageable pageable,
      Map<String, Join<Base, Base>> joinMap) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<Exercise> exerciseRoot = cq.from(Exercise.class);

    // -- Sorting --
    SortUtilsCriteriaBuilder.SortSpecification sortSpecification =
        toSortCriteriaBuilderWithNullHandling(cb, exerciseRoot, pageable.getSort());
    cq.orderBy(sortSpecification.orders());

    // -- Select
    List<Selection<?>> selections = getCriteriaBuilderSelections(cb, exerciseRoot, joinMap);
    cq.groupBy(Collections.singletonList(exerciseRoot.get("id")));
    selections.addAll(sortSpecification.selections());
    cq.multiselect(selections).distinct(true);

    // -- Text Search and Filters --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(exerciseRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // Type Query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<ExerciseSimple> exercises = execution(query);

    return new CriteriaBuilderAndExercises(cb, exercises);
  }

  public List<FilterUtilsJpa.Option> getOptionsByNameLinkedToFindings(
      String searchText, String simulationOrScenarioId, Pageable pageable) {
    String trimmedSearchText = org.apache.commons.lang3.StringUtils.trimToNull(searchText);
    String trimmedSimulationOrScenarioId =
        org.apache.commons.lang3.StringUtils.trimToNull(simulationOrScenarioId);

    List<Object[]> results;

    if (trimmedSimulationOrScenarioId == null) {
      results = exerciseRepository.findAllOptionByNameLinkedToFindings(trimmedSearchText, pageable);
    } else {
      results =
          exerciseRepository.findAllOptionByNameLinkedToFindingsWithContext(
              trimmedSimulationOrScenarioId, trimmedSearchText, pageable);
    }
    return results.stream()
        .map(i -> new FilterUtilsJpa.Option((String) i[0], (String) i[1]))
        .toList();
  }

  public List<InjectExpectationResultsByAttackPattern> extractExpectationResultsByAttackPattern(
      String exerciseId) {
    Exercise exercise = exercise(exerciseId);
    return resultUtils.computeInjectExpectationResults(exercise.getInjects());
  }

  private record CriteriaBuilderAndExercises(CriteriaBuilder cb, List<ExerciseSimple> exercises) {}

  // -- SELECT --
  private List<Selection<?>> getCriteriaBuilderSelections(
      CriteriaBuilder cb, Root<Exercise> exerciseRoot, Map<String, Join<Base, Base>> joinMap) {
    List<Selection<?>> selections = new ArrayList<>();

    // Array aggregations
    Join<Base, Base> exerciseTagsJoin = exerciseRoot.join("tags", JoinType.LEFT);
    joinMap.put("tags", exerciseTagsJoin);
    Expression<String[]> tagIdsExpression =
        arrayAggOnId((HibernateCriteriaBuilder) cb, exerciseTagsJoin);

    Join<Base, Base> injectsJoin = exerciseRoot.join("injects", JoinType.LEFT);
    joinMap.put("injects", injectsJoin);
    Expression<String[]> injectIdsExpression =
        arrayAggOnId((HibernateCriteriaBuilder) cb, injectsJoin);

    // SELECTIONS
    selections.add(exerciseRoot.get("id").alias("exercise_id"));
    selections.add(exerciseRoot.get("name").alias("exercise_name"));
    selections.add(exerciseRoot.get("status").alias("exercise_status"));
    selections.add(exerciseRoot.get("subtitle").alias("exercise_subtitle"));
    selections.add(exerciseRoot.get("category").alias("exercise_category"));
    selections.add(exerciseRoot.get("start").alias("exercise_start_date"));
    selections.add(exerciseRoot.get("end").alias("exercise_end_date"));
    selections.add(exerciseRoot.get("updatedAt").alias("exercise_updated_at"));
    selections.add(tagIdsExpression.alias("exercise_tags"));
    selections.add(injectIdsExpression.alias("exercise_injects"));

    // GROUP BY
    return selections;
  }

  // -- EXECUTION --
  private List<ExerciseSimple> execution(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple -> {
              ExerciseSimple exerciseSimple = new ExerciseSimple();
              exerciseSimple.setId(tuple.get("exercise_id", String.class));
              exerciseSimple.setName(tuple.get("exercise_name", String.class));
              exerciseSimple.setStatus(tuple.get("exercise_status", ExerciseStatus.class));
              exerciseSimple.setSubtitle(tuple.get("exercise_subtitle", String.class));
              exerciseSimple.setCategory(tuple.get("exercise_category", String.class));
              exerciseSimple.setStart(tuple.get("exercise_start_date", Instant.class));
              exerciseSimple.setUpdatedAt(tuple.get("exercise_updated_at", Instant.class));
              exerciseSimple.setTagIds(
                  new HashSet<>(Arrays.asList(tuple.get("exercise_tags", String[].class))));
              exerciseSimple.setInjectIds(tuple.get("exercise_injects", String[].class));
              return exerciseSimple;
            })
        .toList();
  }

  // -- COMPUTED ATTRIBUTES --
  private void setComputedAttributes(List<ExerciseSimple> originalExercises) {
    List<ExerciseSimple> exercises = getExercisesWithId(originalExercises);
    if (exercises.isEmpty()) {
      return;
    }

    Set<String> exerciseIds = getExerciseIds(exercises);
    MappingsByExerciseIds mappingsByExerciseIds = getResultsByExerciseIds(exerciseIds);

    Map<String, List<RawInjectExpectation>> expectationsByExerciseIds =
        getExpectationsByExerciseId(exerciseIds);

    for (ExerciseSimple exercise : exercises) {
      setGlobalScore(exercise, expectationsByExerciseIds);

      setTargets(exercise, mappingsByExerciseIds);
    }
  }

  private void setComputedAttributesWithEmptyGlobalScore(List<ExerciseSimple> originalExercises) {
    List<ExerciseSimple> exercises = getExercisesWithId(originalExercises);
    if (exercises.isEmpty()) {
      return;
    }

    MappingsByExerciseIds mappingsByExerciseIds =
        getResultsByExerciseIds(getExerciseIds(exercises));

    for (ExerciseSimple exercise : exercises) {
      exercise.setExpectationResultByTypes(new ArrayList<>());

      setTargets(exercise, mappingsByExerciseIds);
    }
  }

  private static List<ExerciseSimple> getExercisesWithId(List<ExerciseSimple> exercises) {
    return exercises.stream().filter(exercise -> exercise.getId() != null).toList();
  }

  private static Set<String> getExerciseIds(List<ExerciseSimple> exercises) {
    return exercises.stream().map(ExerciseSimple::getId).collect(Collectors.toSet());
  }

  private MappingsByExerciseIds getResultsByExerciseIds(Set<String> exerciseIds) {
    Map<String, List<Object[]>> teamsByExerciseIds =
        getTeamsOrAssetsOrAssetGroupsByExerciseIds(teamRepository.teamsByExerciseIds(exerciseIds));

    Map<String, List<Object[]>> assetsByExerciseIds =
        getTeamsOrAssetsOrAssetGroupsByExerciseIds(
            assetRepository.assetsByExerciseIds(exerciseIds));

    Map<String, List<Object[]>> assetGroupByExerciseIds =
        getTeamsOrAssetsOrAssetGroupsByExerciseIds(
            assetGroupRepository.assetGroupsByExerciseIds(exerciseIds));

    return new MappingsByExerciseIds(
        teamsByExerciseIds, assetsByExerciseIds, assetGroupByExerciseIds);
  }

  private Map<String, List<Object[]>> getTeamsOrAssetsOrAssetGroupsByExerciseIds(
      List<Object[]> rawTeamsOrAssetsOrAssetGroups) {
    return ofNullable(rawTeamsOrAssetsOrAssetGroups).orElse(emptyList()).stream()
        .filter(
            rawTeamOrAssetOrAssetGroup ->
                0 < rawTeamOrAssetOrAssetGroup.length && rawTeamOrAssetOrAssetGroup[0] != null)
        .collect(
            Collectors.groupingBy(
                rawTeamOrAssetOrAssetGroup -> (String) rawTeamOrAssetOrAssetGroup[0]));
  }

  private record MappingsByExerciseIds(
      Map<String, List<Object[]>> teamsByExerciseIds,
      Map<String, List<Object[]>> assetsByExerciseIds,
      Map<String, List<Object[]>> assetGroupsByExerciseIds) {}

  private Map<String, List<RawInjectExpectation>> getExpectationsByExerciseId(
      Set<String> exerciseIds) {
    return ofNullable(injectExpectationRepository.rawForComputeGlobalByExerciseIds(exerciseIds))
        .orElse(emptyList())
        .stream()
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(RawInjectExpectation::getExercise_id));
  }

  private void setGlobalScore(
      ExerciseSimple exercise, Map<String, List<RawInjectExpectation>> expectationsByExerciseIds) {
    List<RawInjectExpectation> expectations =
        expectationsByExerciseIds.getOrDefault(exercise.getId(), emptyList());
    HashSet<String> injectIds = new HashSet<>(Arrays.asList(exercise.getInjectIds()));

    exercise.setExpectationResultByTypes(
        injectExpectationMapper.extractExpectationResultByTypesFromRaw(injectIds, expectations));
  }

  private void setTargets(ExerciseSimple exercise, MappingsByExerciseIds mappingsByExerciseIds) {
    List<TargetSimple> allTargets =
        Stream.of(
                getTargets(exercise, mappingsByExerciseIds.teamsByExerciseIds, TargetType.TEAMS)
                    .stream(),
                getTargets(exercise, mappingsByExerciseIds.assetsByExerciseIds, TargetType.ASSETS)
                    .stream(),
                getTargets(
                    exercise,
                    mappingsByExerciseIds.assetGroupsByExerciseIds,
                    TargetType.ASSETS_GROUPS)
                    .stream())
            .flatMap(Function.identity())
            .toList();
    exercise.getTargets().addAll(allTargets);
  }

  private List<TargetSimple> getTargets(
      ExerciseSimple exercise,
      Map<String, List<Object[]>> targetsByExerciseIds,
      TargetType targetType) {
    return injectMapper.toTargetSimple(
        targetsByExerciseIds.getOrDefault(exercise.getId(), emptyList()), targetType);
  }

  // -- SCENARIO EXERCISES --
  public Iterable<ExerciseSimple> scenarioExercises(@NotBlank String scenarioId) {
    List<RawExerciseSimple> exercises = exerciseRepository.rawAllByScenarioIds(List.of(scenarioId));
    return exerciseMapper.getExerciseSimples(exercises);
  }

  // -- GLOBAL RESULTS --
  public List<ExpectationResultsByType> getGlobalResults(@NotBlank String exerciseId) {
    return resultUtils.computeGlobalExpectationResults(
        exerciseRepository.findInjectsByExercise(exerciseId));
  }

  public ExercisesGlobalScoresOutput getExercisesGlobalScores(ExercisesGlobalScoresInput input) {
    Map<String, List<ExpectationResultsByType>> globalScoresByExerciseIds =
        input.exerciseIds().stream()
            .collect(Collectors.toMap(Function.identity(), this::getGlobalResults));
    return new ExercisesGlobalScoresOutput(globalScoresByExerciseIds);
  }

  // -- TEAMS --
  @Transactional(rollbackFor = Exception.class)
  public Iterable<TeamOutput> removeTeams(
      @NotBlank final String exerciseId, @NotNull final List<String> teamIds) {
    // Remove teams from exercise
    this.exerciseRepository.removeTeams(exerciseId, teamIds);
    // Remove all association between users / exercises / teams
    this.exerciseTeamUserRepository.deleteTeamsFromAllReferences(teamIds);
    // Remove all association between injects and teams
    this.injectRepository.removeTeamsForExercise(exerciseId, teamIds);
    // Remove all association between lessons learned and teams
    this.lessonsCategoryRepository.removeTeamsForExercise(exerciseId, teamIds);
    return teamService.find(fromIds(teamIds));
  }

  public List<TeamOutput> replaceTeams(
      @NotBlank final String exerciseId, @NotNull final List<String> teamIds) {
    Exercise exercise = this.exercise(exerciseId);
    List<String> previousTeamIds = exercise.getTeams().stream().map(Team::getId).toList();

    // Replace teams from exercise
    List<Team> teams = fromIterable(this.teamRepository.findAllById(teamIds));
    exercise.setTeams(teams);

    List<String> teamIdsAdded =
        teamIds.stream().filter(id -> !previousTeamIds.contains(id)).toList();

    List<Team> teamsAdded = fromIterable(this.teamRepository.findAllById(teamIdsAdded));

    // Enable user
    teamsAdded.forEach(
        team -> {
          List<String> playerIds = team.getUsers().stream().map(User::getId).toList();
          this.enablePlayers(exerciseId, team, playerIds);
        });

    // You must return all the modified teams to ensure the frontend store updates correctly
    List<String> modifiedTeamIds =
        Stream.concat(previousTeamIds.stream(), teams.stream().map(Team::getId))
            .distinct()
            .toList();
    return teamService.find(fromIds(modifiedTeamIds));
  }

  public Exercise enablePlayers(
      @NotBlank final String exerciseId,
      @NotBlank final Team team,
      @NotNull final List<String> playerIds) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    playerIds.forEach(
        playerId -> {
          ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
          exerciseTeamUser.setExercise(exercise);
          exerciseTeamUser.setTeam(team);
          exerciseTeamUser.setUser(this.userRepository.findById(playerId).orElseThrow());
          this.exerciseTeamUserRepository.save(exerciseTeamUser);
        });
    return exercise;
  }

  /**
   * Update the simulation and each of the injects to add default asset groups
   *
   * @param exercise
   * @param currentTags list of the tags before the update
   * @return
   */
  @Transactional
  public Exercise updateExercice(
      @NotNull final Exercise exercise, @NotNull final Set<Tag> currentTags, boolean applyRule) {
    if (applyRule) {
      // Get asset groups from the TagRule of the added tags
      List<AssetGroup> defaultAssetGroupsToAdd =
          tagRuleService.getAssetGroupsFromTagIds(
              exercise.getTags().stream()
                  .filter(tag -> !currentTags.contains(tag))
                  .map(Tag::getId)
                  .toList());

      // Add the default asset groups to the injects
      exercise.getInjects().stream()
          .filter(inject -> this.injectService.canApplyTargetType(inject, TargetType.ASSETS_GROUPS))
          .forEach(
              inject ->
                  injectService.applyDefaultAssetGroupsToInject(
                      inject.getId(), defaultAssetGroupsToAdd));
    }
    exercise.setUpdatedAt(now());
    return exerciseRepository.save(exercise);
  }

  public Exercise previousFinishedSimulation(
      @NotBlank final String scenarioId, @NotNull final Instant instant) {
    return this.exerciseRepository
        .findAll(fromScenario(scenarioId).and(finished()).and(closestBefore(instant)))
        .stream()
        .findFirst()
        .orElse(null);
  }

  public boolean isFinished(Exercise exercise) {
    return ExerciseStatus.FINISHED.equals(exercise.getStatus());
  }

  public boolean isThereAScoreDegradation(
      Map<ExpectationType, ExpectationResultsByType> lastSimulationResultsMap,
      Map<ExpectationType, ExpectationResultsByType> secondLastSimulationResultsMap) {

    for (Map.Entry<ExpectationType, ExpectationResultsByType> entry :
        lastSimulationResultsMap.entrySet()) {
      ExpectationResultsByType lastSimulationResultsByType = entry.getValue();
      ExpectationType type = entry.getKey();

      // we ignore manual expectation
      if (ExpectationType.HUMAN_RESPONSE.equals(type)) {
        break;
      }

      ExpectationResultsByType secondLastSimulationResultsByType =
          secondLastSimulationResultsMap.get(type);

      // we ignore if one of the 2 expectation is still PENDING
      if (InjectExpectation.EXPECTATION_STATUS.PENDING.equals(
              lastSimulationResultsByType.avgResult())
          || InjectExpectation.EXPECTATION_STATUS.PENDING.equals(
              secondLastSimulationResultsByType.avgResult())) {
        break;
      }

      float lastSimulationScore =
          ScenarioStatisticService.getRoundedPercentage(lastSimulationResultsByType);
      float secondLastSimulationScore =
          ScenarioStatisticService.getRoundedPercentage(secondLastSimulationResultsByType);
      if (lastSimulationScore < secondLastSimulationScore) {
        return true;
      }
    }
    return false;
  }

  // -- OPTION --

  public List<FilterUtilsJpa.Option> findAllAsOptions(
      final Specification<Exercise> specification, final String searchText) {
    return fromIterable(
            exerciseRepository.findAll(
                specification.and(byName(searchText)), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
