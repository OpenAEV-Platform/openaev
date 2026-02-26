package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepState;
import io.openaev.database.model.StepStateEntries;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepStateRepository;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StepStateService Tests")
class StepStateServiceTest {

  @Mock private StepStateRepository stepStateRepository;

  @InjectMocks private StepStateService stepStateService;

  // ========================================================================
  // createState Tests
  // ========================================================================
  @Nested
  @DisplayName("createState")
  class CreateStateTests {

    @Captor private ArgumentCaptor<StepState> stepStateCaptor;

    @Test
    @DisplayName("should create state with correct properties")
    void shouldCreateStateWithCorrectProperties() {
      // Prepare
      Set<String> executionKeys = Set.of("key1", "key2");
      Step stepTemplate = mock(Step.class);
      Workflow workflowExecution = mock(Workflow.class);

      // Act
      stepStateService.createState(executionKeys, stepTemplate, workflowExecution);

      // Assert
      verify(stepStateRepository).save(stepStateCaptor.capture());
      StepState savedState = stepStateCaptor.getValue();

      assertNotNull(savedState);
      assertEquals(stepTemplate, savedState.getStepTemplate());
      assertEquals(workflowExecution, savedState.getWorkflowExecution());
      assertNotNull(savedState.getEntries());
    }

    @Test
    @DisplayName("should throw exception with empty execution keys")
    void shouldNotCreateStateWithEmptyExecutionKeys_Throw() {
      // Prepare
      Set<String> executionKeys = Collections.emptySet();
      Step stepTemplate = mock(Step.class);
      Workflow workflowExecution = mock(Workflow.class);

      // Act + Assert
      assertThrows(
          IllegalArgumentException.class,
          () -> stepStateService.createState(executionKeys, stepTemplate, workflowExecution));
    }

    @Test
    @DisplayName("should throw exception with null execution keys")
    void shouldNotCreateStateWithNullExecutionKeys_Throw() {
      // Prepare
      Step stepTemplate = mock(Step.class);
      Workflow workflowExecution = mock(Workflow.class);

      // Act + Assert
      assertThrows(
          IllegalArgumentException.class,
          () -> stepStateService.createState(null, stepTemplate, workflowExecution));
    }

    @Test
    @DisplayName("should create state with single execution key")
    void shouldCreateStateWithSingleExecutionKey() {
      // Prepare
      Set<String> executionKeys = Set.of("singleKey");
      Step stepTemplate = mock(Step.class);
      Workflow workflowExecution = mock(Workflow.class);

      // Act
      stepStateService.createState(executionKeys, stepTemplate, workflowExecution);

      // Assert
      verify(stepStateRepository).save(stepStateCaptor.capture());
      assertNotNull(stepStateCaptor.getValue());
      verifyNoMoreInteractions(stepStateRepository);
    }
  }

  // ========================================================================
  // getState Tests
  // ========================================================================
  @Nested
  @DisplayName("getState")
  class GetStateTests {

    @Captor private ArgumentCaptor<String> stepTemplateIdCaptor;

    @Captor private ArgumentCaptor<String> workflowExecutionIdCaptor;

    @Test
    @DisplayName("should return state entries when found")
    void shouldReturnStateEntriesWhenFound() {
      // Prepare
      String stepTemplateId = UUID.randomUUID().toString();
      String workflowExecutionId = UUID.randomUUID().toString();

      StepStateEntries expectedEntries =
          new StepStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of("key1"));
      Gson gson = new Gson();
      String entriesJson = gson.toJson(expectedEntries);

      StepState stepState = mock(StepState.class);
      when(stepState.getEntries()).thenReturn(entriesJson);
      when(stepStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(stepState);

      // Act
      StepStateEntries result = stepStateService.getState(stepTemplateId, workflowExecutionId);

      // Assert
      verify(stepStateRepository)
          .findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateIdCaptor.capture(), workflowExecutionIdCaptor.capture());
      assertEquals(stepTemplateId, stepTemplateIdCaptor.getValue());
      assertEquals(workflowExecutionId, workflowExecutionIdCaptor.getValue());
      assertNotNull(result);
    }

    @Test
    @DisplayName("should throw exception when state not found")
    void shouldThrowExceptionWhenNotFound() {
      // Prepare
      String stepTemplateId = UUID.randomUUID().toString();
      String workflowExecutionId = UUID.randomUUID().toString();

      when(stepStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(null);

      // Act & Assert
      assertThrows(
          NullPointerException.class,
          () -> stepStateService.getState(stepTemplateId, workflowExecutionId));
    }

    @Test
    @DisplayName("should return state with execution keys")
    void shouldReturnStateWithExecutionKeys() {
      // Prepare
      String stepTemplateId = UUID.randomUUID().toString();
      String workflowExecutionId = UUID.randomUUID().toString();

      Set<String> executionKeys = Set.of("key1", "key2", "key3");
      StepStateEntries expectedEntries =
          new StepStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), executionKeys);
      Gson gson = new Gson();
      String entriesJson = gson.toJson(expectedEntries);

