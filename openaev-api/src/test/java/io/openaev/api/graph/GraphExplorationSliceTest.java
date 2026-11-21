package io.openaev.api.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.api.graph.dto.GraphEdgeType;
import io.openaev.api.graph.dto.GraphNodeOutput;
import io.openaev.api.graph.dto.GraphNodeType;
import io.openaev.api.graph.dto.GraphOutput;
import io.openaev.api.graph.dto.GraphResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure tests of the exploration graph's slice assembly and result rules (#9388). */
class GraphExplorationSliceTest {

  @Test
  @DisplayName("every node type has its own expansion")
  void everyNodeTypeExpands() {
    GraphExplorationService service = new GraphExplorationService();
    for (GraphNodeType type : GraphNodeType.values()) {
      assertThat(service.expands(type)).as(type.name()).isTrue();
    }
    assertThatThrownBy(() -> service.expand(null, List.of(), null, "x", null, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("an expectation without a score is pending, below its expected score failed")
  void resultOfScore() {
    assertThat(GraphResult.ofScore(null, 100.0)).isEqualTo(GraphResult.PENDING);
    assertThat(GraphResult.ofScore(100.0, 100.0)).isEqualTo(GraphResult.SUCCESS);
    assertThat(GraphResult.ofScore(0.0, 100.0)).isEqualTo(GraphResult.FAILED);
    assertThat(GraphResult.ofScore(50.0, 50.0)).isEqualTo(GraphResult.SUCCESS);
    assertThat(GraphResult.ofScore(99.0, null)).isEqualTo(GraphResult.FAILED);
  }

  @Test
  @DisplayName("the worst result wins: one failed expectation marks the link failed")
  void worstWins() {
    assertThat(GraphResult.worst(GraphResult.SUCCESS, GraphResult.FAILED))
        .isEqualTo(GraphResult.FAILED);
    assertThat(GraphResult.worst(GraphResult.PENDING, GraphResult.SUCCESS))
        .isEqualTo(GraphResult.PENDING);
    assertThat(GraphResult.worst(null, GraphResult.SUCCESS)).isEqualTo(GraphResult.SUCCESS);
    assertThat(GraphResult.worst(GraphResult.FAILED, null)).isEqualTo(GraphResult.FAILED);
  }

  @Test
  @DisplayName("nodes and edges are deduplicated and failed counts follow the TARGETS edges")
  void sliceDeduplicatesAndCounts() {
    GraphExplorationService.Slice slice = new GraphExplorationService.Slice();
    String asset = slice.node(GraphNodeType.ASSET, "a1", "web-01", "Endpoint");
    String inject = slice.node(GraphNodeType.INJECT, "i1", "Mimikatz", null);
    slice.node(GraphNodeType.ASSET, "a1", "ignored", "Endpoint");
    slice.edge(GraphEdgeType.TARGETS, inject, asset, GraphResult.SUCCESS);
    slice.edge(GraphEdgeType.TARGETS, inject, asset, GraphResult.FAILED);
    String finding = slice.node(GraphNodeType.FINDING, "f1", "CVE-2024-0001", "cve");
    slice.edge(GraphEdgeType.FOUND_ON, finding, asset, null);

    GraphOutput out = slice.build(asset, n -> true, List.of());

    assertThat(out.nodes()).hasSize(3);
    assertThat(out.edges()).hasSize(2);
    GraphNodeOutput assetNode =
        out.nodes().stream().filter(n -> n.nodeId().equals(asset)).findFirst().orElseThrow();
    assertThat(assetNode.label()).isEqualTo("web-01");
    assertThat(assetNode.result()).isEqualTo(GraphResult.FAILED);
    assertThat(assetNode.failedCount()).isEqualTo(1);
    assertThat(assetNode.findingCount()).isEqualTo(1);
    assertThat(out.edges())
        .filteredOn(e -> e.edgeType() == GraphEdgeType.TARGETS)
        .singleElement()
        .satisfies(e -> assertThat(e.result()).isEqualTo(GraphResult.FAILED));
  }

  @Test
  @DisplayName("a node the caller cannot read is dropped with every edge touching it")
  void invisibleNodesDropTheirEdges() {
    GraphExplorationService.Slice slice = new GraphExplorationService.Slice();
    String inject = slice.node(GraphNodeType.INJECT, "i1", "Payload", null);
    String simulation = slice.node(GraphNodeType.SIMULATION, "s1", "Q3 run", null);
    slice.edge(GraphEdgeType.PART_OF, inject, simulation, null);

    GraphOutput out = slice.build(inject, n -> n.nodeType() != GraphNodeType.SIMULATION, List.of());

    assertThat(out.nodes()).extracting(GraphNodeOutput::nodeId).containsExactly(inject);
    assertThat(out.edges()).isEmpty();
  }

  @Test
  @DisplayName("the limit is clamped between 1 and the hard ceiling")
  void limitClamped() {
    assertThat(GraphExplorationService.clamp(0)).isEqualTo(1);
    assertThat(GraphExplorationService.clamp(10_000)).isEqualTo(GraphExplorationService.MAX_LIMIT);
  }

  @Test
  @DisplayName("entity types parse case-insensitively from the path segment")
  void typeParsing() {
    assertThat(GraphNodeType.fromParam("asset_group")).isEqualTo(GraphNodeType.ASSET_GROUP);
    assertThat(GraphNodeType.fromParam(" Finding ")).isEqualTo(GraphNodeType.FINDING);
    assertThatThrownBy(() -> GraphNodeType.fromParam(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
