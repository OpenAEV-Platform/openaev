package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Data;

/** Input to set (or clear) the recurrence scheduling of an atomic testing (mirrors scenario). */
@Data
public class InjectRecurrenceInput {

  @JsonProperty("inject_recurrence")
  private String recurrence;

  @JsonProperty("inject_recurrence_start")
  private Instant recurrenceStart;

  @JsonProperty("inject_recurrence_end")
  private Instant recurrenceEnd;
}
