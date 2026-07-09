package io.openaev.service.chaining;

import java.util.Set;

public record PrimitiveValidationContext(
    Set<String> allowedAssetIds,
    Set<String> allowedAssetGroupIds,
    Set<String> allowlistedIps,
    Set<String> denylistedIps,
    Set<String> allowlistedSubnets,
    Set<String> denylistedSubnets,
    Set<String> allowlistedDomains,
    Set<String> denylistedDomains) {}
