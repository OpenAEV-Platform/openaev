package io.openbas.rest.atomic_testing.form;

import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openbas.database.model.ExecutionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Data
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

  @Builder.Default
  @JsonProperty("status_main_traces")
  private List<ExecutionTraceOutput> traces = new ArrayList<>();

  @JsonProperty("tracking_sent_date")
  private Instant trackingSentDate;

  @JsonProperty("tracking_end_date")
  private Instant trackingEndDate;
}
