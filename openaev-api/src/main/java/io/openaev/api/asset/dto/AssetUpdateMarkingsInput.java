package io.openaev.api.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The markings carried by an asset — its sensitivity labels, not a clearance.
 *
 * <p>Replace-the-whole-set, like {@code GroupUpdateMarkingsInput}: an empty list clears every
 * marking and makes the asset visible to everyone again. A PATCH-style add/remove would make "what
 * is this asset marked with?" depend on request ordering, which is the wrong property for a
 * security boundary.
 */
public record AssetUpdateMarkingsInput(
    @JsonProperty("asset_markings") @NotNull List<String> markingIds) {

  public AssetUpdateMarkingsInput {
    markingIds = markingIds == null ? List.of() : List.copyOf(markingIds);
  }
}
