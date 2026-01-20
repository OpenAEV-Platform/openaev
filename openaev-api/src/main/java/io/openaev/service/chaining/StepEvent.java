package io.openaev.service.chaining;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.helper.queue.Queueable;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepEvent implements Queueable {

  @Getter private final String id = UUID.randomUUID().toString();

  @JsonProperty("workflow_id")
  private String workflowId;

  @JsonProperty("step_id")
  private String stepId;

  @JsonProperty("event_emission_date")
  private long emissionDate;

  @Override
  public boolean equals(Object o) {
    if (o instanceof StepEvent) {
      return id != null && id.equals(((StepEvent) o).getId());
    }
    return false;
  }

  @Override
  public String getUniqueElementKey() {
    return workflowId + stepId;
  }
}
