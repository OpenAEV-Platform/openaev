package io.openaev.rest.injector_contract.output;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class InjectorContractSearchResult {
  private List<InjectorContractFullOutput> contracts;
  private Map<String, Long> injectorContractDomainCounts;

  public InjectorContractSearchResult(
      List<InjectorContractFullOutput> contracts, Map<String, Long> domainCounts) {
    this.contracts = contracts;
    this.injectorContractDomainCounts = domainCounts;
  }
}
