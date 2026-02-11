package io.openaev.rest.inject.output;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AgentsAndAssetsAgentless(
    @NotNull Set<Agent> agents, @NotNull Set<Asset> assetsAgentless) {}
