package io.openaev.engine.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EsAvgs {

  @JsonProperty("security_domain_average")
  @NotBlank
  private List<EsDomainsAvgData> domainAvg;

  public EsAvgs(List<EsDomainsAvgData> domainAvg) {
    this.domainAvg = domainAvg;
  }
}
