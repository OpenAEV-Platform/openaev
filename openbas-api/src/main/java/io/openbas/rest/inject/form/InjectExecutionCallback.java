package io.openbas.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InjectExecutionCallback {

  private String id = UUID.randomUUID().toString();

  @JsonProperty("agent_id")
  private String agentId;

  @JsonProperty("inject_id")
  private String injectId;

  @JsonProperty("inject_execution_input")
  private InjectExecutionInput injectExecutionInput;

  @JsonProperty("execution_emission_date")
  private long emissionDate;

  @Override
  public boolean equals(Object o) {
    if (o instanceof InjectExecutionCallback) {
      return id != null && id.equals(((InjectExecutionCallback) o).getId());
    }
    return false;
  }
}
