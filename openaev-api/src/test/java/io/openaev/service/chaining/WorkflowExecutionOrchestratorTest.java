package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.JsonElement;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.database.repository.WorkflowRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowExecutionOrchestrator Tests")
class WorkflowExecutionOrchestratorTest {

  @Mock private WorkflowStateService workflowStateService;
  @Mock private WorkflowRepository workflowRepository;
  @Mock private ObjectProvider<StepService> stepServiceProvider;
  @Mock private StepService stepService;

  @InjectMocks private WorkflowExecutionOrchestrator orchestrator;

  @Nested
  @DisplayName("syncAndEvaluate")
  class SyncAndEvaluate {

    @Test
    @DisplayName("should skip when sync payload is null")
    void shouldSkipWhenPayloadIsNull() throws Exception {
      Workflow workflowRun = mock(Workflow.class);

      orchestrator.syncAndEvaluate(null, Map.of(), workflowRun);

      verifyNoInteractions(workflowStateService, stepServiceProvider, workflowRepository);
    }

    @Test
    @DisplayName("should sync state evaluate steps and save workflow")
    void shouldSyncEvaluateAndSave() throws Exception {
      Workflow workflowRun = mock(Workflow.class);
      JsonElement payload = com.google.gson.JsonParser.parseString("{\"IPv4\":[\"10.0.0.1\"]}");
      Map<String, ContractOutputType> fieldTypeMap =
          Map.of(ContractOutputType.IPv4.name(), ContractOutputType.IPv4);
      when(stepServiceProvider.getObject()).thenReturn(stepService);

      orchestrator.syncAndEvaluate(payload, fieldTypeMap, workflowRun);

      verify(workflowStateService).syncState(payload, fieldTypeMap, workflowRun);
      verify(stepService).evaluate(workflowRun);
      verify(workflowRepository).save(workflowRun);
    }
  }

  @Nested
  @DisplayName("startWorkflow")
  class StartWorkflow {

    @Test
    @DisplayName("should seed scope whitelist into sync payload")
    void shouldSeedWhitelistIntoSyncPayload() throws Exception {
      Workflow workflowRun = mock(Workflow.class);
      WorkflowScopeRule rule = new WorkflowScopeRule();
      rule.setRuleValue("10.10.10.10");
      rule.setValueType(ScopeRuleValueType.IP);
      when(workflowRun.getWhitelist()).thenReturn(List.of(rule));
      when(stepServiceProvider.getObject()).thenReturn(stepService);

      orchestrator.startWorkflow(workflowRun);

      ArgumentCaptor<JsonElement> payloadCaptor = ArgumentCaptor.forClass(JsonElement.class);
      verify(workflowStateService).syncState(payloadCaptor.capture(), any(), eq(workflowRun));
      verify(stepService).evaluate(workflowRun);
      verify(workflowRepository).save(workflowRun);

      JsonElement payload = payloadCaptor.getValue();
      assertEquals(
          "10.10.10.10",
          payload.getAsJsonObject().get("IPv4").getAsJsonArray().get(0).getAsString());
    }
  }
}
