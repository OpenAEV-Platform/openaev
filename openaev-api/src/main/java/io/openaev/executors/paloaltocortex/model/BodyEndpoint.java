package io.openaev.executors.paloaltocortex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BodyEndpoint {

  private int search_from;
  private int search_to;
  private List<PaloAltoCortexFilter> filters;
}
