package io.openaev.rest.cve.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.config.AppConfig;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data
public class CVEBulkInsertInput {

  @JsonProperty("cves")
  @NotNull(message = AppConfig.MANDATORY_MESSAGE)
  private List<CveCreateInput> cves;

  @JsonProperty("last_modified_date_fetched")
  private Instant lastModifiedDateFetched;

  @JsonProperty("last_index")
  private Integer lastIndex;

  @JsonProperty("initial_dataset_completed")
  private Boolean initialDatasetCompleted;

  @JsonProperty("source_identifier")
  @NotNull(message = AppConfig.MANDATORY_MESSAGE)
  private String sourceIdentifier;
}
