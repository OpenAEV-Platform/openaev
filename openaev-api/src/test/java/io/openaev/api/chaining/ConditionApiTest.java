package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.api.chaining.dto.EventOutput;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionKeyType;
import io.openaev.database.model.ConditionType;
import io.openaev.service.chaining.ConditionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConditionApiTest {

  @Mock private ConditionService conditionService;

  @InjectMocks private ConditionApi conditionApi;

  @Test
  void create_shouldReturnMappedEventOutput() {
    EventInput input = eventInput();
    Condition root = conditionTree("cond-root", "wf-1", "event-1", "desc-1");

    when(conditionService.createConditionTree(input)).thenReturn(root);

    EventOutput result = conditionApi.create(input);

    assertNotNull(result);
    assertEquals("cond-root", result.getId());
    assertEquals("event-1", result.getName());
    assertEquals("wf-1", result.getWorkflowId());
    assertEquals(2, result.getConditions().size());
    verify(conditionService).createConditionTree(input);
  }

  @Test
  void findAllByWorkflow_shouldReturnMappedList() {
    Condition root = conditionTree("c-wf", "wf-9", "ev-9", "d");
    when(conditionService.findConditionRootsByWorkflowId("wf-9")).thenReturn(List.of(root));

    List<EventOutput> result = conditionApi.findAllByWorkflow("wf-9");

    assertEquals(1, result.size());
    assertEquals("c-wf", result.get(0).getId());
    assertEquals("wf-9", result.get(0).getWorkflowId());
    verify(conditionService).findConditionRootsByWorkflowId("wf-9");
  }

  @Test
  void findById_shouldReturnMappedEventOutput() {
    Condition root = conditionTree("c-42", "wf-42", "ev-42", "desc");
    when(conditionService.findConditionRootById("c-42")).thenReturn(root);

    EventOutput result = conditionApi.findById("c-42");

    assertNotNull(result);
    assertEquals("c-42", result.getId());
    assertEquals("ev-42", result.getName());
    verify(conditionService).findConditionRootById("c-42");
  }

  @Test
  void update_shouldReturnMappedEventOutput() {
    EventInput input = eventInput();
    Condition updatedRoot = conditionTree("c-upd", "wf-1", "event-upd", "desc-upd");

    when(conditionService.updateConditionTree("c-upd", input)).thenReturn(updatedRoot);

    EventOutput result = conditionApi.update("c-upd", input);

    assertNotNull(result);
    assertEquals("c-upd", result.getId());
    assertEquals("event-upd", result.getName());
    verify(conditionService).updateConditionTree("c-upd", input);
  }

  @Test
  void delete_shouldDelegateToService() {
    conditionApi.delete("c-del");
    verify(conditionService).deleteConditionTree("c-del");
  }

  private EventInput eventInput() {
    ConditionCreateInput root = new ConditionCreateInput();
    root.setTemporaryId("tmp-root");
    root.setType(ConditionType.AND);

    return EventInput.builder()
        .name("event-1")
        .description("desc-1")
        .workflowId("wf-1")
        .conditions(List.of(root))
        .build();
  }

  private Condition conditionTree(
      String rootId, String workflowId, String name, String description) {
    Condition root = new Condition();
    root.setId(rootId);
    root.setWorkflowId(workflowId);
    root.setName(name);
    root.setDescription(description);
    root.setType(ConditionType.AND);
    root.setCreationDate(Instant.parse("2026-03-01T10:00:00Z"));
    root.setUpdateDate(Instant.parse("2026-03-01T10:01:00Z"));

    Condition child = new Condition();
    child.setId(rootId + "-child");
    child.setWorkflowId(workflowId);
    child.setType(ConditionType.EQ);
    child.setKeyType(ConditionKeyType.PORTSCAN);
    child.setValue("445");
    child.setConditionParent(root);

    root.getConditionChildren().add(child);
    return root;
  }
}
