package io.openaev.rest.lessons;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.lessons.LessonsCategoryHydration.hydrateForResponse;
import static java.time.Instant.now;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UrlAccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.database.specification.LessonsAnswerSpecification;
import io.openaev.database.specification.LessonsCategorySpecification;
import io.openaev.database.specification.LessonsQuestionSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.lessons.form.*;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.MailingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ExerciseLessonsApi extends RestBehavior {

  public static final String EXERCISE_URL = "/api/exercises/";
  private static final String TENANT_EXERCISE_URL = TENANT_PREFIX + "/exercises/";

  private final ExerciseService exerciseService;
  private final TeamRepository teamRepository;
  private final LessonsTemplateRepository lessonsTemplateRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final LessonsAnswerRepository lessonsAnswerRepository;
  private final UserRepository userRepository;
  private final MailingService mailingService;

  @GetMapping({
    EXERCISE_URL + "{exerciseId}/lessons_categories",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_categories"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<LessonsCategory> exerciseLessonsCategories(
      TxCtx ctx, @PathVariable String exerciseId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    return hydrateForResponse(
        lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromExercise(exerciseId)));
  }

  @PostMapping({
    EXERCISE_URL + "{exerciseId}/lessons_apply_template/{lessonsTemplateId}",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_apply_template/{lessonsTemplateId}"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  // TxCtx scopes the template lookup so a cross-tenant template is not found. Not used directly.
  public Iterable<LessonsCategory> applyExerciseLessonsTemplate(
      TxCtx ctx, @PathVariable String exerciseId, @PathVariable String lessonsTemplateId) {
    Exercise exercise =
        exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    LessonsTemplate lessonsTemplate =
        lessonsTemplateRepository
            .findById(lessonsTemplateId)
            .orElseThrow(ElementNotFoundException::new);
    List<LessonsTemplateCategory> lessonsTemplateCategories =
        lessonsTemplate.getCategories().stream().toList();
    for (LessonsTemplateCategory lessonsTemplateCategory : lessonsTemplateCategories) {
      LessonsCategory lessonsCategory = new LessonsCategory();
      lessonsCategory.setExercise(exercise);
      lessonsCategory.setName(lessonsTemplateCategory.getName());
      lessonsCategory.setDescription(lessonsTemplateCategory.getDescription());
      lessonsCategory.setOrder(lessonsTemplateCategory.getOrder());
      lessonsCategoryRepository.save(lessonsCategory);
      List<LessonsQuestion> lessonsQuestions =
          lessonsTemplateCategory.getQuestions().stream()
              .map(
                  lessonsTemplateQuestion -> {
                    LessonsQuestion lessonsQuestion = new LessonsQuestion();
                    lessonsQuestion.setCategory(lessonsCategory);
                    lessonsQuestion.setContent(lessonsTemplateQuestion.getContent());
                    lessonsQuestion.setExplanation(lessonsTemplateQuestion.getExplanation());
                    lessonsQuestion.setOrder(lessonsTemplateQuestion.getOrder());
                    return lessonsQuestion;
                  })
              .toList();
      lessonsQuestionRepository.saveAll(lessonsQuestions);
    }
    return hydrateForResponse(
        lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromExercise(exerciseId)));
  }

  @PostMapping({
    EXERCISE_URL + "{exerciseId}/lessons_categories",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_categories"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public LessonsCategory createExerciseLessonsCategory(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @Valid @RequestBody LessonsCategoryCreateInput input) {
    Exercise exercise =
        exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setUpdateAttributes(input);
    lessonsCategory.setExercise(exercise);
    return lessonsCategoryRepository.save(lessonsCategory);
  }

  @PostMapping({
    EXERCISE_URL + "{exerciseId}/lessons_answers_reset",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_answers_reset"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Iterable<LessonsCategory> resetExerciseLessonsAnswers(
      TxCtx ctx, @PathVariable String exerciseId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    List<LessonsAnswer> lessonsAnswers =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
            .stream()
            .flatMap(
                lessonsCategory ->
                    lessonsQuestionRepository
                        .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                        .stream()
                        .flatMap(
                            lessonsQuestion ->
                                lessonsAnswerRepository
                                    .findAll(
                                        LessonsAnswerSpecification.fromQuestion(
                                            lessonsQuestion.getId()))
                                    .stream()))
            .toList();
    lessonsAnswerRepository.deleteAll(lessonsAnswers);
    return hydrateForResponse(
        lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromExercise(exerciseId)));
  }

  @PostMapping({
    EXERCISE_URL + "{exerciseId}/lessons_empty",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_empty"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Iterable<LessonsCategory> emptyExerciseLessons(
      TxCtx ctx, @PathVariable String exerciseId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    List<LessonsCategory> lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
            .stream()
            .toList();
    lessonsCategoryRepository.deleteAll(lessonsCategories);
    lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
            .stream()
            .toList();
    return hydrateForResponse(lessonsCategories);
  }

  @PutMapping({
    EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public LessonsCategory updateExerciseLessonsCategory(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String lessonsCategoryId,
      @Valid @RequestBody LessonsCategoryUpdateInput input) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    LessonsCategory lessonsTemplateCategory = categoryOfExercise(exerciseId, lessonsCategoryId);
    lessonsTemplateCategory.setUpdateAttributes(input);
    lessonsTemplateCategory.setUpdated(now());
    return hydrateForResponse(lessonsCategoryRepository.save(lessonsTemplateCategory));
  }

  @DeleteMapping({
    EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void deleteExerciseLessonsCategory(
      TxCtx ctx, @PathVariable String exerciseId, @PathVariable String lessonsCategoryId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    lessonsCategoryRepository.delete(categoryOfExercise(exerciseId, lessonsCategoryId));
  }

  @PutMapping({
    EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}/teams",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}/teams"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public LessonsCategory updateExerciseLessonsCategoryTeams(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String lessonsCategoryId,
      @Valid @RequestBody LessonsCategoryTeamsInput input) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    LessonsCategory lessonsCategory = categoryOfExercise(exerciseId, lessonsCategoryId);
    Iterable<Team> lessonsCategoryTeams = teamRepository.findAllById(input.getTeamIds());
    lessonsCategory.setTeams(fromIterable(lessonsCategoryTeams));
    return hydrateForResponse(lessonsCategoryRepository.save(lessonsCategory));
  }

  @GetMapping({
    EXERCISE_URL + "{exerciseId}/lessons_questions",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_questions"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<LessonsQuestion> exerciseLessonsQuestions(
      TxCtx ctx, @PathVariable String exerciseId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream())
        .toList();
  }

  @GetMapping({
    EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<LessonsQuestion> exerciseLessonsCategoryQuestions(
      TxCtx ctx, @PathVariable String exerciseId, @PathVariable String lessonsCategoryId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    categoryOfExercise(exerciseId, lessonsCategoryId);
    return lessonsQuestionRepository.findAll(
        LessonsQuestionSpecification.fromCategory(lessonsCategoryId));
  }

  @PostMapping({
    EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public LessonsQuestion createExerciseLessonsQuestion(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String lessonsCategoryId,
      @Valid @RequestBody LessonsQuestionCreateInput input) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    LessonsCategory lessonsCategory = categoryOfExercise(exerciseId, lessonsCategoryId);
    LessonsQuestion lessonsQuestion = new LessonsQuestion();
    lessonsQuestion.setUpdateAttributes(input);
    lessonsQuestion.setCategory(lessonsCategory);
    return lessonsQuestionRepository.save(lessonsQuestion);
  }

  @PutMapping({
    EXERCISE_URL
        + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}",
    TENANT_EXERCISE_URL
        + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public LessonsQuestion updateExerciseLessonsQuestion(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String lessonsCategoryId,
      @PathVariable String lessonsQuestionId,
      @Valid @RequestBody LessonsQuestionUpdateInput input) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    LessonsQuestion lessonsQuestion =
        questionOfCategory(exerciseId, lessonsCategoryId, lessonsQuestionId);
    lessonsQuestion.setUpdateAttributes(input);
    lessonsQuestion.setUpdated(now());
    return lessonsQuestionRepository.save(lessonsQuestion);
  }

  @DeleteMapping({
    EXERCISE_URL
        + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}",
    TENANT_EXERCISE_URL
        + "{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void deleteExerciseLessonsQuestion(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String lessonsCategoryId,
      @PathVariable String lessonsQuestionId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    lessonsQuestionRepository.delete(
        questionOfCategory(exerciseId, lessonsCategoryId, lessonsQuestionId));
  }

  @PostMapping({
    EXERCISE_URL + "{exerciseId}/lessons_send",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_send"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void sendExerciseLessons(
      // Unused by the handler body; TenantScopeTransactionAspect reads it to set the tenant scope
      // for the transaction (MailingService#sendEmail resolves the email injector contract's
      // linked injector, v2 tenant-scoped through the injectors table; without a scope the
      // association is empty and the send throws IllegalStateException).
      TxCtx ctx, @PathVariable String exerciseId, @Valid @RequestBody LessonsSendInput input) {
    Exercise exercise =
        exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    List<LessonsCategory> lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
            .stream()
            .toList();
    List<User> users =
        lessonsCategories.stream()
            .flatMap(
                lessonsCategory ->
                    lessonsCategory.getTeams().stream().flatMap(team -> team.getUsers().stream()))
            .distinct()
            .toList();
    mailingService.sendEmail(input.getSubject(), input.getBody(), users, Optional.of(exercise));
  }

  @GetMapping({
    EXERCISE_URL + "{exerciseId}/lessons_answers",
    TENANT_EXERCISE_URL + "{exerciseId}/lessons_answers"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<LessonsAnswer> exerciseLessonsAnswers(
      TxCtx ctx, @PathVariable String exerciseId, @RequestParam Optional<String> userId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream()
                    .flatMap(
                        lessonsQuestion ->
                            lessonsAnswerRepository
                                .findAll(
                                    LessonsAnswerSpecification.fromQuestion(
                                        lessonsQuestion.getId()))
                                .stream()))
        .toList();
  }

  @GetMapping({
    "/api/player/lessons/exercise/{exerciseId}/lessons_categories",
    TENANT_PREFIX + "/player/lessons/exercise/{exerciseId}/lessons_categories"
  })
  @Transactional
  @AccessControl(skipRBAC = true)
  @UrlAccessControl(exerciseId = "#exerciseId", userId = "#userId")
  public List<LessonsCategory> playerLessonsCategories(
      TxCtx ctx, @PathVariable String exerciseId, @RequestParam Optional<String> userId)
      throws AuthenticationError {
    impersonateUser(userRepository, userId); // Protection for ?
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
        .stream()
        .filter(
            lessonsCategory ->
                userId.isEmpty()
                    || lessonsCategory.getTeams().stream()
                        .anyMatch(
                            team ->
                                team.getUsers().stream()
                                    .anyMatch(user -> user.getId().equals(userId.get()))))
        .toList();
  }

  @GetMapping({
    "/api/player/lessons/exercise/{exerciseId}/lessons_questions",
    TENANT_PREFIX + "/player/lessons/exercise/{exerciseId}/lessons_questions"
  })
  @Transactional
  @AccessControl(skipRBAC = true)
  @UrlAccessControl(exerciseId = "#exerciseId", userId = "#userId")
  public List<LessonsQuestion> playerLessonsQuestions(
      TxCtx ctx, @PathVariable String exerciseId, @RequestParam Optional<String> userId)
      throws AuthenticationError {
    impersonateUser(userRepository, userId); // Protection for ?
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream())
        .toList();
  }

  @GetMapping({
    "/api/player/lessons/exercise/{exerciseId}/lessons_answers",
    TENANT_PREFIX + "/player/lessons/exercise/{exerciseId}/lessons_answers"
  })
  @Transactional
  @AccessControl(skipRBAC = true)
  @UrlAccessControl(exerciseId = "#exerciseId", userId = "#userId")
  public List<LessonsAnswer> playerLessonsAnswers(
      TxCtx ctx, @PathVariable String exerciseId, @RequestParam Optional<String> userId)
      throws AuthenticationError {
    impersonateUser(userRepository, userId); // Protection for ?
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream()
                    .flatMap(
                        lessonsQuestion ->
                            lessonsAnswerRepository
                                .findAll(
                                    LessonsAnswerSpecification.fromQuestion(
                                        lessonsQuestion.getId()))
                                .stream()))
        .filter(
            lessonsAnswer ->
                userId.isEmpty() || lessonsAnswer.getUser().getId().equals(userId.get()))
        .toList();
  }

  @PostMapping({
    "/api/player/lessons/exercise/{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}/lessons_answers",
    TENANT_PREFIX
        + "/player/lessons/exercise/{exerciseId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}/lessons_answers"
  })
  @Transactional
  @AccessControl(skipRBAC = true)
  @UrlAccessControl(exerciseId = "#exerciseId", userId = "#userId")
  public LessonsAnswer createExerciseLessonsQuestion(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String lessonsQuestionId,
      @Valid @RequestBody LessonsAnswerCreateInput input,
      @RequestParam Optional<String> userId)
      throws AuthenticationError {
    User user = impersonateUser(userRepository, userId);
    LessonsQuestion lessonsQuestion =
        lessonsQuestionRepository
            .findById(lessonsQuestionId)
            .orElseThrow(ElementNotFoundException::new);

    Optional<LessonsAnswer> optionalAnswer =
        lessonsAnswerRepository.findByUserIdAndQuestionId(user.getId(), lessonsQuestionId);
    LessonsAnswer lessonsAnswer =
        optionalAnswer.orElseGet(
            () -> {
              LessonsAnswer newAnswer = new LessonsAnswer();
              newAnswer.setQuestion(lessonsQuestion);
              newAnswer.setUser(user);
              return newAnswer;
            });
    lessonsAnswer.setScore(input.getScore());
    lessonsAnswer.setPositive(input.getPositive());
    lessonsAnswer.setNegative(input.getNegative());

    return lessonsAnswerRepository.save(lessonsAnswer);
  }

  /** The category, only when it belongs to the given simulation. */
  private LessonsCategory categoryOfExercise(String exerciseId, String lessonsCategoryId) {
    return lessonsCategoryRepository
        .findById(lessonsCategoryId)
        .filter(
            category ->
                category.getExercise() != null && exerciseId.equals(category.getExercise().getId()))
        .orElseThrow(ElementNotFoundException::new);
  }

  /** The question, only when it belongs to the category of the path, itself in the simulation. */
  private LessonsQuestion questionOfCategory(
      String exerciseId, String lessonsCategoryId, String lessonsQuestionId) {
    return lessonsQuestionRepository
        .findById(lessonsQuestionId)
        .filter(
            question ->
                question.getCategory() != null
                    && lessonsCategoryId.equals(question.getCategory().getId())
                    && question.getCategory().getExercise() != null
                    && exerciseId.equals(question.getCategory().getExercise().getId()))
        .orElseThrow(ElementNotFoundException::new);
  }
}
