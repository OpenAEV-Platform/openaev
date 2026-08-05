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
  @DisplayName("A finding row id is deterministic and changes with each component")
  void finding_row_identity() {
    String base = AttackPathIds.findingRow("sim-1", "cve", "text_field", "CVE-1", "asset-1");
    assertThat(base)
        .isEqualTo(AttackPathIds.findingRow("sim-1", "cve", "text_field", "CVE-1", "asset-1"));
    // Changing any one component yields a different id.
    assertThat(base)
        .isNotEqualTo(AttackPathIds.findingRow("sim-2", "cve", "text_field", "CVE-1", "asset-1"))
        .isNotEqualTo(
            AttackPathIds.findingRow("sim-1", "credentials", "text_field", "CVE-1", "asset-1"))
        .isNotEqualTo(AttackPathIds.findingRow("sim-1", "cve", "other_field", "CVE-1", "asset-1"))
        .isNotEqualTo(AttackPathIds.findingRow("sim-1", "cve", "text_field", "CVE-2", "asset-1"))
        .isNotEqualTo(AttackPathIds.findingRow("sim-1", "cve", "text_field", "CVE-1", "asset-2"));
    // A null field (seed rows) is deterministic and stays distinct from a blank field.
    assertThat(AttackPathIds.findingRow("sim-1", "cve", null, "CVE-1", "asset-1"))
        .isEqualTo(AttackPathIds.findingRow("sim-1", "cve", null, "CVE-1", "asset-1"))
        .isNotEqualTo(AttackPathIds.findingRow("sim-1", "cve", "", "CVE-1", "asset-1"));
  }

  @Test
  @DisplayName("A short finding row id keeps its legacy (un-hashed) form across upgrades")
  void finding_row_id_keeps_legacy_form_for_short_values() {
    // Rows copied before the value hashing was introduced carry the raw-value id; a short value
    // must keep resolving to that exact id so a post-upgrade re-copy upserts onto the existing row
    // instead of inserting a duplicate (the natural-key index was dropped, the PK is the dedup).
    assertThat(AttackPathIds.findingRow("sim-1", "cve", "text_field", "CVE-1", "asset-1"))
        .isEqualTo("FINDING_ROW|sim-1|cve|text_field|CVE-1|asset-1");
  }

  @Test
  @DisplayName("A finding row id stays bounded and deterministic even for a very long value")
  void finding_row_id_is_bounded_for_long_values() {
    // ADR-004 lets arbitrarily long parsed outputs reach attackpath_finding; when the raw encoding
    // would overflow the varchar(255) primary key, the value is hashed (FINDING_ROW_H namespace).
    String longValue = "x".repeat(10_000);
    String id = AttackPathIds.findingRow("sim-1", "output", "text_field", longValue, "asset-1");

    assertThat(id.length()).isLessThanOrEqualTo(255);
    assertThat(id).startsWith("FINDING_ROW_H|");
    // Deterministic: the same long value always yields the same id.
    assertThat(id)
        .isEqualTo(AttackPathIds.findingRow("sim-1", "output", "text_field", longValue, "asset-1"));
    // Two different long values (even sharing a long prefix) yield different ids: the whole value
    // is hashed, never truncated.
    assertThat(id)
        .isNotEqualTo(
            AttackPathIds.findingRow("sim-1", "output", "text_field", longValue + "y", "asset-1"));
    // The hashed namespace can never collide with a raw id: a short value crafted to look like the
    // long value's hashed component still lives under the FINDING_ROW kind.
    String hashedComponent = id.substring("FINDING_ROW_H|".length());
    assertThat(
            AttackPathIds.findingRow("sim-1", "output", "text_field", hashedComponent, "asset-1"))
        .startsWith("FINDING_ROW|")
        .isNotEqualTo(id);
  }

  @Test
  @DisplayName("A finding row id stays bounded when the overflow comes from another component")
  void finding_row_id_is_bounded_when_other_components_overflow() {
    // Hashing the value cannot shrink an id whose excess length comes from, e.g., a very long
    // endpoint key: the last resort hashes the whole raw id into the fixed-size FINDING_ROW_F
    // form, which stays deterministic and distinct per natural key.
    String longEndpointKey = "k".repeat(300);
    String id = AttackPathIds.findingRow("sim-1", "port", "text_field", "22", longEndpointKey);

    assertThat(id.length()).isLessThanOrEqualTo(255);
    assertThat(id).startsWith("FINDING_ROW_F|");
    assertThat(id)
        .isEqualTo(AttackPathIds.findingRow("sim-1", "port", "text_field", "22", longEndpointKey));
    assertThat(id)
        .isNotEqualTo(
            AttackPathIds.findingRow("sim-1", "port", "text_field", "23", longEndpointKey))
        .isNotEqualTo(
            AttackPathIds.findingRow("sim-1", "port", "text_field", "22", longEndpointKey + "k"));
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
  @DisplayName(
      "An injector node id is per (injector, contract); a null contract falls back to the per-injector"
          + " id")
  void injector_node_per_contract() {
    // A contract-bearing injector node is a distinct, 3-segment id.
    assertThat(AttackPathIds.injectorNode("NMAP", "C-1")).isEqualTo("NODE_INJECTOR|NMAP|C-1");
    // A null contract falls back to the per-injector id (byte-identical to the 1-arg form), so
    // contractless/seed/legacy injectors keep their current id.
    assertThat(AttackPathIds.injectorNode("NMAP", null))
        .isEqualTo(AttackPathIds.injectorNode("NMAP"))
        .isEqualTo("NODE_INJECTOR|NMAP");
    // Two contracts of the same injector never collide, and neither collides with the per-injector
    // id.
    assertThat(AttackPathIds.injectorNode("NMAP", "C-1"))
        .isNotEqualTo(AttackPathIds.injectorNode("NMAP", "C-2"))
        .isNotEqualTo(AttackPathIds.injectorNode("NMAP"));
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
