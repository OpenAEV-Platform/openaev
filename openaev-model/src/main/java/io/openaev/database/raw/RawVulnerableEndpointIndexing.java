package io.openaev.database.raw;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface RawVulnerableEndpointIndexing extends RawTenant {
  String getBase_id();

  String getVulnerable_endpoint_id();

  Instant getVulnerable_endpoint_created_at();

  Instant getVulnerable_endpoint_updated_at();

  String getVulnerable_endpoint_hostname();

  String getVulnerable_endpoint_platform();

  String getVulnerable_endpoint_architecture();

  Boolean getVulnerable_endpoint_eol();

  String getVulnerable_endpoint_simulation();

  String getVulnerable_endpoint_scenario();

  Set<String> getVulnerable_endpoint_agents();

  List<String> getVulnerable_endpoint_agents_privileges();

  List<String> getVulnerable_endpoint_agents_statuses();

  Set<String> getVulnerable_endpoint_findings();

  Set<String> getVulnerable_endpoint_cves();

  Set<String> getVulnerable_endpoint_tags();
}
