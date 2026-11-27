package io.openaev.rest.inject.service;

import io.openaev.database.model.Asset;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

/**
 * One resolution of an inject's targets, split by the launcher's marking clearance: the targets to
 * execute, and the ones dropped because the clearance does not cover their markings.
 */
public record ResolvedAssetsToExecute(
    @NotNull List<AssetToExecute> cleared, @NotNull Set<Asset> withoutClearance) {}
