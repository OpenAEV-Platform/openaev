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
public class EsAvgs {

 /* private String label;
  private String color;*/

  /*@JsonProperty("security_domain_average")
  @NotBlank
  private List<EsDomainsAvgData> domainsAvg;*/

  @JsonProperty("inject_expectation_average")
  @NotBlank
  private List<EsExpectationsAvgData> expectationsAvg;

//  public EsAvgs(String label) {this.label = label;}

  public EsAvgs(/*String label, List<EsDomainsAvgData> domainsAvg,*/List<EsExpectationsAvgData> expectationsAvg) {
    /*this.label = label;
    this.domainsAvg = domainsAvg;*/
    this.expectationsAvg = expectationsAvg;
  }

}
