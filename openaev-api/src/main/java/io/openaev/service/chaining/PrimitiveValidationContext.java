package io.openaev.service.chaining;

import java.util.Set;

public record PrimitiveValidationContext(
    Set<String> allowlistedAssetGroupIds,
    Set<String> allowlistedAssetIds,
    Set<String> allowlistedDomains,
    Set<String> allowlistedIps,
    Set<String> allowlistedSubnets,
    Set<String> denylistedAssetGroupIds,
    Set<String> denylistedAssetIds,
    Set<String> denylistedDomains,
    Set<String> denylistedIps,
    Set<String> denylistedSubnets) {}
