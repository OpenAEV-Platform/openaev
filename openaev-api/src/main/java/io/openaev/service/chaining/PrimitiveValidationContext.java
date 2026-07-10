package io.openaev.service.chaining;

import java.util.Set;

public record PrimitiveValidationContext(
    Set<String> allowedAssetGroupIds,
    Set<String> allowedAssetIds,
    Set<String> allowlistedDomains,
    Set<String> allowlistedIps,
    Set<String> allowlistedSubnets,
    Set<String> denylistedDomains,
    Set<String> denylistedIps,
    Set<String> denylistedSubnets) {}
