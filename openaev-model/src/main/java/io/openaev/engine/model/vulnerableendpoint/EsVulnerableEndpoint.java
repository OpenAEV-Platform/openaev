package io.openaev.engine.model.vulnerableendpoint;

import io.openaev.annotation.EsQueryable;
import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.database.model.Endpoint;
import io.openaev.engine.model.EsBase;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "vulnerable-endpoint", label = "Vulnerable Endpoint")
public class EsVulnerableEndpoint extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(label = "vulnerable endpoint id")
  @EsQueryable(keyword = true)
  private String vulnerable_endpoint_id;

  @Queryable(label = "vulnerable endpoint hostname", filterable = true)
  @EsQueryable(keyword = true)
  private String vulnerable_endpoint_hostname;

  @Queryable(
      label = "vulnerable endpoint platform",
      filterable = true,
      refEnumClazz = Endpoint.PLATFORM_TYPE.class)
  @EsQueryable(keyword = true)
  private String vulnerable_endpoint_platform;

  @Queryable(
      label = "vulnerable endpoint architecture",
      filterable = true,
      refEnumClazz = Endpoint.PLATFORM_ARCH.class)
  @EsQueryable(keyword = true)
  private String vulnerable_endpoint_architecture;

  // -- DENORMALISATION --

  @Queryable(
      label = "vulnerable endpoint agents privileges",
      filterable = true,
      refEnumClazz = AgentPrivilege.class)
  @EsQueryable(keyword = true)
  private List<String> vulnerable_endpoint_agents_privileges;

  @Queryable(
      label = "vulnerable endpoint agents active status",
      filterable = true,
      refEnumClazz = AgentStatus.class)
  @EsQueryable(keyword = true)
  private List<Boolean> vulnerable_endpoint_agents_active_status;

  @Queryable(
      label = "vulnerable endpoint action",
      filterable = true,
      refEnumClazz = VulnerableEndpointAction.class)
  @EsQueryable(keyword = true)
  private String vulnerable_endpoint_action;

  @Queryable(label = "vulnerable endpoint findings summary", filterable = true)
  @EsQueryable(keyword = true)
  private String vulnerable_endpoint_findings_summary;

  // -- SIDE --

  @Queryable(label = "simulation", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_simulation_side; // Must finish by _side

  @Queryable(label = "scenario", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private String base_scenario_side; // Must finish by _side

  @Queryable(label = "agents")
  @EsQueryable(keyword = true)
  private Set<String> base_agents_side; // Must finish by _side

  @Queryable(label = "findings")
  @EsQueryable(keyword = true)
  private Set<String> base_findings_side; // Must finish by _side

  @Queryable(label = "tags", filterable = true, dynamicValues = true)
  @EsQueryable(keyword = true)
  private Set<String> base_tags_side; // Must finish by _side
}
