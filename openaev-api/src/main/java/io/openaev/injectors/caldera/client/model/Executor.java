package io.openaev.injectors.caldera.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Executor {

  private String name;
  private String platform;
  private String command;
  private String commandExecutor;
  private List<String> cleanup;
}
