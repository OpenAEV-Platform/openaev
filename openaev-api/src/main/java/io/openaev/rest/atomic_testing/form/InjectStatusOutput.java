package io.openaev.rest.atomic_testing.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ExecutionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Data
@JsonInclude(NON_NULL)
@SuperBuilder
public class InjectStatusOutput {

  @JsonProperty("status_id")
  @NotNull
  private String id;

  @Getter(NONE)
  @JsonProperty("status_name")
  private String name;

  public String getName() {
    return name != null ? name : ExecutionStatus.DRAFT.name();
  }

  @JsonProperty("status_main_traces")
  private List<ExecutionTraceOutput> traces;

  @JsonProperty("tracking_sent_date")
  private Instant trackingSentDate;

  @JsonProperty("tracking_end_date")
  private Instant trackingEndDate;
}