      StepState stepState = mock(StepState.class);
      when(stepState.getEntries()).thenReturn(entriesJson);
      when(stepStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(stepState);

      // Act
      StepStateEntries result = stepStateService.getState(stepTemplateId, workflowExecutionId);

      // Assert
      assertNotNull(result);
      assertEquals(executionKeys, result.getExecutionKeys());
    }
  }

  // ========================================================================
  // newOutput Tests
  // ========================================================================
  @Nested
  @DisplayName("newOutput")
  class NewOutputTests {

    @Test
    @DisplayName("should add new value to input when path is not correlated")
    void shouldAddNewValueToInputWhenPathNotCorrelated() {
      // Prepare
      String key = "stdout";
      String path = "outputs.message.stdout";
      String output = "{\"outputs\":{\"message\":{\"stdout\":\"test-value\"}}}";

      StepStateEntries.Input input = new StepStateEntries.Input(key, new HashSet<>());
      List<StepStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      StepStateEntries stateEntries =
          new StepStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      stepStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("test-value"));
    }

    @Test
    @DisplayName("should not add duplicate value to input")
    void shouldNotAddDuplicateValueToInput() {
      // Prepare
      String key = "stdout";
      String path = "outputs.message.stdout";
      String output = "{\"outputs\":{\"message\":{\"stdout\":\"existing-value\"}}}";

      Set<String> existingValues = new HashSet<>();
      existingValues.add("existing-value");
      StepStateEntries.Input input = new StepStateEntries.Input(key, existingValues);
      List<StepStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      StepStateEntries stateEntries =
          new StepStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      stepStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertEquals(1, input.getValues().size());
    }

    @Test
    @DisplayName("should handle correlated path")
    void shouldHandleCorrelatedPath() {
      // Prepare
      String path = "outputs.message.ip+outputs.message.port";
      String output = "{\"outputs\":{\"message\":{\"ip\":\"192.168.1.1\",\"port\":\"8080\"}}}";
      String key = "ip+port";

      List<String> correlatedPaths = List.of("outputs.message.ip", "outputs.message.port");

      StepStateEntries stateEntries = mock(StepStateEntries.class);
      when(stateEntries.isPathCorrelated(path)).thenReturn(true);
      when(stateEntries.pathCorrelated(path)).thenReturn(correlatedPaths);
      when(stateEntries.getIndexCorrelatedInput()).thenReturn(new HashMap<>());
      when(stateEntries.getCorrelated()).thenReturn(new ArrayList<>());

      // Act
      stepStateService.newOutput(stateEntries, output, path, key);

      // Assert
      verify(stateEntries).isPathCorrelated(path);
      verify(stateEntries).pathCorrelated(path);
    }

    @Test
    @DisplayName("should not add correlated when already exists")
    void shouldNotAddCorrelatedWhenAlreadyExists() {
      // Prepare
      String path = "outputs.message.ip+outputs.message.port";
      String output = "{\"outputs\":{\"message\":{\"ip\":\"192.168.1.1\",\"port\":\"8080\"}}}";
      String key = "ip+port";

      List<String> correlatedPaths = List.of("outputs.message.ip", "outputs.message.port");

      Set<StepStateEntries.Pair> existingPairs = new HashSet<>();
      existingPairs.add(new StepStateEntries.Pair("ip", "192.168.1.1"));
      existingPairs.add(new StepStateEntries.Pair("port", "8080"));

      Map<Set<StepStateEntries.Pair>, StepStateEntries.Correlated> existingIndex = new HashMap<>();
      existingIndex.put(existingPairs, new StepStateEntries.Correlated(existingPairs));

      StepStateEntries stateEntries = mock(StepStateEntries.class);
      when(stateEntries.isPathCorrelated(path)).thenReturn(true);
      when(stateEntries.pathCorrelated(path)).thenReturn(correlatedPaths);
      when(stateEntries.getIndexCorrelatedInput()).thenReturn(existingIndex);

      // Act
      stepStateService.newOutput(stateEntries, output, path, key);

      // Assert
      verify(stateEntries, never()).getCorrelated();
    }
  }

  // ========================================================================
  // getValues Tests (tested indirectly through newOutput)
  // ========================================================================
  @Nested
  @DisplayName("getValues (private method - tested via newOutput)")
  class GetValuesTests {

    @Test
    @DisplayName("should extract primitive string value")
    void shouldExtractPrimitiveStringValue() {
      // Prepare
      String key = "message";
      String path = "outputs.message";
      String output = "{\"outputs\":{\"message\":\"hello world\"}}";

      StepStateEntries.Input input = new StepStateEntries.Input(key, new HashSet<>());
      List<StepStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      StepStateEntries stateEntries =
          new StepStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      stepStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("hello world"));
    }

    @Test
    @DisplayName("should handle null value in output")
    void shouldHandleNullValueInOutput() {
      // Prepare
      String key = "message";
      String path = "outputs.message";
      String output = "{\"outputs\":{\"message\":null}}";

      StepStateEntries.Input input = new StepStateEntries.Input(key, new HashSet<>());
      List<StepStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      StepStateEntries stateEntries =
          new StepStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      stepStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().isEmpty() || input.getValues().contains(null));
    }

    @Test
    @DisplayName("should extract numeric value as string")
    void shouldExtractNumericValueAsString() {
      // Prepare
      String key = "count";
      String path = "outputs.count";
      String output = "{\"outputs\":{\"count\":42}}";

      StepStateEntries.Input input = new StepStateEntries.Input(key, new HashSet<>());
      List<StepStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      StepStateEntries stateEntries =
          new StepStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      stepStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("42"));
    }

    @Test
    @DisplayName("should extract boolean value as string")
    void shouldExtractBooleanValueAsString() {
      // Prepare
      String key = "enabled";
      String path = "outputs.enabled";
      String output = "{\"outputs\":{\"enabled\":true}}";

      StepStateEntries.Input input = new StepStateEntries.Input(key, new HashSet<>());
      List<StepStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      StepStateEntries stateEntries =
          new StepStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      stepStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("true"));
    }
  }
}
