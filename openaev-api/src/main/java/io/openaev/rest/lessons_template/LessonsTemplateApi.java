package io.openaev.rest.lessons_template;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static java.time.Instant.now;

import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.repository.LessonsTemplateCategoryRepository;
import io.openaev.database.repository.LessonsTemplateQuestionRepository;
import io.openaev.database.repository.LessonsTemplateRepository;
import io.openaev.database.specification.LessonsTemplateCategorySpecification;
import io.openaev.database.specification.LessonsTemplateQuestionSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.lessons_template.form.LessonsTemplateCategoryInput;
import io.openaev.rest.lessons_template.form.LessonsTemplateInput;
import io.openaev.rest.lessons_template.form.LessonsTemplateQuestionInput;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class LessonsTemplateApi extends RestBehavior {

  public static final String LESSON_TEMPLATE_URI = "/api/lessons_templates";

  private final LessonsTemplateRepository lessonsTemplateRepository;
  private final LessonsTemplateCategoryRepository lessonsTemplateCategoryRepository;
  private final LessonsTemplateQuestionRepository lessonsTemplateQuestionRepository;

  // -- LESSONS TEMPLATES --

  @PostMapping(LESSON_TEMPLATE_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.LESSON_LEARNED)
  @Transactional(rollbackOn = Exception.class)
  public LessonsTemplate createLessonsTemplate(@Valid @RequestBody LessonsTemplateInput input) {
    LessonsTemplate lessonsTemplate = new LessonsTemplate();
    lessonsTemplate.setUpdateAttributes(input);
    return lessonsTemplateRepository.save(lessonsTemplate);
  }

  @GetMapping(LESSON_TEMPLATE_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.LESSON_LEARNED)
  public Iterable<LessonsTemplate> lessonsTemplates() {
    return fromIterable(lessonsTemplateRepository.findAll()).stream().toList();
  }

  @PostMapping(LESSON_TEMPLATE_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.LESSON_LEARNED)
  public Page<LessonsTemplate> lessonsTemplates(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        this.lessonsTemplateRepository::findAll, searchPaginationInput, LessonsTemplate.class);
  }

  @PutMapping(LESSON_TEMPLATE_URI + "/{lessonsTemplateId}")
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.LESSON_LEARNED)
  public LessonsTemplate updateLessonsTemplate(
      @PathVariable String lessonsTemplateId, @Valid @RequestBody LessonsTemplateInput input) {
    LessonsTemplate lessonsTemplate =
        lessonsTemplateRepository
            .findById(lessonsTemplateId)
            .orElseThrow(ElementNotFoundException::new);
    lessonsTemplate.setUpdateAttributes(input);
    lessonsTemplate.setUpdated(now());
    return lessonsTemplateRepository.save(lessonsTemplate);
  }

  @DeleteMapping(LESSON_TEMPLATE_URI + "/{lessonsTemplateId}")
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.LESSON_LEARNED)
  public void deleteLessonsTemplate(@PathVariable String lessonsTemplateId) {
    lessonsTemplateRepository.deleteById(lessonsTemplateId);
  }

  // -- LESSONS TEMPLATES CATEGORIES --

  public static final String LESSON_CATEGORY_URI =
      LESSON_TEMPLATE_URI + "/{lessonsTemplateId}/lessons_template_categories";

  @PostMapping(LESSON_CATEGORY_URI)
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.LESSON_LEARNED)
  @Transactional(rollbackOn = Exception.class)
  public LessonsTemplateCategory createLessonsTemplateCategory(
      @PathVariable String lessonsTemplateId,
      @Valid @RequestBody LessonsTemplateCategoryInput input) {
    LessonsTemplate lessonsTemplate =
        lessonsTemplateRepository
            .findById(lessonsTemplateId)
            .orElseThrow(ElementNotFoundException::new);
    LessonsTemplateCategory lessonsTemplateCategory = new LessonsTemplateCategory();
    lessonsTemplateCategory.setUpdateAttributes(input);
    lessonsTemplateCategory.setTemplate(lessonsTemplate);
    return lessonsTemplateCategoryRepository.save(lessonsTemplateCategory);
  }

  @GetMapping(LESSON_CATEGORY_URI)
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.LESSON_LEARNED)
  public Iterable<LessonsTemplateCategory> lessonsTemplateCategories(
      @PathVariable String lessonsTemplateId) {
    return lessonsTemplateCategoryRepository.findAll(
        LessonsTemplateCategorySpecification.fromTemplate(lessonsTemplateId));
  }

  @PutMapping(LESSON_CATEGORY_URI + "/{lessonsTemplateCategoryId}")
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.LESSON_LEARNED)
  @Transactional(rollbackOn = Exception.class)
  public LessonsTemplateCategory updateLessonsTemplateCategory(
      @PathVariable String lessonsTemplateId,
      @PathVariable String lessonsTemplateCategoryId,
      @Valid @RequestBody LessonsTemplateCategoryInput input) {
    LessonsTemplateCategory lessonsTemplateCategory =
        lessonsTemplateCategoryRepository
            .findById(lessonsTemplateCategoryId)
            .orElseThrow(ElementNotFoundException::new);
    lessonsTemplateCategory.setUpdateAttributes(input);
    lessonsTemplateCategory.setUpdated(now());
    return lessonsTemplateCategoryRepository.save(lessonsTemplateCategory);
  }

  @DeleteMapping(LESSON_CATEGORY_URI + "/{lessonsTemplateCategoryId}")
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.LESSON_LEARNED)
  public void deleteLessonsTemplateCategory(
      @PathVariable String lessonsTemplateId, @PathVariable String lessonsTemplateCategoryId) {
    lessonsTemplateCategoryRepository.deleteById(lessonsTemplateCategoryId);
  }

  // -- LESSONS TEMPLATES QUESTIONS --

  @GetMapping(LESSON_TEMPLATE_URI + "/{lessonsTemplateId}/lessons_template_questions")
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.LESSON_LEARNED)
  public Iterable<LessonsTemplateQuestion> lessonsTemplateQuestions(
      @PathVariable String lessonsTemplateId) {
    return lessonsTemplateCategoryRepository
        .findAll(LessonsTemplateCategorySpecification.fromTemplate(lessonsTemplateId))
        .stream()
        .flatMap(
            lessonsTemplateCategory ->
                lessonsTemplateQuestionRepository
                    .findAll(
                        LessonsTemplateQuestionSpecification.fromCategory(
                            lessonsTemplateCategory.getId()))
                    .stream())
        .toList();
  }

  public static final String LESSON_QUESTION_URI =
      LESSON_CATEGORY_URI + "/{lessonsTemplateCategoryId}/lessons_template_questions";

  @PostMapping(LESSON_QUESTION_URI)
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.LESSON_LEARNED)
  public LessonsTemplateQuestion createLessonsTemplateQuestion(
      @PathVariable String lessonsTemplateId,
      @PathVariable String lessonsTemplateCategoryId,
      @Valid @RequestBody LessonsTemplateQuestionInput input) {
    LessonsTemplateCategory lessonsTemplateCategory =
        lessonsTemplateCategoryRepository
            .findById(lessonsTemplateCategoryId)
            .orElseThrow(ElementNotFoundException::new);
    LessonsTemplateQuestion lessonsTemplateQuestion = new LessonsTemplateQuestion();
    lessonsTemplateQuestion.setUpdateAttributes(input);
    lessonsTemplateQuestion.setCategory(lessonsTemplateCategory);
    return lessonsTemplateQuestionRepository.save(lessonsTemplateQuestion);
  }

  @GetMapping(LESSON_QUESTION_URI)
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.LESSON_LEARNED)
  public Iterable<LessonsTemplateQuestion> lessonsTemplateCategoryQuestions(
      @PathVariable String lessonsTemplateId, @PathVariable String lessonsTemplateCategoryId) {
    return lessonsTemplateQuestionRepository.findAll(
        LessonsTemplateQuestionSpecification.fromCategory(lessonsTemplateCategoryId));
  }

  @PutMapping(LESSON_QUESTION_URI + "/{lessonsTemplateQuestionId}")
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.LESSON_LEARNED)
  public LessonsTemplateQuestion updateLessonsTemplateQuestion(
      @PathVariable String lessonsTemplateId,
      @PathVariable String lessonsTemplateCategoryId,
      @PathVariable String lessonsTemplateQuestionId,
      @Valid @RequestBody LessonsTemplateQuestionInput input) {
    LessonsTemplateQuestion lessonsTemplateQuestion =
        lessonsTemplateQuestionRepository
            .findById(lessonsTemplateQuestionId)
            .orElseThrow(ElementNotFoundException::new);
    lessonsTemplateQuestion.setUpdateAttributes(input);
    lessonsTemplateQuestion.setUpdated(now());
    return lessonsTemplateQuestionRepository.save(lessonsTemplateQuestion);
  }

  @DeleteMapping(LESSON_QUESTION_URI + "/{lessonsTemplateQuestionId}")
  @RBAC(
      resourceId = "#lessonsTemplateId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.LESSON_LEARNED)
  public void deleteLessonsTemplateQuestion(
      @PathVariable String lessonsTemplateId,
      @PathVariable String lessonsTemplateCategoryId,
      @PathVariable String lessonsTemplateQuestionId) {
    lessonsTemplateQuestionRepository.deleteById(lessonsTemplateQuestionId);
  }
}
