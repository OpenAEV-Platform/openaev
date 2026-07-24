package io.openaev.service.attackpath.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * An edge of the attack-path graph (issue 6647), named after the design's {@code AttackPathEdges}.
 * Executions sharing {@code (source, target)} collapse into one edge carrying {@code count} and the
 * producing {@code executionIds}, so a finding still resolves to its action even when the execution
 * is rendered only as an edge.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttackPathEdges {

  private String edgeId;
  private String edgeSourceId;
  private String edgeTargetId;
  private String label;
  private String type;
  private int count;
  private List<String> executionIds = new ArrayList<>();
}
