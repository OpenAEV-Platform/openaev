package io.openaev.service;

import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.utils.IpAddressUtils;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves a raw {@code host} string (IP or hostname) to a unique asset ID within a tenant.
 *
 * <p>Promotion to {@code asset_id} occurs ONLY when exactly one endpoint matches. Zero or multiple
 * matches yield {@link Optional#empty()}, leaving the caller to keep the original host value.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetCorrelationResolver {

  private final EndpointRepository endpointRepository;

  /**
   * Attempts to promote a host string to an asset ID via unique endpoint resolution.
   *
   * @param host the host string (IPv4, IPv6, or hostname)
   * @param tenantId the current tenant context
   * @return the unique asset ID if exactly one endpoint matches, otherwise empty
   */
  public Optional<String> resolveAssetId(String host, String tenantId) {
    if (host == null || host.isBlank() || tenantId == null || tenantId.isBlank()) {
      return Optional.empty();
    }

    List<Endpoint> candidates;

    if (IpAddressUtils.isIpv4Address(host) || IpAddressUtils.isIpv6Address(host)) {
      log.trace("Resolving host as IP address: {}", host);
      candidates = endpointRepository.findByAtLeastOneIp(new String[] {host}, tenantId);
    } else {
      log.trace("Resolving host as hostname: {}", host);
      candidates = endpointRepository.findByHostname(host, tenantId);
    }

    if (candidates.size() == 1) {
      String assetId = candidates.getFirst().getId();
      log.debug("Host '{}' uniquely resolved to asset_id '{}'", host, assetId);
      return Optional.of(assetId);
    }

    log.trace("Host '{}' resolved to {} endpoint(s) — no promotion", host, candidates.size());
    return Optional.empty();
  }
}
