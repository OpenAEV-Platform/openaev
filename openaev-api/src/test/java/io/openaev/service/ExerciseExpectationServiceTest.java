package io.openaev.service;

import static io.openaev.database.model.ExerciseStatus.SCHEDULED;
import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.Team;
import io.openaev.database.repository.*;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
public class ExerciseExpectationServiceTest extends IntegrationTest {

  public static final String EXPECTATION_NAME =
      "The animation team can validate the audience reaction";
  @Autowired private ExerciseExpectationService exerciseExpectationService;

  @Autowired private InjectExpectationService injectExpectationService;

  @Autowired private ExerciseRepository exerciseRepository;

  @Autowired private InjectRepository injectRepository;

  @Autowired private TeamRepository teamRepository;

  @Autowired private InjectExpectationRepository injectExpectationRepository;

  @Autowired private InjectorContractRepository injectorContractRepository;

  static String EXERCISE_ID;

  @BeforeEach
  void beforeEach() {
    Exercise exerciseCreated = getExercise();
    EXERCISE_ID = exerciseCreated.getId();
    Team teamCreated = getTeam();
    Inject injectCreated = getInject(exerciseCreated);
    getInjectExpectation(injectCreated, teamCreated, exerciseCreated);
  }

  @DisplayName("Retrieve inject expectations")
  @Test
  void retrieveInjectExpectations() {
    List<InjectExpectation> expectations =
        this.exerciseExpectationService.injectExpectations(EXERCISE_ID);
    assertNotNull(expectations);

    assertEquals(EXPECTATION_NAME, expectations.getFirst().getName());
  }

  @DisplayName("Update inject expectation")
  @Test
  void updateInjectExpectation() {
    // -- PREPARE --
    List<InjectExpectation> expectations =
        this.exerciseExpectationService.injectExpectations(EXERCISE_ID);
    assertNotNull(expectations);
    String id = expectations.getFirst().getId();

    // -- EXECUTE --
    ExpectationUpdateInput input = ExpectationUpdateInput.builder().score(7.0).build();
    InjectExpectation expectation =
        this.injectExpectationService.updateInjectExpectation(id, input);

    // -- ASSERT --
    assertNotNull(expectation);
    assertEquals(7, expectation.getScore());
  }

  protected Exercise getExercise() {
    Exercise exercise = new Exercise();
    exercise.setName("Exercise name");
    exercise.setStatus(SCHEDULED);
    exercise.setFrom("test@test.com");
    exercise.setReplyTos(List.of("test@test.com"));
    exercise.setStart(Instant.now());
    return this.exerciseRepository.save(exercise);
  }

  private Team getTeam() {
    Team team = new Team();
    team.setName("test");
    return this.teamRepository.save(team);
  }

  private Inject getInject(Exercise exerciseCreated) {
    Inject inject = new Inject();
    inject.setTitle("test");
    inject.setInjectorContract(
        this.injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow());
    inject.setExercise(exerciseCreated);
    inject.setDependsDuration(0L);
    return this.injectRepository.save(inject);
  }

  private void getInjectExpectation(
      Inject injectCreated, Team teamCreated, Exercise exerciseCreated) {
    this.injectExpectationRepository.save(
        InjectExpectationFixture.createManualInjectExpectationWithExercise(
            teamCreated, injectCreated, exerciseCreated, EXPECTATION_NAME));
  }
}
