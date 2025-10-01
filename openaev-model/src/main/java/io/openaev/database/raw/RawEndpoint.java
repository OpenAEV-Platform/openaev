package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawEndpoint extends RawAsset {
  Set<String> getEndpoint_ips();

  String getEndpoint_hostname();

  String getEndpoint_platform();

  String getEndpoint_arch();

  Set<String> getEndpoint_mac_addresses();

  String getEndpoint_seen_ip();

  boolean getEndpoint_is_eol();

  Set<String> getEndpoint_exercises();

  Set<String> getEndpoint_scenarios();

  Instant getEndpoint_updated_at();
}
