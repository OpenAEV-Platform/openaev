package io.openaev.rest.lessons;

import static io.openaev.helper.StreamHelper.fromIterable;
import static java.time.Instant.now;

import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.database.specification.LessonsCategorySpecification;
import io.openaev.database.specification.LessonsQuestionSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.lessons.form.*;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioLessonsApi extends RestBehavior {

  public static final String SCENARIO_URI = "/api/scenarios/";

  private final ScenarioRepository scenarioRepository;
  private final TeamRepository teamRepository;
  private final LessonsTemplateRepository lessonsTemplateRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final LessonsAnswerRepository lessonsAnswerRepository;
  private final UserRepository userRepository;

  @GetMapping(SCENARIO_URI + "{scenarioId}/lessons_categories")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<LessonsCategory> scenarioLessonsCategories(@PathVariable String scenarioId) {
    return lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromScenario(scenarioId));
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/lessons_apply_template/{lessonsTemplateId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<LessonsCategory> applyScenarioLessonsTemplate(
      @PathVariable String scenarioId, @PathVariable String lessonsTemplateId) {
    Scenario scenario =
        scenarioRepository.findById(scenarioId).orElseThrow(ElementNotFoundException::new);
    LessonsTemplate lessonsTemplate =
        lessonsTemplateRepository
            .findById(lessonsTemplateId)
            .orElseThrow(ElementNotFoundException::new);
    List<LessonsTemplateCategory> lessonsTemplateCategories =
        lessonsTemplate.getCategories().stream().toList();
    for (LessonsTemplateCategory lessonsTemplateCategory : lessonsTemplateCategories) {
      LessonsCategory lessonsCategory = new LessonsCategory();
      lessonsCategory.setScenario(scenario);
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
    return lessonsCategoryRepository.findAll(LessonsCategorySpecification.fromScenario(scenarioId));
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/lessons_categories")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public LessonsCategory createScenarioLessonsCategory(
      @PathVariable String scenarioId, @Valid @RequestBody LessonsCategoryCreateInput input) {
    Scenario scenario =
        scenarioRepository.findById(scenarioId).orElseThrow(ElementNotFoundException::new);
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setUpdateAttributes(input);
    lessonsCategory.setScenario(scenario);
    return lessonsCategoryRepository.save(lessonsCategory);
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/lessons_empty")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<LessonsCategory> emptyScenarioLessons(@PathVariable String scenarioId) {
    List<LessonsCategory> lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromScenario(scenarioId))
            .stream()
            .toList();
    lessonsCategoryRepository.deleteAll(lessonsCategories);
    lessonsCategories =
        lessonsCategoryRepository
            .findAll(LessonsCategorySpecification.fromScenario(scenarioId))
            .stream()
            .toList();
    return lessonsCategories;
  }

  @PutMapping(SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public LessonsCategory updateScenarioLessonsCategory(
      @PathVariable String scenarioId,
      @PathVariable String lessonsCategoryId,
      @Valid @RequestBody LessonsCategoryUpdateInput input) {
    LessonsCategory lessonsTemplateCategory =
        lessonsCategoryRepository
            .findById(lessonsCategoryId)
            .orElseThrow(ElementNotFoundException::new);
    lessonsTemplateCategory.setUpdateAttributes(input);
    lessonsTemplateCategory.setUpdated(now());
    return lessonsCategoryRepository.save(lessonsTemplateCategory);
  }

  @DeleteMapping(SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public void deleteScenarioLessonsCategory(
      @PathVariable String scenarioId, @PathVariable String lessonsCategoryId) {
    lessonsCategoryRepository.deleteById(lessonsCategoryId);
  }

  @PutMapping(SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}/teams")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public LessonsCategory updateScenarioLessonsCategoryTeams(
      @PathVariable String scenarioId,
      @PathVariable String lessonsCategoryId,
      @Valid @RequestBody LessonsCategoryTeamsInput input) {
    LessonsCategory lessonsCategory =
        lessonsCategoryRepository
            .findById(lessonsCategoryId)
            .orElseThrow(ElementNotFoundException::new);
    Iterable<Team> lessonsCategoryTeams = teamRepository.findAllById(input.getTeamIds());
    lessonsCategory.setTeams(fromIterable(lessonsCategoryTeams));
    return lessonsCategoryRepository.save(lessonsCategory);
  }

  @GetMapping(SCENARIO_URI + "{scenarioId}/lessons_questions")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<LessonsQuestion> scenarioLessonsQuestions(@PathVariable String scenarioId) {
    return lessonsCategoryRepository
        .findAll(LessonsCategorySpecification.fromScenario(scenarioId))
        .stream()
        .flatMap(
            lessonsCategory ->
                lessonsQuestionRepository
                    .findAll(LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                    .stream())
        .toList();
  }

  @GetMapping(
      SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}/lessons_questions")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<LessonsQuestion> scenarioLessonsCategoryQuestions(
      @PathVariable String scenarioId, @PathVariable String lessonsCategoryId) {
    return lessonsQuestionRepository.findAll(
        LessonsQuestionSpecification.fromCategory(lessonsCategoryId));
  }

  @PostMapping(
      SCENARIO_URI + "{scenarioId}/lessons_categories/{lessonsCategoryId}/lessons_questions")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public LessonsQuestion createScenarioLessonsQuestion(
      @PathVariable String scenarioId,
      @PathVariable String lessonsCategoryId,
      @Valid @RequestBody LessonsQuestionCreateInput input) {
    LessonsCategory lessonsCategory =
        lessonsCategoryRepository
            .findById(lessonsCategoryId)
            .orElseThrow(ElementNotFoundException::new);
    LessonsQuestion lessonsQuestion = new LessonsQuestion();
    lessonsQuestion.setUpdateAttributes(input);
    lessonsQuestion.setCategory(lessonsCategory);
    return lessonsQuestionRepository.save(lessonsQuestion);
  }

  @PutMapping(
      SCENARIO_URI
          + "{scenarioId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public LessonsQuestion updateScenarioLessonsQuestion(
      @PathVariable String scenarioId,
      @PathVariable String lessonsQuestionId,
      @Valid @RequestBody LessonsQuestionUpdateInput input) {
    LessonsQuestion lessonsQuestion =
        lessonsQuestionRepository
            .findById(lessonsQuestionId)
            .orElseThrow(ElementNotFoundException::new);
    lessonsQuestion.setUpdateAttributes(input);
    lessonsQuestion.setUpdated(now());
    return lessonsQuestionRepository.save(lessonsQuestion);
  }

  @DeleteMapping(
      SCENARIO_URI
          + "{scenarioId}/lessons_categories/{lessonsCategoryId}/lessons_questions/{lessonsQuestionId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public void deleteScenarioLessonsQuestion(
      @PathVariable String scenarioId, @PathVariable String lessonsQuestionId) {
    lessonsQuestionRepository.deleteById(lessonsQuestionId);
  }
}
