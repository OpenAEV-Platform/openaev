package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StepEvent Tests")
class StepEventTest {

  // ========================================================================
  // equals Tests (custom implementation based on id only)
  // ========================================================================
  @Nested
  @DisplayName("equals")
  class EqualsTests {

    @Test
    @DisplayName("should return true when comparing same instance")
    void shouldReturnTrueForSameInstance() {
      StepEvent event = StepEvent.builder().build();
      assertEquals(event, event);
    }

    @Test
    @DisplayName("should return false when comparing with null")
    void shouldReturnFalseForNull() {
      StepEvent event = StepEvent.builder().build();
      assertNotEquals(null, event);
    }

    @Test
    @DisplayName("should return false when comparing with different class")
    void shouldReturnFalseForDifferentClass() {
      StepEvent event = StepEvent.builder().build();
      assertNotEquals(event, "not a StepEvent");
    }

    @Test
    @DisplayName("should return false for different instances (different auto-generated IDs)")
    void shouldReturnFalseForDifferentIds() {
      // Even with same field values, different instances have different IDs
      StepEvent event1 = StepEvent.builder().workflowId("wf1").stepId("step1").build();
      StepEvent event2 = StepEvent.builder().workflowId("wf1").stepId("step1").build();

      assertNotEquals(event1, event2);
    }
  }

  // ========================================================================
  // hashCode Tests (custom implementation based on id only)
  // ========================================================================
  @Nested
  @DisplayName("hashCode")
  class HashCodeTests {

    @Test
    @DisplayName("should be consistent across multiple calls")
    void shouldBeConsistent() {
      StepEvent event = StepEvent.builder().build();
      assertEquals(event.hashCode(), event.hashCode());
    }

    @Test
    @DisplayName("should differ for different instances")
    void shouldDifferForDifferentInstances() {
      StepEvent event1 = StepEvent.builder().build();
      StepEvent event2 = StepEvent.builder().build();

      assertNotEquals(event1.hashCode(), event2.hashCode());
    }
  }

  // ========================================================================
  // getUniqueElementKey Tests (business logic for queue routing)
  // ========================================================================
  @Nested
  @DisplayName("getUniqueElementKey")
  class GetUniqueElementKeyTests {

    @Test
    @DisplayName("should return concatenation of workflowId and stepId")
    void shouldReturnConcatenationOfWorkflowIdAndStepId() {
      StepEvent event = StepEvent.builder().workflowId("workflow-123").stepId("step-456").build();

      assertEquals("workflow-123step-456", event.getUniqueElementKey());
    }

    @Test
    @DisplayName("should handle null fields")
    void shouldHandleNullFields() {
      StepEvent event = StepEvent.builder().build();

      assertEquals("nullnull", event.getUniqueElementKey());
    }
  }
}
