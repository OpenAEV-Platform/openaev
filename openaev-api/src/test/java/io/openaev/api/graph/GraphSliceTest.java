package io.openaev.api.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.api.graph.dto.GraphEdgeType;
import io.openaev.api.graph.dto.GraphNodeOutput;
import io.openaev.api.graph.dto.GraphNodeType;
import io.openaev.api.graph.dto.GraphOutput;
import io.openaev.api.graph.dto.GraphResult;
import io.openaev.expectation.ExpectationType;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The slice builder and the verdict helpers of the graph (#9447), without a database. */
class GraphSliceTest {

  private static final String PREVENTED = ExpectationType.PREVENTION.successLabel;
  private static final String DETECTED = ExpectationType.DETECTION.successLabel;

  @Test
  @DisplayName(
      "an attack-path step is a success when prevented, a failure otherwise, pending unread")
  void stepVerdict() {
    assertThat(GraphExplorationService.stepResult(PREVENTED, null)).isEqualTo(GraphResult.SUCCESS);
    assertThat(GraphExplorationService.stepResult(PREVENTED, DETECTED))
        .isEqualTo(GraphResult.SUCCESS);
    assertThat(GraphExplorationService.stepResult("Not Prevented", DETECTED))
        .isEqualTo(GraphResult.FAILED);
    assertThat(GraphExplorationService.stepResult("Not Prevented", null))
        .isEqualTo(GraphResult.FAILED);
    assertThat(GraphExplorationService.stepResult(null, null)).isEqualTo(GraphResult.PENDING);
  }

  @Test
  @DisplayName("a person is labelled by full name, else email, else id")
  void personLabel() {
    assertThat(GraphExplorationService.personLabel("u1", "Ada", "Lovelace", "ada@x.io"))
        .isEqualTo("Ada Lovelace");
    assertThat(GraphExplorationService.personLabel("u1", null, "Lovelace", null))
        .isEqualTo("Lovelace");
    assertThat(GraphExplorationService.personLabel("u1", " ", null, "ada@x.io"))
        .isEqualTo("ada@x.io");
    assertThat(GraphExplorationService.personLabel("u1", null, null, null)).isEqualTo("u1");
  }

  @Test
  @DisplayName("an attack-path step shows only the verdicts of the simulations the caller reads")
  void stepGatedPerSimulation() {
    GraphExplorationService.Slice slice = new GraphExplorationService.Slice();
    String a = slice.node(GraphNodeType.ASSET, "a", "host-a", "Endpoint");
    String b = slice.node(GraphNodeType.ASSET, "b", "host-b", "Endpoint");
    slice.step(a, b, "visible", GraphResult.SUCCESS);
    slice.step(a, b, "hidden", GraphResult.FAILED);

    Predicate<GraphNodeOutput> onlyVisible =
        n -> n.nodeType() != GraphNodeType.SIMULATION || "visible".equals(n.entityId());
    GraphOutput out = slice.build(null, onlyVisible, List.of());

    assertThat(out.edges())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.edgeType()).isEqualTo(GraphEdgeType.ATTACK_STEP);
              assertThat(e.result()).isEqualTo(GraphResult.SUCCESS);
            });
    // A step is never counted into its nodes' results: it is a verdict of its own.
    assertThat(out.nodes()).allSatisfy(n -> assertThat(n.result()).isNull());
  }

  @Test
  @DisplayName("an attack-path step of a simulation the caller cannot read is dropped")
  void stepDroppedWhenNoSimulationVisible() {
    GraphExplorationService.Slice slice = new GraphExplorationService.Slice();
    String a = slice.node(GraphNodeType.ASSET, "a", "host-a", "Endpoint");
    String b = slice.node(GraphNodeType.ASSET, "b", "host-b", "Endpoint");
    slice.step(a, b, "hidden", GraphResult.FAILED);
    slice.edge(GraphEdgeType.MEMBER_OF, a, b, null);

    GraphOutput out = slice.build(null, n -> n.nodeType() != GraphNodeType.SIMULATION, List.of());

    assertThat(out.edges()).extracting(e -> e.edgeType()).containsExactly(GraphEdgeType.MEMBER_OF);
  }

  @Test
  @DisplayName("the worst verdict of the visible simulations wins on a shared step")
  void stepWorstOfVisible() {
    GraphExplorationService.Slice slice = new GraphExplorationService.Slice();
    String a = slice.node(GraphNodeType.ASSET, "a", "host-a", "Endpoint");
    String b = slice.node(GraphNodeType.ASSET, "b", "host-b", "Endpoint");
    slice.step(a, b, "s1", GraphResult.SUCCESS);
    slice.step(a, b, "s2", GraphResult.FAILED);

    GraphOutput out = slice.build(null, n -> true, List.of());

    assertThat(out.edges())
        .singleElement()
        .satisfies(e -> assertThat(e.result()).isEqualTo(GraphResult.FAILED));
  }

  @Test
  @DisplayName("a node carries the latest date its rows give, and none without one")
  void nodeDate() {
    GraphExplorationService.Slice slice = new GraphExplorationService.Slice();
    String s = slice.node(GraphNodeType.SIMULATION, "s", "Run 1", null);
    String a = slice.node(GraphNodeType.ASSET, "a", "host-a", "Endpoint");
    Instant older = Instant.parse("2026-10-01T00:00:00Z");
    Instant newer = Instant.parse("2026-11-01T00:00:00Z");
    slice.date(s, older);
    slice.date(s, newer);
    slice.date(s, older);
    slice.date(a, null);

    GraphOutput out = slice.build(null, n -> true, List.of());

    assertThat(out.nodes())
        .filteredOn(n -> n.nodeId().equals(s))
        .singleElement()
        .satisfies(n -> assertThat(n.date()).isEqualTo(newer));
    assertThat(out.nodes())
        .filteredOn(n -> n.nodeId().equals(a))
        .singleElement()
        .satisfies(n -> assertThat(n.date()).isNull());
  }

  @Test
  @DisplayName("people node types parse from their path segment")
  void peopleTypesParse() {
    assertThat(GraphNodeType.fromParam("person")).isEqualTo(GraphNodeType.PERSON);
    assertThat(GraphNodeType.fromParam("Organization")).isEqualTo(GraphNodeType.ORGANIZATION);
    assertThat(GraphNodeType.fromParam(" team ")).isEqualTo(GraphNodeType.TEAM);
  }
}
