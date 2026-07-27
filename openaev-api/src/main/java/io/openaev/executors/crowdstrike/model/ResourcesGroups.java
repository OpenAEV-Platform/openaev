package io.openaev.executors.crowdstrike.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourcesGroups {

  private List<CrowdStrikeHostGroup> resources;
  private CrowdstrikeMeta meta;
  private List<CrowdstrikeError> errors;
}
