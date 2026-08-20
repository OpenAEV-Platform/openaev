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

  private static final String TENANT_ID = "tenant-1";

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
  void given_nullContractIds_should_noOp() {
    Assertions.assertEquals(0, service.deleteTemplateStepsByInjectorContractIds(null, TENANT_ID));
    verifyNoInteractions(stepRepository, conditionService);
  }

  @Test
  @DisplayName("empty contract ids is a no-op that never touches the repositories")
  void given_emptyContractIds_should_noOp() {
    Assertions.assertEquals(
        0, service.deleteTemplateStepsByInjectorContractIds(List.of(), TENANT_ID));
    verifyNoInteractions(stepRepository, conditionService);
  }

  @Test
  @DisplayName("a null tenant fails fast instead of silently sweeping nothing")
  void given_nullTenant_should_failFast() {
    Assertions.assertThrows(
        NullPointerException.class,
        () -> service.deleteTemplateStepsByInjectorContractIds(List.of("c1"), null));
    verifyNoInteractions(stepRepository, conditionService);
  }

  @Test
  @DisplayName("no matching steps returns 0 and deletes nothing")
  void given_noMatchingSteps_should_returnZero() {
    when(stepRepository.findTemplateStepsByInjectorContractIds(List.of("c1"), TENANT_ID))
        .thenReturn(List.of());

    Assertions.assertEquals(
        0, service.deleteTemplateStepsByInjectorContractIds(List.of("c1"), TENANT_ID));

    verify(stepRepository).findTemplateStepsByInjectorContractIds(List.of("c1"), TENANT_ID);
    verify(conditionService, never()).deleteAllConditionsByStepId(anyString());
    verify(stepRepository, never()).delete(any());
  }

  @Test
  @DisplayName("each matched step has its conditions pruned before the step row is removed")
  void given_matchingSteps_should_pruneConditionsThenDeleteSteps() {
    Step s1 = templateStep("step-1");
    Step s2 = templateStep("step-2");
    when(stepRepository.findTemplateStepsByInjectorContractIds(List.of("c1", "c2"), TENANT_ID))
        .thenReturn(List.of(s1, s2));

    int removed = service.deleteTemplateStepsByInjectorContractIds(List.of("c1", "c2"), TENANT_ID);

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
