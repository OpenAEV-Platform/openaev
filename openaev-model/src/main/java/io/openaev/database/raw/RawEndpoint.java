package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for endpoint asset data.
 *
 * <p>This interface extends {@link RawAsset} with endpoint-specific fields including network
 * information, platform details, and exercise/scenario associations. Used for queries that need
 * detailed endpoint information without full entity loading.
 *
 * @see io.openaev.database.model.Endpoint
 * @see RawAsset
 */
public interface RawEndpoint extends RawAsset {

  /**
   * Returns the set of IP addresses assigned to this endpoint.
   *
   * @return set of IP addresses (IPv4 or IPv6 format)
   */
  Set<String> getEndpoint_ips();

  /**
   * Returns the hostname of the endpoint.
   *
   * @return the endpoint hostname
   */
  String getEndpoint_hostname();

  /**
   * Returns the operating system platform type.
   *
   * @return the platform type (e.g., "Linux", "Windows", "macOS")
   */
  String getEndpoint_platform();

  /**
   * Returns the CPU architecture of the endpoint.
   *
   * @return the architecture (e.g., "x86_64", "arm64")
   */
  String getEndpoint_arch();

  /**
   * Returns the set of MAC addresses for the endpoint's network interfaces.
   *
   * @return set of MAC addresses
   */
  Set<String> getEndpoint_mac_addresses();

  /**
   * Returns the IP address from which the endpoint was last seen connecting.
   *
   * @return the last seen IP address
   */
  String getEndpoint_seen_ip();

  /**
   * Returns whether the endpoint's operating system has reached end-of-life.
   *
   * @return {@code true} if the OS is end-of-life, {@code false} otherwise
   */
  boolean getEndpoint_is_eol();

  /**
   * Returns the set of exercise IDs this endpoint participates in.
   *
   * @return set of exercise IDs
   */
  Set<String> getEndpoint_exercises();

  /**
   * Returns the set of scenario IDs this endpoint is configured for.
   *
   * @return set of scenario IDs
   */
  Set<String> getEndpoint_scenarios();

  /**
   * Returns the last update timestamp for the endpoint.
   *
   * @return the update timestamp
   */
  Instant getEndpoint_updated_at();
}
