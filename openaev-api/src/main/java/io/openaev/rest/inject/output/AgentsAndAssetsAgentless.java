package io.openaev.rest.inject.output;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public record AgentsAndAssetsAgentless(
    @NotNull Set<Agent> agents, @NotNull Set<Asset> assetsAgentless) {}
