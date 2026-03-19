package io.openaev.executors.paloaltocortex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaloAltoCortexCommandList {

  private List<String> commands_list;
}
