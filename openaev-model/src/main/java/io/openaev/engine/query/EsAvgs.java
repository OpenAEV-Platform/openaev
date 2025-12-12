package io.openaev.engine.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EsAvgs {

  @JsonProperty("security_domain_average")
  @NotBlank
  private List<EsDomainsAvgData> domainsAvg;

  @JsonProperty("inject_expectation_average")
  @NotBlank
  private List<EsExpectationsAvgData> expectationsAvg;



}
