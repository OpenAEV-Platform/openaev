package io.openaev.rest.injector_contract.output;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InjectorContractDomainCountOutput {
  private String domain;
  private Long count;

  public InjectorContractDomainCountOutput(String domain, Long count) {
    this.domain = domain;
    this.count = count;
  }
}
