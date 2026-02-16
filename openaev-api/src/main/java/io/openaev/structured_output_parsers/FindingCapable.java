package io.openaev.structured_output_parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Set;

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
  default Set<String> toFindingAssets(JsonNode jsonNode) {
    return Collections.emptySet();
  }

  /**
   * Extract user IDs from JSON node for finding linking. Default implementation returns empty list.
   */
  default Set<String> toFindingUsers(JsonNode jsonNode) {
    return Collections.emptySet();
  }

  /**
   * Extract team IDs from JSON node for finding linking. Default implementation returns empty list.
   */
  default Set<String> toFindingTeams(JsonNode jsonNode) {
    return Collections.emptySet();
  }
}
