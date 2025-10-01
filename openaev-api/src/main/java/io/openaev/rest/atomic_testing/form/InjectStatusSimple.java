package io.openaev.rest.atomic_testing.form;

import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ExecutionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class InjectStatusSimple {

  @JsonProperty("status_id")
  @NotNull
  private String id;

  @Getter(NONE)
  @JsonProperty("status_name")
  private String name;

  public String getName() {
    return name != null ? name : ExecutionStatus.DRAFT.name();
  }

  @JsonProperty("tracking_sent_date")
  private Instant trackingSentDate;

  @JsonProperty("tracking_end_date")
  private Instant trackingEndDate;
}
