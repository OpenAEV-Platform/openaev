package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.api.chaining.dto.EventOutput;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionKeyType;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.MappingType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.ConditionService;
import java.time.Instant;
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
class ConditionApiTest {

  @Mock private ConditionService conditionService;
  @Mock private PreviewFeatureService previewFeatureService;

  @InjectMocks private ConditionApi conditionApi;

  @Nested
  @DisplayName("When feature flags are enabled")
  class WhenFlagsEnabled {

    @BeforeEach
    void enableFlags() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(true);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.CHAINING_SWIMLANES))
          .thenReturn(true);
    }

    @Test
    void given_validInput_should_create() {
      // Arrange
      EventInput input = eventInput();
      Condition root = conditionTree("cond-root", "wf-1", "event-1", "desc-1");
      when(conditionService.createConditionTree(input)).thenReturn(root);

      // Act
      EventOutput result = conditionApi.create(input);

      // Assert
      assertNotNull(result);
      assertEquals("cond-root", result.getId());
      assertEquals("event-1", result.getName());
      assertEquals("wf-1", result.getWorkflowId());
      assertEquals(2, result.getConditions().size());
      assertEquals(
          MappingType.LOCAL,
          result.getConditions().stream()
              .filter(c -> c.getId().equals("cond-root-child"))
              .findFirst()
              .orElseThrow()
              .getMappingType());
      verify(conditionService).createConditionTree(input);
    }

    @Test
    void given_workflowId_should_findAllByWorkflow() {
      // Arrange
      Condition root = conditionTree("c-wf", "wf-9", "ev-9", "d");
      when(conditionService.findConditionRootsByWorkflowId("wf-9")).thenReturn(List.of(root));

      // Act
      List<EventOutput> result = conditionApi.findAllByWorkflow("wf-9");

      // Assert
      assertEquals(1, result.size());
      assertEquals("c-wf", result.getFirst().getId());
      assertEquals("wf-9", result.getFirst().getWorkflowId());
      verify(conditionService).findConditionRootsByWorkflowId("wf-9");
    }

    @Test
    void given_validId_should_findById() {
      // Arrange
      Condition root = conditionTree("c-42", "wf-42", "ev-42", "desc");
      when(conditionService.findConditionRootById("c-42")).thenReturn(root);

      // Act
      EventOutput result = conditionApi.findById("c-42");

      // Assert
      assertNotNull(result);
      assertEquals("c-42", result.getId());
      assertEquals("ev-42", result.getName());
      verify(conditionService).findConditionRootById("c-42");
    }

    @Test
    void given_validInput_should_update() {
      // Arrange
      EventInput input = eventInput();
      Condition updatedRoot = conditionTree("c-upd", "wf-1", "event-upd", "desc-upd");
      when(conditionService.updateConditionTree("c-upd", input)).thenReturn(updatedRoot);

      // Act
      EventOutput result = conditionApi.update("c-upd", input);

      // Assert
      assertNotNull(result);
      assertEquals("c-upd", result.getId());
      assertEquals("event-upd", result.getName());
      verify(conditionService).updateConditionTree("c-upd", input);
    }

    @Test
    void given_validId_should_delete() {
      // Act
      conditionApi.delete("c-del");

      // Assert
      verify(conditionService).deleteConditionTree("c-del");
    }
  }

  @Nested
  @DisplayName("When CHAINING_SWIMLANES flag is disabled")
  class WhenFlagDisabled {

    @BeforeEach
    void disableSwimlanes() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(true);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.CHAINING_SWIMLANES))
          .thenReturn(false);
    }

    @Test
    void given_flagDisabled_should_rejectCreate() {
      // Arrange
      EventInput input = eventInput();

      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> conditionApi.create(input));
      verifyNoInteractions(conditionService);
    }

    @Test
    void given_flagDisabled_should_rejectFindById() {
      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> conditionApi.findById("c-1"));
      verifyNoInteractions(conditionService);
    }

    @Test
    void given_flagDisabled_should_rejectFindAllByWorkflow() {
      // Act & Assert
      assertThrows(
          ElementNotFoundException.class, () -> conditionApi.findAllByWorkflow("wf-1"));
      verifyNoInteractions(conditionService);
    }

    @Test
    void given_flagDisabled_should_rejectUpdate() {
      // Arrange
      EventInput input = eventInput();

      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> conditionApi.update("c-1", input));
      verifyNoInteractions(conditionService);
    }

    @Test
    void given_flagDisabled_should_rejectDelete() {
      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> conditionApi.delete("c-1"));
      verifyNoInteractions(conditionService);
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
      EventInput input = eventInput();

      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> conditionApi.create(input));
      verifyNoInteractions(conditionService);
    }
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
    child.setMappingType(MappingType.LOCAL);
    child.setConditionParent(root);

    root.getConditionChildren().add(child);
    return root;
  }
}
