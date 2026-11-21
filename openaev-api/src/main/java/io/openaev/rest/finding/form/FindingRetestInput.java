package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/** The findings to re-test. */
@Data
public class FindingRetestInput {

  @Schema(description = "Ids of the findings to re-test")
  @JsonProperty("finding_ids")
  @NotEmpty
  private List<String> findingIds;
}
