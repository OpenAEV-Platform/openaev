package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Variable;
import io.openaev.database.model.Variable.VariableType;
import io.openaev.database.repository.ExerciseRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VariableServiceTest extends IntegrationTest {

  @Autowired private VariableService variableService;

  @Autowired private ExerciseRepository exerciseRepository;

  static Exercise EXERCISE;
  static String VARIABLE_ID;

  @BeforeAll
  void beforeAll() {
    Exercise exercise = new Exercise();
    exercise.setName("Exercise name");
    exercise.setFrom("test@test.com");
    exercise.setReplyTos(List.of("test@test.com"));
    EXERCISE = this.exerciseRepository.save(exercise);
  }

  @AfterAll
  void afterAll() {
    this.exerciseRepository.deleteById(EXERCISE.getId());
  }

  @DisplayName("Create variable")
  @Test
  @Order(1)
  void createVariableTest() {
    // -- PREPARE --
    Variable variable = new Variable();
    String variableKey = "key";
    variable.setKey(variableKey);
    variable.setExercise(EXERCISE);

    // -- EXECUTE --
    Variable variableCreated = this.variableService.createVariable(variable);
    VARIABLE_ID = variableCreated.getId();
    assertNotNull(variableCreated);
    assertNotNull(variableCreated.getId());
    assertNotNull(variableCreated.getCreatedAt());
    assertNotNull(variableCreated.getUpdatedAt());
    assertEquals(variableKey, variableCreated.getKey());
    assertEquals(VariableType.String, variableCreated.getType());
  }

  @DisplayName("Retrieve variable")
  @Test
  @Order(2)
  void retrieveVariableTest() {
    Variable variable = this.variableService.variable(VARIABLE_ID);
    assertNotNull(variable);

    List<Variable> variables = this.variableService.variablesFromExercise(EXERCISE.getId());
    assertNotNull(variable);
    assertEquals(VARIABLE_ID, variables.get(0).getId());
  }

  @DisplayName("Update variable")
  @Test
  @Order(3)
  void updateVariableTest() {
    // -- PREPARE --
    Variable variable = this.variableService.variable(VARIABLE_ID);
    String value = "A value";
    variable.setValue(value);

    // -- EXECUTE --
    Variable variableUpdated = this.variableService.updateVariable(variable);
    assertNotNull(variable);
    assertEquals(value, variableUpdated.getValue());
  }

  @DisplayName("Delete variable")
  @Test
  @Order(4)
  void deleteVariableTest() {
    this.variableService.deleteVariable(VARIABLE_ID);
    assertThrows(NoSuchElementException.class, () -> this.variableService.variable(VARIABLE_ID));
  }
}
