package io.openaev.service.chaining;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.helper.queue.Queueable;
import java.util.Objects;
import java.util.UUID;
import lombok.*;

/**
 * Represents an event associated with a workflow step.
 *
 * <p>Each event has a unique ID and contains information about the workflow and step it belongs to,
 * along with the emission timestamp.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepEvent implements Queueable {

  /** Unique identifier for this event, automatically generated as a UUID. */
  @Builder.Default private final String id = UUID.randomUUID().toString();

  /** The ID of the workflow this event belongs to. */
  @JsonProperty("workflow_id")
  private String workflowId;

  /** The ID of the step this event is associated with. */
  @JsonProperty("step_id")
  private String stepId;

  /** The timestamp when this event was emitted, in milliseconds since epoch. */
  @JsonProperty("event_emission_date")
  private long emissionDate;

  /**
   * Compares this step event to another object for equality based on ID.
   *
   * @param o the object to compare with
   * @return true if the objects are equal, false otherwise
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StepEvent stepEvent = (StepEvent) o;
    return Objects.equals(id, stepEvent.id);
  }

  /**
   * Returns a hash code based on the event ID.
   *
   * @return the hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /**
   * Returns a unique key for this event based on workflow and step IDs.
   *
   * @return the unique element key
   */
  @Override
  public String getUniqueElementKey() {
    return workflowId + stepId;
  }
}
