package io.openaev.api.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Scope and payload of a bulk marking replacement: the same select-all-with-exclusions shape as
 * {@code AssetBulkProcessingInput}, plus the markings to replace each selected asset's set with.
 *
 * <p>Replace-the-whole-set, the bulk mirror of {@link AssetUpdateMarkingsInput}: every asset in
 * scope ends up carrying exactly {@code markingIds}, nothing kept and nothing merged. An empty list
 * clears every selected asset's markings, same as the single-asset endpoint. Capability is gated by
 * the same per-direction rule as the single-asset endpoint - {@code DELETE_MARKING_ASSIGNMENT} only
 * if some asset in scope would lose a marking, {@code ASSIGN_MARKING} only if some asset would gain
 * one - aggregated over the whole scope upfront (see {@code
 * AssetMarkingsService#findBulkDiffDirections}) rather than per asset during the chunked write, so
 * the gate still fails closed before any chunk runs instead of discovering a missing capability
 * mid-write on some later chunk.
 */
public record AssetBulkReplaceMarkingsInput(
    @JsonProperty("search_pagination_input") SearchPaginationInput searchPaginationInput,
    @JsonProperty("asset_ids_to_process") List<String> assetIdsToProcess,
    @JsonProperty("asset_ids_to_ignore") List<String> assetIdsToIgnore,
    @JsonProperty("marking_ids") @NotNull List<String> markingIds) {}
