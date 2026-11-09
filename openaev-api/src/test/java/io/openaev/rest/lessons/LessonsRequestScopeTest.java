package io.openaev.rest.lessons;

import static io.openaev.config.ProductionActiveTables.names;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Evaluation;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Objective;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.EvaluationRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.LessonsAnswerFixture;
import io.openaev.utils.fixtures.LessonsCategoryFixture;
import io.openaev.utils.fixtures.LessonsQuestionFixture;
import io.openaev.utils.fixtures.ObjectiveFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.VariableFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.LessonsAnswersComposer;
import io.openaev.utils.fixtures.composers.LessonsCategoryComposer;
import io.openaev.utils.fixtures.composers.LessonsQuestionsComposer;
import io.openaev.utils.fixtures.composers.ObjectiveComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.composers.VariableComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RowTenantScope;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The children of a simulation (lessons, objectives, evaluations, variables) are resolved in the
 * request scope, and bound to the parent named in the path.
 *
 * <p>A simulation belongs to tenant A. The caller addresses it, and its children, by id on the
 * route of tenant B, where the caller holds the assessment capability and is no member of A. The
 * rule: the caller reads none of A's rows and changes none of them, because the simulation the ids
 * point at lies outside the scope of the request. The same holds when the caller names its own
 * simulation in the path and a child of A by id: the child is not one of that simulation.
 *
 * <p>The setup runs under the production activation list, so the isolation that is on in a
 * deployment is the one in force. Rows are read back with native SQL, by the ids seeded in A, so no
 * entity filter shapes what the assertions see. Every refusal is asserted as a 404 together with an
 * unchanged row, so a different status or an empty answer cannot pass for it.
 *
 * <p>The controls pass: a holder of the capability in A works on A's own simulation, a child
 * addressed through another parent of the same simulation is not found, and a member of B without
 * the capability is refused.
 */
@Transactional
@WithMockUser(isAdmin = false)
@DisplayName("Children of a simulation are resolved in the request scope")
class LessonsRequestScopeTest extends IntegrationTest {

  private static final Set<Capability> ASSESSMENT_MANAGER =
      Set.of(Capability.ACCESS_ASSESSMENT, Capability.MANAGE_ASSESSMENT);

  private static final String CATEGORY_BODY =
      "{\"lessons_category_name\":\"renamed\",\"lessons_category_description\":\"d\","
          + "\"lessons_category_order\":1}";
  private static final String TEAMS_BODY = "{\"lessons_category_teams\":[]}";
  private static final String QUESTION_BODY =
      "{\"lessons_question_content\":\"changed\",\"lessons_question_explanation\":\"e\","
          + "\"lessons_question_order\":1}";
  private static final String OBJECTIVE_BODY =
      "{\"objective_title\":\"changed\",\"objective_description\":\"d\",\"objective_priority\":1}";
  private static final String VARIABLE_BODY =
      "{\"variable_key\":\"variable_renamed\",\"variable_value\":\"v\","
          + "\"variable_description\":\"d\"}";
  private static final String EVALUATION_BODY = "{\"evaluation_score\":9}";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add("openaev.tenant.active-tables", () -> String.join(",", names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private LessonsCategoryComposer lessonsCategoryComposer;
  @Autowired private LessonsQuestionsComposer lessonsQuestionsComposer;
  @Autowired private LessonsAnswersComposer lessonsAnswersComposer;
  @Autowired private ObjectiveComposer objectiveComposer;
  @Autowired private VariableComposer variableComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private EvaluationRepository evaluationRepository;
  @Autowired private RowTenantScope rowTenantScope;

  /** What the caller of the current test sent and what came back, for the failure messages. */
  private final StringBuilder calls = new StringBuilder();

  private String tenantA;
  private String tenantB;
  private String simulationOfA;
  private String simulationOfB;
  private String categoryOfA;
  private String otherCategoryOfA;
  private String questionOfA;
  private String answerOfA;
  private String objectiveOfA;
  private String otherObjectiveOfA;
  private String evaluationOfA;
  private String variableOfA;

  @BeforeEach
  void seedTwoTenantsAndASimulationInA() throws Exception {
    tenantA = tenantHelper.createTenant("children-scope-a").getId();
    tenantB = tenantHelper.createTenant("children-scope-b").getId();

    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(tenantA));

    Team team = TeamFixture.getDefaultTeam();
    team.setTenant(new Tenant(tenantA));
    rowTenantScope.write(tenantA, () -> teamComposer.forTeam(team).persist());

    var answer =
        lessonsAnswersComposer
            .forLessonsAnswer(LessonsAnswerFixture.createLessonsAnswer())
            .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail()));
    var question =
        lessonsQuestionsComposer
            .forLessonsQuestion(LessonsQuestionFixture.createDefaultLessonsQuestion())
            .withAnswer(answer);
    var category =
        lessonsCategoryComposer
            .forLessonsCategory(LessonsCategoryFixture.createDefaultLessonsCategory())
            .withLessonsQuestion(question);
    category.get().setTeams(List.of(team));
    var otherCategory =
        lessonsCategoryComposer.forLessonsCategory(
            LessonsCategoryFixture.createDefaultLessonsCategory());

