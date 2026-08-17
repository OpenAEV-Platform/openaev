package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindingArchiveBulkInput {

  @JsonProperty("finding_ids")
  @NotEmpty
  private List<@NotBlank String> findingIds;

  // true = archive, false = un-archive (reactivate).
  @JsonProperty("archived")
  @NotNull
  private Boolean archived;
}
