package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;

/**
 * Interface for handlers that can process findings. Only handlers that support
 * ProcessingContext.FINDING should implement this.
 */
public interface FindingCapable {

  /** Convert JSON node to finding value string */
  String toFindingValue(JsonNode jsonNode);

  /**
   * Extract asset IDs from JSON node for finding linking. Default implementation returns empty
   * list.
   */
  default List<String> toFindingAssets(JsonNode jsonNode) {
    return Collections.emptyList();
  }

  /**
   * Extract user IDs from JSON node for finding linking. Default implementation returns empty list.
   */
  default List<String> toFindingUsers(JsonNode jsonNode) {
    return Collections.emptyList();
  }

  /**
   * Extract team IDs from JSON node for finding linking. Default implementation returns empty list.
   */
  default List<String> toFindingTeams(JsonNode jsonNode) {
    return Collections.emptyList();
  }
}