    Objective objective = ObjectiveFixture.getDefaultObjective();
    objective.setExercise(exercise);
    Objective otherObjective = ObjectiveFixture.getDefaultObjective();
    otherObjective.setExercise(exercise);
    var objectiveComposed = objectiveComposer.forObjective(objective);
    var otherObjectiveComposed = objectiveComposer.forObjective(otherObjective);
    var variable = variableComposer.forVariable(VariableFixture.getDefaultVariable());
    exerciseComposer
        .forExercise(exercise)
        .withLessonCategory(category)
        .withLessonCategory(otherCategory)
        .withObjective(objectiveComposed)
        .withObjective(otherObjectiveComposed)
        .withVariable(variable)
        .persist();
    entityManager.flush();

    Evaluation evaluation = new Evaluation();
    evaluation.setObjective(objectiveComposed.get());
    evaluation.setUser(testUserHolder.get());
    evaluation.setScore(3L);
    evaluationRepository.save(evaluation);
    entityManager.flush();

    Exercise exerciseOfB = ExerciseFixture.createDefaultExercise();
    exerciseOfB.setTenant(new Tenant(tenantB));
    exerciseComposer.forExercise(exerciseOfB).persist();
    entityManager.flush();

    simulationOfA = exercise.getId();
    simulationOfB = exerciseOfB.getId();
    categoryOfA = category.get().getId();
    otherCategoryOfA = otherCategory.get().getId();
    questionOfA = question.get().getId();
    answerOfA = answer.get().getId();
    objectiveOfA = objectiveComposed.get().getId();
    otherObjectiveOfA = otherObjectiveComposed.get().getId();
    evaluationOfA = evaluation.getId();
    variableOfA = variable.get().getId();
    entityManager.clear();

