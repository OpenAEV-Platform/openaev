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
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.StepService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepApiTest {

  @Mock private StepService stepService;
  @Mock private PreviewFeatureService previewFeatureService;

  @InjectMocks private StepApi stepApi;

  @Nested
  @DisplayName("When feature flags are enabled")
  class WhenFlagsEnabled {

    @BeforeEach
    void enableFlags() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.CHAINING_SWIMLANES))
          .thenReturn(true);
    }

    @Test
    void given_validInput_should_createStep() throws Exception {
      // Arrange
      StepInput input = new StepInput();
      input.setWorkflowId("wf-1");
      input.setStepAction(StepActionClass.INJECT_EXECUTION);

      Step created = step("step-1", 2, StepStatus.TEMPLATE, "{\"a\":1}");
      when(stepService.createStepTemplate(eq("wf-1"), any(StepsCreateInput.StepInput.class)))
          .thenReturn(created);

      // Act
      StepOutput result = stepApi.createStep(input);

      // Assert
      assertNotNull(result);
      assertEquals("step-1", result.getId());
      assertEquals(StepStatus.TEMPLATE, result.getStatus());
      assertEquals("{\"a\":1}", result.getData().toString());
      verify(stepService).createStepTemplate(eq("wf-1"), any(StepsCreateInput.StepInput.class));
    }

    @Test
    void given_validId_should_findById() {
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
    void given_workflowId_should_findByWorkflowId() {
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
    void given_validInput_should_updateStep() throws ChainingException {
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
    void given_validId_should_deleteStep() {
      // Act
      stepApi.deleteStep("s-del");

      // Assert
      verify(stepService).deleteStepTemplate("s-del");
    }
  }

  @Nested
  @DisplayName("When CHAINING_SWIMLANES flag is disabled")
  class WhenFlagDisabled {

    @BeforeEach
    void disableSwimlanes() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.CHAINING_SWIMLANES))
          .thenReturn(false);
    }

    @Test
    void given_flagDisabled_should_rejectCreate() {
      // Arrange
      StepInput input = new StepInput();
      input.setWorkflowId("wf-1");
      input.setStepAction(StepActionClass.INJECT_EXECUTION);

      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> stepApi.createStep(input));
      verifyNoInteractions(stepService);
    }

    @Test
    void given_flagDisabled_should_rejectFindById() {
      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> stepApi.findById("step-1"));
      verifyNoInteractions(stepService);
    }

    @Test
    void given_flagDisabled_should_rejectFindByWorkflowId() {
      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> stepApi.findByWorkflowId("wf-1"));
      verifyNoInteractions(stepService);
    }

    @Test
    void given_flagDisabled_should_rejectUpdate() {
      // Arrange
      StepInput input = new StepInput();

      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> stepApi.updateStep("s-1", input));
      verifyNoInteractions(stepService);
    }

    @Test
    void given_flagDisabled_should_rejectDelete() {
      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> stepApi.deleteStep("s-1"));
      verifyNoInteractions(stepService);
    }
  }

  @Nested
  @DisplayName("When INJECT_CHAINING flag is disabled")
  class WhenBaseFlagDisabled {

    @BeforeEach
    void disableBaseFlag() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
    }

    @Test
    void given_baseFlagDisabled_should_rejectCreate() {
      // Arrange
      StepInput input = new StepInput();
      input.setWorkflowId("wf-1");
      input.setStepAction(StepActionClass.INJECT_EXECUTION);

      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> stepApi.createStep(input));
      verifyNoInteractions(stepService);
    }
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
