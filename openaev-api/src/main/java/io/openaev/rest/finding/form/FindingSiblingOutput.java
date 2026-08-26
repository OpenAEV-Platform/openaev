package io.openaev.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * One row of the "Also Detected On" panel (finding_triforce_design.md, Task 1): a sibling Finding
 * sharing the same Type + Value as the Finding being viewed, but a different Location. Extends
 * {@link AggregatedFindingOutput} so the frontend can reuse the exact same row-rendering component
 * as the main Finding list (same fields, same badges), adding only the two pieces of information
 * that view doesn't otherwise carry: which single asset is this sibling's Location, and whether it
 * is currently archived (Decision #10: archived siblings are always included, never hidden, flagged
 * with a badge instead).
 */
@Data
@SuperBuilder
@JsonInclude(NON_NULL)
public class FindingSiblingOutput extends AggregatedFindingOutput {

  @Schema(
      description =
          "The single asset that is this sibling Finding's Location (Triforce identity, Phase 1)."
              + " Null for findings not yet covered by the Location backfill (multi-asset or"
              + " unlocated finding types - see Finding#locationAsset).")
  @JsonProperty("finding_location")
  private EndpointSimple location;

  @Schema(
      description =
          "Whether this sibling is currently archived (manually, or by re-detection timeout) -"
              + " always included per Decision #10, never hidden from this panel.")
  @JsonProperty("finding_archived")
  private boolean archived;
}