    // The seed is what the assertions below are about: it must be there before any call.
    assertEquals(1, rowsOfA("lessons_categories", "lessons_category_id", categoryOfA));
    assertEquals(1, rowsOfA("lessons_categories", "lessons_category_id", otherCategoryOfA));
    assertEquals(1, rowsOfA("lessons_questions", "lessons_question_id", questionOfA));
    assertEquals(1, rowsOfA("lessons_answers", "lessons_answer_id", answerOfA));
    assertEquals(1, rowsOfA("objectives", "objective_id", objectiveOfA));
    assertEquals(1, rowsOfA("objectives", "objective_id", otherObjectiveOfA));
    assertEquals(1, rowsOfA("evaluations", "evaluation_id", evaluationOfA));
    assertEquals(1, rowsOfA("variables", "variable_id", variableOfA));
    assertEquals("1", teamsOfCategory());
  }

  // -- the rule: a foreign simulation, and a foreign child under one's own simulation --

  /**
   * Every handler that takes a child by id, as a caller of B holding the capability there. The
   * subclasses differ by the simulation the path names: A's, or B's own.
   */
  abstract class ChildByIdIsNotFound {

    /** The route of the simulation the path names, without a trailing slash. */
    abstract String simulation();

    @BeforeEach
    void callerHoldsTheCapabilityInBOnly() {
      callerIsMemberOf(tenantB, true);
      // The premise: the caller is a member of B and not of A.
      assertEquals(1, membershipsOfCaller(tenantB));
      assertEquals(0, membershipsOfCaller(tenantA));
    }

    @Test
    @DisplayName("lists the questions of A's category: not found")
    void categoryQuestionsAreNotFound() throws Exception {
      assertRefused(
          get(simulation() + "/lessons_categories/" + categoryOfA + "/lessons_questions"),
          () -> "");
    }

    @Test
    @DisplayName("updates A's category: not found, row unchanged")
    void updateCategoryIsNotFound() throws Exception {
      assertRefused(
          json(put(simulation() + "/lessons_categories/" + categoryOfA), CATEGORY_BODY),
          () -> categoryState());
    }

    @Test
    @DisplayName("sets the teams of A's category: not found, teams unchanged")
    void updateCategoryTeamsAreNotFound() throws Exception {
      assertRefused(
          json(put(simulation() + "/lessons_categories/" + categoryOfA + "/teams"), TEAMS_BODY),
          () -> categoryState());
    }

    @Test
    @DisplayName("deletes A's category: not found, row stays")
    void deleteCategoryIsNotFound() throws Exception {
      assertRefused(
          delete(simulation() + "/lessons_categories/" + categoryOfA), () -> categoryState());
    }

    @Test
    @DisplayName("creates a question in A's category: not found, none added")
    void createQuestionIsNotFound() throws Exception {
      assertRefused(
          json(
              post(simulation() + "/lessons_categories/" + categoryOfA + "/lessons_questions"),
              QUESTION_BODY),
          () -> questionsOfCategory());
    }

    @Test
    @DisplayName("updates A's question: not found, row unchanged")
    void updateQuestionIsNotFound() throws Exception {
      assertRefused(
          json(put(questionRoute(simulation(), categoryOfA, questionOfA)), QUESTION_BODY),
          () -> questionState());
    }

    @Test
    @DisplayName("deletes A's question: not found, row stays")
    void deleteQuestionIsNotFound() throws Exception {
      assertRefused(
          delete(questionRoute(simulation(), categoryOfA, questionOfA)), () -> questionState());
    }

    @Test
    @DisplayName("updates A's objective: not found, row unchanged")
    void updateObjectiveIsNotFound() throws Exception {
      assertRefused(
          json(put(simulation() + "/objectives/" + objectiveOfA), OBJECTIVE_BODY),
          () -> objectiveState(objectiveOfA));
    }

    @Test
    @DisplayName("deletes A's objective: not found, row stays")
    void deleteObjectiveIsNotFound() throws Exception {
      assertRefused(
          delete(simulation() + "/objectives/" + objectiveOfA), () -> objectiveState(objectiveOfA));
    }

    @Test
    @DisplayName("lists the evaluations of A's objective: not found")
    void getEvaluationsIsNotFound() throws Exception {
      assertRefused(get(simulation() + "/objectives/" + objectiveOfA + "/evaluations"), () -> "");
    }

    @Test
    @DisplayName("reads A's evaluation: not found")
    void getEvaluationIsNotFound() throws Exception {
      assertRefused(get(evaluationRoute(simulation(), objectiveOfA, evaluationOfA)), () -> "");
    }

    @Test
    @DisplayName("updates A's evaluation: not found, row and objective unchanged")
    void updateEvaluationIsNotFound() throws Exception {
      assertRefused(
          json(put(evaluationRoute(simulation(), objectiveOfA, evaluationOfA)), EVALUATION_BODY),
          () -> evaluationState() + objectiveState(objectiveOfA));
    }

    @Test
    @DisplayName("deletes A's evaluation: not found, row stays")
    void deleteEvaluationIsNotFound() throws Exception {
      assertRefused(
          delete(evaluationRoute(simulation(), objectiveOfA, evaluationOfA)),
          () -> evaluationState());
    }

    @Test
    @DisplayName("creates an evaluation under A's objective: not found, none added")
    void createEvaluationIsNotFound() throws Exception {
      assertRefused(
          json(
              post(simulation() + "/objectives/" + objectiveOfA + "/evaluations"), EVALUATION_BODY),
          () ->
              rowsOfA("evaluations", "evaluation_objective", objectiveOfA)
                  + objectiveState(objectiveOfA));
    }

    @Test
    @DisplayName("updates A's variable: not found, row unchanged")
    void updateVariableIsNotFound() throws Exception {
      assertRefused(
          json(put(simulation() + "/variables/" + variableOfA), VARIABLE_BODY),
          () -> variableState());
    }

    @Test
    @DisplayName("deletes A's variable: not found, row stays")
    void deleteVariableIsNotFound() throws Exception {
      assertRefused(delete(simulation() + "/variables/" + variableOfA), () -> variableState());
    }
  }

  @Nested
  @DisplayName("A holder of the capability in tenant B only, addressing A's simulation by id")
  class HolderInBOnly extends ChildByIdIsNotFound {

    @Override
    String simulation() {
      return routeOf(tenantB, simulationOfA);
    }

    @Test
    @DisplayName("reads none of A's lessons, objectives or variables: not found")
    void readsNoRowOfA() throws Exception {
      assertAll(
          () -> assertRefused(get(simulation() + "/lessons_categories"), () -> ""),
          () -> assertRefused(get(simulation() + "/lessons_questions"), () -> ""),
          () -> assertRefused(get(simulation() + "/lessons_answers"), () -> ""),
          () -> assertRefused(get(simulation() + "/objectives"), () -> ""),
          () -> assertRefused(get(simulation() + "/variables"), () -> ""));
    }

    @Test
    @DisplayName("empties none of A's lessons: not found, rows stay")
    void emptiesNoLessonOfA() throws Exception {
      assertRefused(post(simulation() + "/lessons_empty"), () -> lessonsRows());
    }

    @Test
    @DisplayName("resets none of A's lessons answers: not found, rows stay")
    void resetsNoAnswerOfA() throws Exception {
      assertRefused(post(simulation() + "/lessons_answers_reset"), () -> lessonsRows());
    }
  }

  @Nested
  @DisplayName(
      "A holder of the capability in B, addressing A's children through B's own simulation")
  class HolderInBOwnSimulation extends ChildByIdIsNotFound {

    @Override
    String simulation() {
      return routeOf(tenantB, simulationOfB);
    }
  }

  // -- the controls: they pass, so the test is not one that refuses everything --

  @Nested
  @DisplayName("Control: a holder of the capability in A, on A's own simulation")
  class HolderInA {

    @BeforeEach
    void callerHoldsTheCapabilityInA() {
      callerIsMemberOf(tenantA, true);
    }

    private String simulation() {
      return routeOf(tenantA, simulationOfA);
    }

    @Test
    @DisplayName("reads A's lessons, objectives, evaluations and variables")
    void readsItsOwnRows() throws Exception {
      assertAll(
          () -> assertBodyHas(get(simulation() + "/lessons_categories"), categoryOfA),
          () -> assertBodyHas(get(simulation() + "/lessons_questions"), questionOfA),
          () ->
              assertBodyHas(
                  get(simulation() + "/lessons_categories/" + categoryOfA + "/lessons_questions"),
                  questionOfA),
          () -> assertBodyHas(get(simulation() + "/lessons_answers"), answerOfA),
          () -> assertBodyHas(get(simulation() + "/objectives"), objectiveOfA),
          () ->
              assertBodyHas(
                  get(simulation() + "/objectives/" + objectiveOfA + "/evaluations"),
                  evaluationOfA),
          () ->
              assertBodyHas(
                  get(evaluationRoute(simulation(), objectiveOfA, evaluationOfA)), evaluationOfA),
          () -> assertBodyHas(get(simulation() + "/variables"), variableOfA));
    }

    @Test
    @DisplayName("empties A's lessons")
    void emptiesItsLessons() throws Exception {
      assertOk(post(simulation() + "/lessons_empty"));

      assertAll(
          () -> assertEquals("none", categoryState()),
          () -> assertEquals("none", questionState()),
          () -> assertEquals(0, rowsOfA("lessons_answers", "lessons_answer_id", answerOfA)));
    }

    @Test
    @DisplayName("resets the answers of A's lessons")
    void resetsItsAnswers() throws Exception {
      assertOk(post(simulation() + "/lessons_answers_reset"));

      assertAll(
          () -> assertEquals(0, rowsOfA("lessons_answers", "lessons_answer_id", answerOfA)),
          () -> assertNotEquals("none", questionState()));
    }

    @Test
    @DisplayName("updates A's category and sets its teams")
    void updatesItsCategory() throws Exception {
      assertOk(json(put(simulation() + "/lessons_categories/" + categoryOfA), CATEGORY_BODY));
      assertTrue(categoryState().startsWith("renamed/1/"), categoryState());

      assertOk(
          json(put(simulation() + "/lessons_categories/" + categoryOfA + "/teams"), TEAMS_BODY));
      assertEquals("renamed/1/0", categoryState());
    }

    @Test
    @DisplayName("creates, updates and deletes a question of A's category")
    void managesItsQuestions() throws Exception {
      MvcResult created =
          assertOk(
              json(
                  post(simulation() + "/lessons_categories/" + categoryOfA + "/lessons_questions"),
                  QUESTION_BODY));
      assertEquals("2", questionsOfCategory());
      assertTrue(created.getResponse().getContentAsString().contains("changed"));

      assertOk(json(put(questionRoute(simulation(), categoryOfA, questionOfA)), QUESTION_BODY));
      assertTrue(questionState().startsWith("changed/1/"), questionState());

      assertOk(delete(questionRoute(simulation(), categoryOfA, questionOfA)));
      assertAll(
          () -> assertEquals("none", questionState()),
          () -> assertEquals("1", questionsOfCategory()));
    }

    @Test
    @DisplayName("deletes A's category")
    void deletesItsCategory() throws Exception {
      assertOk(delete(simulation() + "/lessons_categories/" + categoryOfA));

      assertEquals("none", categoryState());
    }

    @Test
    @DisplayName("updates and deletes A's objective")
    void managesItsObjective() throws Exception {
      assertOk(json(put(simulation() + "/objectives/" + objectiveOfA), OBJECTIVE_BODY));
      assertTrue(objectiveState(objectiveOfA).startsWith("changed/1/"));

      assertOk(delete(simulation() + "/objectives/" + objectiveOfA));
      assertEquals("none", objectiveState(objectiveOfA));
    }

    @Test
    @DisplayName("creates, reads, updates and deletes an evaluation of A's objective")
    void evaluationLifecycle() throws Exception {
      String objectiveRoute = simulation() + "/objectives/" + objectiveOfA + "/evaluations";
      MvcResult created = assertOk(json(post(objectiveRoute), EVALUATION_BODY));
      String id = JsonPath.read(created.getResponse().getContentAsString(), "$.evaluation_id");
      assertEquals(2, rowsOfA("evaluations", "evaluation_objective", objectiveOfA));

      assertBodyHas(get(objectiveRoute + "/" + id), id);
      assertOk(json(put(objectiveRoute + "/" + id), "{\"evaluation_score\":4}"));
      assertEquals("4", scoreOf(id));

      assertOk(delete(objectiveRoute + "/" + id));
      assertEquals(1, rowsOfA("evaluations", "evaluation_objective", objectiveOfA));
    }

    @Test
    @DisplayName("updates and deletes A's variable")
    void managesItsVariable() throws Exception {
      assertOk(json(put(simulation() + "/variables/" + variableOfA), VARIABLE_BODY));
      assertEquals("variable_renamed/v", variableState());

      assertOk(delete(simulation() + "/variables/" + variableOfA));
      assertEquals("none", variableState());
    }
  }

  @Nested
  @DisplayName("Control: a child is bound to the parent named in the path, in A's own simulation")
  class ChildUnderAnotherParentOfTheSameSimulation {

    @BeforeEach
    void callerHoldsTheCapabilityInA() {
      callerIsMemberOf(tenantA, true);
    }

    private String simulation() {
      return routeOf(tenantA, simulationOfA);
    }

    @Test
    @DisplayName("a question is not found under another category: update and delete")
    void questionUnderAnotherCategory() throws Exception {
      assertRefused(
          json(put(questionRoute(simulation(), otherCategoryOfA, questionOfA)), QUESTION_BODY),
          () -> questionState());
      assertRefused(
          delete(questionRoute(simulation(), otherCategoryOfA, questionOfA)),
          () -> questionState());
    }

    @Test
    @DisplayName("an evaluation is not found under another objective: read, update and delete")
    void evaluationUnderAnotherObjective() throws Exception {
      assertRefused(get(evaluationRoute(simulation(), otherObjectiveOfA, evaluationOfA)), () -> "");
      assertRefused(
          json(
              put(evaluationRoute(simulation(), otherObjectiveOfA, evaluationOfA)),
              EVALUATION_BODY),
          () -> evaluationState() + objectiveState(otherObjectiveOfA));
      assertRefused(
          delete(evaluationRoute(simulation(), otherObjectiveOfA, evaluationOfA)),
          () -> evaluationState());
    }
  }

  /**
   * The non-prefixed route reads the caller's rights in the default tenant, so the caller holds the
   * capability there as well as in B, and selects B with the {@code X-Tenant-Ids} header: the
   * request scope is B while the ambient tenant stays the default one.
   */
  @Nested
  @DisplayName("A member of tenant B on the non-prefixed route")
  class MemberOfBOnTheNonPrefixedRoute {

    @BeforeEach
    void callerIsMemberOfBAndHoldsTheCapabilityInBothTenants() {
      tenantHelper.detachCurrentUserFromTenant(tenantA);
      tenantHelper.detachCurrentUserFromTenant(tenantB);
      tenantHelper.grantCapabilitiesInTenant(Tenant.DEFAULT_TENANT_UUID, ASSESSMENT_MANAGER);
      tenantHelper.grantCapabilitiesInTenant(tenantB, ASSESSMENT_MANAGER);
    }

    private MockHttpServletRequestBuilder inB(MockHttpServletRequestBuilder request) {
      // Fixtures leave a tenant on the test thread: the request must see the default one.
      TenantContext.clearCurrentTenant();
      return request.header("X-Tenant-Ids", tenantB);
    }

    private String ownSimulation() {
      return "/api/exercises/" + simulationOfB;
    }

    @Test
    @DisplayName("creates an objective, a variable and a lessons category in its own simulation")
    void createsInItsOwnSimulation() throws Exception {
      assertOk(inB(json(post(ownSimulation() + "/objectives"), OBJECTIVE_BODY)));
      assertOk(inB(json(post(ownSimulation() + "/variables"), VARIABLE_BODY)));
      assertOk(inB(json(post(ownSimulation() + "/lessons_categories"), CATEGORY_BODY)));

      assertAll(
          () -> assertEquals(1, rowsOf("objectives", "objective_exercise", simulationOfB)),
          () -> assertEquals(1, rowsOf("variables", "variable_exercise", simulationOfB)),
          () ->
              assertEquals(
                  1, rowsOf("lessons_categories", "lessons_category_exercise", simulationOfB)));
    }

    @Test
    @DisplayName("creates and updates an evaluation under an objective of its own simulation")
    void createsAndUpdatesAnEvaluationInItsOwnSimulation() throws Exception {
      MvcResult objective =
          assertOk(inB(json(post(ownSimulation() + "/objectives"), OBJECTIVE_BODY)));
      String objectiveId =
          JsonPath.read(objective.getResponse().getContentAsString(), "$.objective_id");
      String evaluations = ownSimulation() + "/objectives/" + objectiveId + "/evaluations";

      MvcResult created = assertOk(inB(json(post(evaluations), EVALUATION_BODY)));
      String id = JsonPath.read(created.getResponse().getContentAsString(), "$.evaluation_id");
      assertOk(inB(json(put(evaluations + "/" + id), "{\"evaluation_score\":4}")));

      assertEquals("4", scoreOf(id));
    }

    @Test
    @DisplayName("creates nothing in A's simulation: not found, none added")
    void createsNothingInTheSimulationOfA() throws Exception {
      String simulation = "/api/exercises/" + simulationOfA;
      assertAll(
          () ->
              assertRefused(
                  inB(json(post(simulation + "/objectives"), OBJECTIVE_BODY)),
                  () -> rowsOf("objectives", "objective_exercise", simulationOfA)),
          () ->
              assertRefused(
                  inB(json(post(simulation + "/variables"), VARIABLE_BODY)),
                  () -> rowsOf("variables", "variable_exercise", simulationOfA)),
          () ->
              assertRefused(
                  inB(json(post(simulation + "/lessons_categories"), CATEGORY_BODY)),
                  () -> rowsOf("lessons_categories", "lessons_category_exercise", simulationOfA)),
          () ->
              assertRefused(
                  inB(
                      json(
                          post(simulation + "/objectives/" + objectiveOfA + "/evaluations"),
                          EVALUATION_BODY)),
                  () -> rowsOf("evaluations", "evaluation_objective", objectiveOfA)));
    }
  }

  @Nested
  @DisplayName("Control: a member of tenant B without the capability")
  class MemberOfBWithoutCapability {

    @BeforeEach
    void callerHoldsNoCapability() {
      callerIsMemberOf(tenantB, false);
    }

    @Test
    @DisplayName("is refused on the read and on the empty, and A's rows stay")
    void isRefused() throws Exception {
      MvcResult read = send(get(routeOf(tenantB, simulationOfA) + "/lessons_categories"));
      MvcResult empty = send(post(routeOf(tenantB, simulationOfA) + "/lessons_empty"));

      assertAll(
          () -> assertEquals(403, read.getResponse().getStatus()),
          () -> assertEquals(403, empty.getResponse().getStatus()),
          () -> assertFalse(read.getResponse().getContentAsString().contains(categoryOfA)),
          () -> assertEquals(1, rowsOfA("lessons_categories", "lessons_category_id", categoryOfA)));
    }
  }

  // -- helpers --

  /**
   * Makes the caller a member of one tenant only, with or without the capability there. Tenant
   * creation enrols the creator in the tenant it makes, so both memberships are dropped first.
   */
  private void callerIsMemberOf(String tenantId, boolean withCapability) {
    tenantHelper.detachCurrentUserFromTenant(tenantA);
    tenantHelper.detachCurrentUserFromTenant(tenantB);
    if (withCapability) {
      tenantHelper.grantCapabilitiesInTenant(tenantId, ASSESSMENT_MANAGER);
    } else {
      tenantHelper.attachCurrentUserToTenant(tenantId);
    }
  }

  private static String routeOf(String tenantId, String simulationId) {
    return "/api/tenants/" + tenantId + "/exercises/" + simulationId;
  }

  private static String questionRoute(String simulation, String categoryId, String questionId) {
    return simulation + "/lessons_categories/" + categoryId + "/lessons_questions/" + questionId;
  }

  private static String evaluationRoute(
      String simulation, String objectiveId, String evaluationId) {
    return simulation + "/objectives/" + objectiveId + "/evaluations/" + evaluationId;
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private MvcResult send(MockHttpServletRequestBuilder request) throws Exception {
    // Each request runs in a transaction of its own: nothing of the test's persistence context
    // is carried into it or back out of it.
    entityManager.flush();
    entityManager.clear();
    MvcResult result = mvc.perform(request.with(csrf())).andReturn();
    entityManager.flush();
    entityManager.clear();
    calls
        .append("\n  ")
        .append(result.getRequest().getMethod())
        .append(' ')
        .append(result.getRequest().getRequestURI())
        .append(" -> ")
        .append(result.getResponse().getStatus());
    return result;
  }

  /**
   * The call is refused as not found, nothing of A comes back and the state read before and after
   * is the same. A state that cannot be told apart from "nothing" is a seed that is missing.
   */
  private void assertRefused(MockHttpServletRequestBuilder request, Supplier<Object> state)
      throws Exception {
    String before = String.valueOf(state.get());
    MvcResult result = send(request);
    String body = result.getResponse().getContentAsString();
    assertAll(
        () -> assertNotEquals("none", before, "the seed is missing"),
        () -> assertEquals(404, result.getResponse().getStatus(), () -> "calls:" + calls),
        () ->
            assertEquals(
                before, String.valueOf(state.get()), () -> "state changed; calls:" + calls),
        () -> assertFalse(carriesARowOfA(body), () -> "a row of A came back; calls:" + calls));
  }

  private MvcResult assertOk(MockHttpServletRequestBuilder request) throws Exception {
    MvcResult result = send(request);
    assertEquals(200, result.getResponse().getStatus(), () -> "calls:" + calls);
    return result;
  }

  private void assertBodyHas(MockHttpServletRequestBuilder request, String id) throws Exception {
    MvcResult result = assertOk(request);
    assertTrue(result.getResponse().getContentAsString().contains(id), () -> "calls:" + calls);
  }

  private boolean carriesARowOfA(String body) {
    // The ids of A's rows, none of which belongs in the body of a refusal.
    return body.contains("\"lessons_category_id\"")
        || body.contains("\"lessonsquestion_id\"")
        || body.contains("\"objective_id\"")
        || body.contains("\"variable_id\"")
        || body.contains("\"evaluation_id\"")
        || body.contains("\"lessons_answer_id\"");
  }

  private long membershipsOfCaller(String tenantId) {
    return count(
        "SELECT count(*) FROM users_tenants WHERE user_id = :user AND tenant_id = :tenant",
        "user",
        testUserHolder.get().getId(),
        "tenant",
        tenantId);
  }

  private long rowsOf(String table, String column, String id) {
    return rowsOfA(table, column, id);
  }

  /** Rows of A matching one id, counted with native SQL. */
  private long rowsOfA(String table, String idColumn, String id) {
    return count("SELECT count(*) FROM " + table + " WHERE " + idColumn + " = :id", "id", id);
  }

  private long count(String sql, String... namesAndValues) {
    entityManager.flush();
    var query = entityManager.createNativeQuery(sql);
    for (int i = 0; i < namesAndValues.length; i += 2) {
      query.setParameter(namesAndValues[i], namesAndValues[i + 1]);
    }
    return ((Number) query.getSingleResult()).longValue();
  }

  /** One text value of a row of A, or "none" when the row is gone. */
  private String state(String sql, String id) {
    entityManager.flush();
    List<?> found = entityManager.createNativeQuery(sql).setParameter("id", id).getResultList();
    return found.isEmpty() ? "none" : String.valueOf(found.get(0));
  }

  private String categoryState() {
    return state(
        "SELECT lessons_category_name || '/' || lessons_category_order || '/'"
            + " || (SELECT count(*) FROM lessons_categories_teams t"
            + " WHERE t.lessons_category_id = c.lessons_category_id)"
            + " FROM lessons_categories c WHERE c.lessons_category_id = :id",
        categoryOfA);
  }

  private String teamsOfCategory() {
    return state(
        "SELECT count(*) FROM lessons_categories_teams WHERE lessons_category_id = :id",
        categoryOfA);
  }

  private String questionsOfCategory() {
    return state(
        "SELECT count(*) FROM lessons_questions WHERE lessons_question_category = :id",
        categoryOfA);
  }

  private String questionState() {
    return state(
        "SELECT lessons_question_content || '/' || lessons_question_order || '/'"
            + " || lessons_question_category FROM lessons_questions"
            + " WHERE lessons_question_id = :id",
        questionOfA);
  }

  private String objectiveState(String objectiveId) {
    return state(
        "SELECT coalesce(objective_title, '') || '/' || coalesce(objective_priority, 0) || '/'"
            + " || objective_updated_at FROM objectives WHERE objective_id = :id",
        objectiveId);
  }

  private String evaluationState() {
    return state(
        "SELECT coalesce(evaluation_score, 0) || '/' || evaluation_updated_at"
            + " FROM evaluations WHERE evaluation_id = :id",
        evaluationOfA);
  }

  private String scoreOf(String evaluationId) {
    return state(
        "SELECT evaluation_score FROM evaluations WHERE evaluation_id = :id", evaluationId);
  }

  private String variableState() {
    return state(
        "SELECT variable_key || '/' || coalesce(variable_value, '')"
            + " FROM variables WHERE variable_id = :id",
        variableOfA);
  }

  private String lessonsRows() {
    return categoryState()
        + "|"
        + questionState()
        + "|"
        + rowsOfA("lessons_answers", "lessons_answer_id", answerOfA);
  }
}
