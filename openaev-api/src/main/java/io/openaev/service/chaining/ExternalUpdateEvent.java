package io.openaev.service.chaining;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.helper.queue.Queueable;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalUpdateEvent implements Queueable {

  @Getter
  private final String id = UUID.randomUUID().toString();

  @JsonProperty("step_id")
  private String stepId;

  @JsonProperty("event_emission_date")
  private long emissionDate;

  @Override
  public boolean equals(Object o) {
    if (o instanceof ExternalUpdateEvent) {
      return id != null && id.equals(((ExternalUpdateEvent) o).getId());
    }
    return false;
  }

  @Override
  public String getUniqueElementKey() {
    return stepId;
  }
}
