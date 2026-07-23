package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the deterministic, collision-safe attack-path IDs. */
class AttackPathIdsTest {

  @Test
  @DisplayName("Same inputs always produce the same id (deterministic)")
  void deterministic() {
    assertThat(AttackPathIds.injectorNode("NMAP")).isEqualTo(AttackPathIds.injectorNode("NMAP"));
    assertThat(AttackPathIds.executionNode("e1", "asset-1", "agent-1"))
        .isEqualTo(AttackPathIds.executionNode("e1", "asset-1", "agent-1"));
    assertThat(AttackPathIds.findingTypeFindingEdge("credentials", "asset-1", "admin:secret"))
        .isEqualTo(AttackPathIds.findingTypeFindingEdge("credentials", "asset-1", "admin:secret"));
  }

  @Test
  @DisplayName("Different kinds with the same value never collide")
  void kinds_do_not_collide() {
    List<String> ids =
        List.of(
            AttackPathIds.injectorNode("X"),
            AttackPathIds.endpointNode("X"),
            AttackPathIds.executionNode("X", "X", "X"),
            AttackPathIds.executionsEdge("X", "X"),
            AttackPathIds.findingTypeNode("X", "X"),
            AttackPathIds.findingNode("X", "X"),
            AttackPathIds.endpointFindingTypeEdge("X", "X"),
            AttackPathIds.findingTypeFindingEdge("X", "X", "X"));
    assertThat(ids).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName(
      "A finding node dedups by (type, value) across endpoints; a finding-type node is per endpoint")
  void finding_dedup_semantics() {
    // The same (type, value) on two endpoints is one finding node (shared finding, e.g. a
    // credential reused across hosts).
    assertThat(AttackPathIds.findingNode("credentials", "admin:secret"))
        .isEqualTo(AttackPathIds.findingNode("credentials", "admin:secret"));
    // A finding-type node is scoped to its endpoint, so it differs per endpoint.
    assertThat(AttackPathIds.findingTypeNode("credentials", "asset-1"))
        .isNotEqualTo(AttackPathIds.findingTypeNode("credentials", "asset-2"));
  }

  @Test
  @DisplayName("Different values within a kind produce different ids")
  void distinct_values() {
    assertThat(AttackPathIds.findingNode("credentials", "a"))
        .isNotEqualTo(AttackPathIds.findingNode("credentials", "b"));
    assertThat(AttackPathIds.endpointNode("asset-1"))
        .isNotEqualTo(AttackPathIds.endpointNode("asset-2"));
  }

  @Test
  @DisplayName("The delimiter inside a component cannot cause a collision (injective encoding)")
  void delimiter_in_component_is_safe() {
    // Without escaping, both would encode to "NODE_FINDING|a|b|c".
    assertThat(AttackPathIds.findingNode("a", "b|c"))
        .isNotEqualTo(AttackPathIds.findingNode("a|b", "c"));
  }

  @Test
  @DisplayName("A null component never collides with a real value")
  void null_component_is_safe() {
    // null agent (injector-based execution) vs an empty-string agent vs a value equal to the
    // marker.
    List<String> ids =
        List.of(
            AttackPathIds.executionNode("e", "t", null),
            AttackPathIds.executionNode("e", "t", ""),
            AttackPathIds.executionNode("e", "t", "\\0"));
    assertThat(ids).doesNotHaveDuplicates();
    // null is deterministic
    assertThat(AttackPathIds.executionNode("e", "t", null))
        .isEqualTo(AttackPathIds.executionNode("e", "t", null));
  }

  @Test
  @DisplayName("The encoding is the documented kind-prefixed, delimiter-joined form")
  void readable_format() {
    assertThat(AttackPathIds.injectorNode("NMAP")).isEqualTo("NODE_INJECTOR|NMAP");
    assertThat(AttackPathIds.findingNode("credentials", "admin"))
        .isEqualTo("NODE_FINDING|credentials|admin");
  }

  @Test
  @DisplayName("isSeedId recognises synthetic seed simulation ids, and only those")
  void is_seed_id() {
    // A synthetic seed simulation id (the shape AttackPathSeedService builds) is a seed.
    assertThat(AttackPathIds.isSeedId(AttackPathIds.SEED_ID_PREFIX + "42-sim-0")).isTrue();
    // A real simulation id (a UUID) and null/blank are not seeds.
    assertThat(AttackPathIds.isSeedId("02d737db-d12f-40bf-b427-c70ef8bac6e0")).isFalse();
    assertThat(AttackPathIds.isSeedId(null)).isFalse();
    assertThat(AttackPathIds.isSeedId("")).isFalse();
  }
}
