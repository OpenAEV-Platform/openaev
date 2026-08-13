package io.openaev.service.chaining;

import static org.mockito.Mockito.*;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;
import io.openaev.database.repository.StepRepository;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChainingStepCleanupServiceTest {

  @Mock private StepRepository stepRepository;
  @Mock private ConditionService conditionService;
  @InjectMocks private ChainingStepCleanupService service;

  private Step templateStep(String id) {
    return Step.builder()
        .id(id)
        .stepAction(StepActionClass.INJECT_EXECUTION)
        .status(StepStatus.TEMPLATE)
        .data("{}")
        .build();
  }

  @Test
  @DisplayName("null contract ids is a no-op that never touches the repositories")
  void nullInput_isNoOp() {
    Assertions.assertEquals(0, service.deleteTemplateStepsByInjectorContractIds(null));
    verifyNoInteractions(stepRepository, conditionService);
  }

  @Test
  @DisplayName("empty contract ids is a no-op that never touches the repositories")
  void emptyInput_isNoOp() {
    Assertions.assertEquals(0, service.deleteTemplateStepsByInjectorContractIds(List.of()));
    verifyNoInteractions(stepRepository, conditionService);
  }

  @Test
  @DisplayName("no matching steps returns 0 and deletes nothing")
  void noMatches_returnsZero() {
    when(stepRepository.findTemplateStepsByInjectorContractIds(List.of("c1")))
        .thenReturn(List.of());

    Assertions.assertEquals(0, service.deleteTemplateStepsByInjectorContractIds(List.of("c1")));

    verify(stepRepository).findTemplateStepsByInjectorContractIds(List.of("c1"));
    verify(conditionService, never()).deleteAllConditionsByStepId(anyString());
    verify(stepRepository, never()).delete(any());
  }

  @Test
  @DisplayName("each matched step has its conditions pruned before the step row is removed")
  void matches_pruneConditionsThenDeleteSteps() {
    Step s1 = templateStep("step-1");
    Step s2 = templateStep("step-2");
    when(stepRepository.findTemplateStepsByInjectorContractIds(List.of("c1", "c2")))
        .thenReturn(List.of(s1, s2));

    int removed = service.deleteTemplateStepsByInjectorContractIds(List.of("c1", "c2"));

    Assertions.assertEquals(2, removed);
    // The conditions (graph edges) must be unlinked/pruned BEFORE the owning step is deleted, per
    // step - the same teardown order as a manual logic-map delete.
    InOrder inOrder = inOrder(conditionService, stepRepository);
    inOrder.verify(conditionService).deleteAllConditionsByStepId("step-1");
    inOrder.verify(stepRepository).delete(s1);
    inOrder.verify(conditionService).deleteAllConditionsByStepId("step-2");
    inOrder.verify(stepRepository).delete(s2);
  }
}
