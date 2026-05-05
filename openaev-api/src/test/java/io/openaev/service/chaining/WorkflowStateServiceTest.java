package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.utils.ConditionUtils;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowStateService Tests")
class WorkflowStateServiceTest {

  @Mock private WorkflowStateRepository workflowStateRepository;
  @Mock private ConditionUtils conditionUtils;

  @InjectMocks private WorkflowStateService workflowStateService;

  private final Gson gson = new Gson();

  // ========================================================================
  // getLocalStateByWorkflowAndStep Tests
  // ========================================================================
  @Nested
  @DisplayName("getLocalStateByWorkflowAndStep")
  class GetLocalStateByWorkflowAndStepTests {

    @Test
    @DisplayName("should return local state when found")
    void given_existingState_should_returnLocalState() {
      // Arrange
      String stepTemplateId = UUID.randomUUID().toString();
      Step stepTemplate = Step.builder().id(stepTemplateId).build();
      String workflowExecutionId = UUID.randomUUID().toString();
      Workflow workflowExecution = Workflow.builder().id(workflowExecutionId).build();

      WorkflowState expected = mock(WorkflowState.class);
      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(expected);

      // Act
      WorkflowState result =
          workflowStateService.loadOrBuildLocalState(stepTemplate, workflowExecution);

      // Assert
      assertSame(expected, result);
      verify(workflowStateRepository)
          .findByStepTemplate_IdAndWorkflowExecution_Id(stepTemplateId, workflowExecutionId);
    }

    @Test
    @DisplayName("should initialize local state when not found")
    void given_noExistingState_should_initializeLocalState() {
      // Arrange
      String stepTemplateId = UUID.randomUUID().toString();
      Step stepTemplate = Step.builder().id(stepTemplateId).build();
      String workflowExecutionId = UUID.randomUUID().toString();
      Workflow workflowExecution = Workflow.builder().id(workflowExecutionId).build();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(null);

      // Act
      WorkflowState result =
          workflowStateService.loadOrBuildLocalState(stepTemplate, workflowExecution);

      // Assert
      assertNotNull(result);
      assertEquals(workflowExecution, result.getWorkflowExecution());
      assertEquals(stepTemplate, result.getStepTemplate());
    }
  }

  // ========================================================================
  // getGlobalStateByWorkflowId Tests
  // ========================================================================
  @Nested
  @DisplayName("getGlobalStateByWorkflowId")
  class GetGlobalStateByWorkflowIdTests {

    @Test
    @DisplayName("should return global state when found")
    void given_existingGlobalState_should_returnIt() {
      // Arrange
      String workflowId = UUID.randomUUID().toString();
      WorkflowState expected = mock(WorkflowState.class);
      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(expected);

      // Act
      WorkflowState result = workflowStateService.getGlobalStateByWorkflowId(workflowId);

      // Assert
      assertSame(expected, result);
      verify(workflowStateRepository).findByStepTemplateIsNullAndWorkflowExecutionId(workflowId);
    }

    @Test
    @DisplayName("should return null when no global state exists")
    void given_noGlobalState_should_returnNull() {
      // Arrange
      String workflowId = UUID.randomUUID().toString();
      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(null);

      // Act
      WorkflowState result = workflowStateService.getGlobalStateByWorkflowId(workflowId);

      // Assert
      assertNull(result);
    }
  }

  // ========================================================================
  // save Tests
  // ========================================================================
  @Nested
  @DisplayName("save")
  class SaveTests {

