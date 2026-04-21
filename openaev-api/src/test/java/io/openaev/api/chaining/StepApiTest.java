package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.dto.StepInput;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.chaining.StepService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepApiTest {

  @Mock private StepService stepService;

  @InjectMocks private StepApi stepApi;

  @Test
  void createStep_shouldReturnMappedOutput() throws Exception {
    StepInput input = new StepInput();
    input.setWorkflowId("wf-1");
    input.setStepAction(StepActionClass.INJECT_EXECUTION);

    Step created = step("step-1", 2, StepStatus.TEMPLATE, "{\"a\":1}");
    when(stepService.createStepTemplate(eq("wf-1"), any(StepsCreateInput.StepInput.class)))
        .thenReturn(created);

    StepOutput result = stepApi.createStep(input);

    assertNotNull(result);
    assertEquals("step-1", result.getId());
    assertEquals(StepStatus.TEMPLATE, result.getStatus());
    assertEquals("{\"a\":1}", result.getData().toString());
    verify(stepService).createStepTemplate(eq("wf-1"), any(StepsCreateInput.StepInput.class));
  }

  @Test
  void findById_shouldReturnMappedStep() {
    when(stepService.findStepTemplateById("step-42"))
        .thenReturn(step("step-42", 1, StepStatus.TEMPLATE, "{}"));

    StepOutput result = stepApi.findById("step-42");

    assertNotNull(result);
    assertEquals("step-42", result.getId());
    verify(stepService).findStepTemplateById("step-42");
  }

  @Test
  void findByWorkflowId_shouldReturnMappedList() {
    when(stepService.findAllStepTemplateByWorkflow("wf-9"))
        .thenReturn(List.of(step("s-9", 5, StepStatus.TEMPLATE, "{}")));

    List<StepOutput> result = stepApi.findByWorkflowId("wf-9");

    assertEquals(1, result.size());
    assertEquals("s-9", result.get(0).getId());
    verify(stepService).findAllStepTemplateByWorkflow("wf-9");
  }

  @Test
  void updateStep_shouldReturnMappedStep() throws ChainingException {
    StepInput input = new StepInput();
    input.setWorkflowId("wf-1");
    input.setStepAction(StepActionClass.INJECT_EXECUTION);

    when(stepService.updateStepTemplate("s-1", input))
        .thenReturn(step("s-1", 9, StepStatus.TEMPLATE, "{\"updated\":true}"));

    StepOutput result = stepApi.updateStep("s-1", input);

    assertNotNull(result);
    assertEquals("s-1", result.getId());
    verify(stepService).updateStepTemplate("s-1", input);
  }

  @Test
  void deleteStep_shouldDelegateToService() {
    stepApi.deleteStep("s-del");

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
