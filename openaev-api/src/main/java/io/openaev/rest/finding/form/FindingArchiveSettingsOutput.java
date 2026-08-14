package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class FindingArchiveSettingsOutput {

  @JsonProperty("finding_archive_days")
  private Integer archiveDays;
}