    @Test
    @DisplayName("should delegate directly to the repository")
    void given_state_should_delegateToRepository() {
      // Arrange
      WorkflowState state = mock(WorkflowState.class);

      // Act
      workflowStateService.save(state);

      // Assert
      verify(workflowStateRepository).save(state);
      verifyNoMoreInteractions(workflowStateRepository);
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
    void given_nonCorrelatedPath_should_addNewValue() {
      // Arrange
      String key = "stdout";
      String path = "outputs.message.stdout";
      String output = "{\"outputs\":{\"message\":{\"stdout\":\"test-value\"}}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("test-value"));
    }

    @Test
    @DisplayName("should not add duplicate value to input")
    void given_existingValue_should_notAddDuplicate() {
      // Arrange
      String key = "stdout";
      String path = "outputs.message.stdout";
      String output = "{\"outputs\":{\"message\":{\"stdout\":\"existing-value\"}}}";

      Set<String> existingValues = new HashSet<>();
      existingValues.add("existing-value");
      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, existingValues);
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertEquals(1, input.getValues().size());
    }

    @Test
    @DisplayName("should handle correlated path")
    void given_correlatedPath_should_handleIt() {
      // Arrange
      String path = "outputs.message.ip+outputs.message.port";
      String output = "{\"outputs\":{\"message\":{\"ip\":\"192.168.1.1\",\"port\":\"8080\"}}}";
      String key = "ip+port";

      List<String> correlatedPaths = List.of("outputs.message.ip", "outputs.message.port");

      WorkflowStateEntries stateEntries = mock(WorkflowStateEntries.class);
      when(stateEntries.isPathCorrelated(path)).thenReturn(true);
      when(stateEntries.pathCorrelated(path)).thenReturn(correlatedPaths);
      when(stateEntries.getIndexCorrelatedInput()).thenReturn(new HashMap<>());
      when(stateEntries.getCorrelated()).thenReturn(new ArrayList<>());

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      verify(stateEntries).isPathCorrelated(path);
      verify(stateEntries).pathCorrelated(path);
    }

    @Test
    @DisplayName("should not add correlated when already exists")
    void given_existingCorrelated_should_notAddDuplicate() {
      // Arrange
      String path = "outputs.message.ip+outputs.message.port";
      String output = "{\"outputs\":{\"message\":{\"ip\":\"192.168.1.1\",\"port\":\"8080\"}}}";
      String key = "ip+port";

      List<String> correlatedPaths = List.of("outputs.message.ip", "outputs.message.port");

      Set<WorkflowStateEntries.Pair> existingPairs = new HashSet<>();
      existingPairs.add(new WorkflowStateEntries.Pair("ip", "192.168.1.1"));
      existingPairs.add(new WorkflowStateEntries.Pair("port", "8080"));

      Map<Set<WorkflowStateEntries.Pair>, WorkflowStateEntries.Correlated> existingIndex =
          new HashMap<>();
      existingIndex.put(existingPairs, new WorkflowStateEntries.Correlated(existingPairs));

      WorkflowStateEntries stateEntries = mock(WorkflowStateEntries.class);
      when(stateEntries.isPathCorrelated(path)).thenReturn(true);
      when(stateEntries.pathCorrelated(path)).thenReturn(correlatedPaths);
      when(stateEntries.getIndexCorrelatedInput()).thenReturn(existingIndex);

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

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
    void given_stringOutput_should_extractValue() {
      // Arrange
      String key = "message";
      String path = "outputs.message";
      String output = "{\"outputs\":{\"message\":\"hello world\"}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("hello world"));
    }

    @Test
    @DisplayName("should handle null value in output")
    void given_nullOutput_should_handleGracefully() {
      // Arrange
      String key = "message";
      String path = "outputs.message";
      String output = "{\"outputs\":{\"message\":null}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().isEmpty() || input.getValues().contains(null));
    }

    @Test
    @DisplayName("should extract numeric value as string")
    void given_numericOutput_should_extractAsString() {
      // Arrange
      String key = "count";
      String path = "outputs.count";
      String output = "{\"outputs\":{\"count\":42}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("42"));
    }

    @Test
    @DisplayName("should extract boolean value as string")
    void given_booleanOutput_should_extractAsString() {
      // Arrange
      String key = "enabled";
      String path = "outputs.enabled";
      String output = "{\"outputs\":{\"enabled\":true}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("true"));
    }
  }
}
