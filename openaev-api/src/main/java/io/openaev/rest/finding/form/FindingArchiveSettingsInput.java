package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindingArchiveSettingsInput {

  @JsonProperty("finding_archive_days")
  @NotNull
  @Min(1)
  private Integer archiveDays;
}
