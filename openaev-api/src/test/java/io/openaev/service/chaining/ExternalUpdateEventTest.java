package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExternalUpdateEvent Tests")
class ExternalUpdateEventTest {

  // ========================================================================
  // equals Tests (custom implementation based on id only)
  // ========================================================================
  @Nested
  @DisplayName("equals")
  class EqualsTests {

    @Test
    @DisplayName("should return true when comparing same instance")
    void shouldReturnTrueForSameInstance() {
      ExternalUpdateEvent event = ExternalUpdateEvent.builder().build();
      assertEquals(event, event);
    }

    @Test
    @DisplayName("should return false when comparing with null")
    void shouldReturnFalseForNull() {
      ExternalUpdateEvent event = ExternalUpdateEvent.builder().build();
      assertNotEquals(null, event);
    }

    @Test
    @DisplayName("should return false when comparing with different class")
    void shouldReturnFalseForDifferentClass() {
      ExternalUpdateEvent event = ExternalUpdateEvent.builder().build();
      assertNotEquals(event, "not an ExternalUpdateEvent");
    }

    @Test
    @DisplayName("should return false for different instances (different auto-generated IDs)")
    void shouldReturnFalseForDifferentIds() {
      // Even with same field values, different instances have different IDs
      ExternalUpdateEvent event1 = ExternalUpdateEvent.builder().stepId("step1").build();
      ExternalUpdateEvent event2 = ExternalUpdateEvent.builder().stepId("step1").build();

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
      ExternalUpdateEvent event = ExternalUpdateEvent.builder().build();
      assertEquals(event.hashCode(), event.hashCode());
    }

    @Test
    @DisplayName("should differ for different instances")
    void shouldDifferForDifferentInstances() {
      ExternalUpdateEvent event1 = ExternalUpdateEvent.builder().build();
      ExternalUpdateEvent event2 = ExternalUpdateEvent.builder().build();

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
    @DisplayName("should return stepId")
    void shouldReturnStepId() {
      ExternalUpdateEvent event = ExternalUpdateEvent.builder().stepId("step-456").build();

      assertEquals("step-456", event.getUniqueElementKey());
    }

    @Test
    @DisplayName("should return null when stepId is null")
    void shouldReturnNullWhenStepIdIsNull() {
      ExternalUpdateEvent event = ExternalUpdateEvent.builder().build();

      assertNull(event.getUniqueElementKey());
    }
  }
}
