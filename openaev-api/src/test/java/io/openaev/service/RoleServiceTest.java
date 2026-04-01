package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.Capability;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleServiceTest {

  @Test
  void test_resolveWithParents_when_inputwithmissingparent_then_should_add_parent() {
    Set<Capability> input = Set.of(Capability.MANAGE_CHANNELS);
    Set<Capability> output = Capability.resolveWithParents(input);
    assertEquals(2, output.size());
    assertTrue(output.contains(Capability.MANAGE_CHANNELS));
    assertTrue(output.contains(Capability.ACCESS_CHANNELS));
  }

  @Test
  void test_resolveWithParents_when_inputwithnomissingparent_then_should_return_input() {
    Set<Capability> input = Set.of(Capability.ACCESS_CHANNELS);
    Set<Capability> output = Capability.resolveWithParents(input);
    assertEquals(1, output.size());
    assertTrue(output.contains(Capability.ACCESS_CHANNELS));
  }
}
