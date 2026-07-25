package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection feeding the {@code asset} search index with the whole asset inventory.
 *
 * <p>Extends {@link RawAssetIndexing} with the host attributes carried by the Endpoint subclass
 * (null for every other asset category) and the simulation / scenario associations.
 *
 * @see io.openaev.database.model.Asset
 * @see RawAssetIndexing
 */
public interface RawIndexedAsset extends RawAssetIndexing {

  /**
   * Returns the set of IP addresses assigned to this asset.
   *
   * @return set of IP addresses (IPv4 or IPv6 format)
   */
  Set<String> getAsset_ips();

  /**
   * Returns the hostname of the asset.
   *
   * @return the asset hostname
   */
  String getAsset_hostname();

  /**
   * Returns the CPU architecture, for host assets only.
   *
   * @return the architecture (e.g., "x86_64", "arm64"), or {@code null}
   */
  String getEndpoint_arch();

  /**
   * Returns the set of MAC addresses for the asset's network interfaces.
   *
   * @return set of MAC addresses
   */
  Set<String> getAsset_mac_addresses();

  /**
   * Returns the IP address from which the asset was last seen connecting.
   *
   * @return the last seen IP address
   */
  String getAsset_seen_ip();

  /**
   * Returns whether the operating system has reached end-of-life, for host assets only.
   *
   * @return {@code true} if the OS is end-of-life, {@code false} otherwise
   */
  boolean getEndpoint_is_eol();

  /**
   * Returns the set of exercise IDs this asset participates in.
   *
   * @return set of exercise IDs
   */
  Set<String> getAsset_exercises();

  /**
   * Returns the set of scenario IDs this asset is configured for.
   *
   * @return set of scenario IDs
   */
  Set<String> getAsset_scenarios();

  /**
   * Returns the indexing sort timestamp: the most recent update across the asset itself and the
   * injects, simulations, scenarios and findings referencing it.
   *
   * @return the indexing cursor timestamp
   */
  Instant getAsset_indexed_at();

  /**
   * Returns the product taxonomy category of the asset (HOST, CONTAINER_WORKLOAD...).
   *
   * @return the asset category
   */
  String getAsset_category();
}
