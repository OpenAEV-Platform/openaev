package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.dto.StepInput;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepApiTest {

  @Mock private StepService stepService;
  @Mock private WorkflowService workflowService;

  @InjectMocks private StepApi stepApi;

  @Test
  void given_validInput_should_createStepAndReturnMappedOutput() throws Exception {
    // Arrange
    StepInput input = new StepInput();
    input.setWorkflowId("wf-1");
    input.setStepAction(StepActionClass.INJECT_EXECUTION);

    Workflow workflow = mock(Workflow.class);
    when(workflowService.getWorkflowByIdAndStatus("wf-1", WorkflowStatus.TEMPLATE))
        .thenReturn(workflow);

    Step created = step("step-1", 2, StepStatus.TEMPLATE, "{\"a\":1}");
    when(stepService.createStepTemplate(eq(workflow), any(StepsCreateInput.StepInput.class)))
        .thenReturn(created);

    // Act
    StepOutput result = stepApi.createStep(input);

    // Assert
    assertNotNull(result);
    assertEquals("step-1", result.getId());
    assertEquals(StepStatus.TEMPLATE, result.getStatus());
    assertEquals("{\"a\":1}", result.getData().toString());
    verify(stepService).createStepTemplate(eq(workflow), any(StepsCreateInput.StepInput.class));
  }

  @Test
  void given_stepId_should_findByIdAndReturnMappedStep() {
    // Arrange
    when(stepService.findStepTemplateById("step-42"))
        .thenReturn(step("step-42", 1, StepStatus.TEMPLATE, "{}"));

    // Act
    StepOutput result = stepApi.findById("step-42");

    // Assert
    assertNotNull(result);
    assertEquals("step-42", result.getId());
    verify(stepService).findStepTemplateById("step-42");
  }

  @Test
  void given_workflowId_should_findByWorkflowIdAndReturnMappedList() {
    // Arrange
    when(stepService.findAllStepTemplateByWorkflow("wf-9"))
        .thenReturn(List.of(step("s-9", 5, StepStatus.TEMPLATE, "{}")));

    // Act
    List<StepOutput> result = stepApi.findByWorkflowId("wf-9");

    // Assert
    assertEquals(1, result.size());
    assertEquals("s-9", result.get(0).getId());
    verify(stepService).findAllStepTemplateByWorkflow("wf-9");
  }

  @Test
  void given_validInput_should_updateStepAndReturnMappedStep() throws ChainingException {
    // Arrange
    StepInput input = new StepInput();
    input.setWorkflowId("wf-1");
    input.setStepAction(StepActionClass.INJECT_EXECUTION);

    when(stepService.updateStepTemplate("s-1", input))
        .thenReturn(step("s-1", 9, StepStatus.TEMPLATE, "{\"updated\":true}"));

    // Act
    StepOutput result = stepApi.updateStep("s-1", input);

    // Assert
    assertNotNull(result);
    assertEquals("s-1", result.getId());
    verify(stepService).updateStepTemplate("s-1", input);
  }

  @Test
  void given_stepId_should_deleteStep() {
    // Act
    stepApi.deleteStep("s-del");

    // Assert
    verify(stepService).deleteStepTemplate("s-del");
  }

  private Step step(String id, int limit, StepStatus status, String data) {
    Step step = new Step();
    step.setId(id);
    step.setLimitExecution(limit);
    step.setStatus(status);
    step.setData(data);
    return step;
  }
}
