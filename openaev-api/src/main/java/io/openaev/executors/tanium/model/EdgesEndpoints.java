package io.openaev.executors.tanium.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EdgesEndpoints {

  private List<NodeEndpoint> edges;
}
