package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.repository.RoleRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RoleServiceTest extends IntegrationTest {

  @Mock RoleRepository roleRepository;

  @InjectMocks RoleService roleService;

  @Test
  void test_getCapabilitiesWithParents_when_inputwithmissingparent_then_should_add_parent() {
    Set<Capability> input = Set.of(Capability.MANAGE_CHANNELS);
    Set<Capability> output = roleService.getCapabilitiesWithParents(input);
    assertEquals(2, output.size());
    assertTrue(output.contains(Capability.MANAGE_CHANNELS));
    assertTrue(output.contains(Capability.ACCESS_CHANNELS));
  }

  @Test
  void test_getCapabilitiesWithParents_when_inputwithnomissingparent_then_should_return_input() {
    Set<Capability> input = Set.of(Capability.ACCESS_CHANNELS);
    Set<Capability> output = roleService.getCapabilitiesWithParents(input);
    assertEquals(1, output.size());
    assertTrue(output.contains(Capability.ACCESS_CHANNELS));
  }
}
