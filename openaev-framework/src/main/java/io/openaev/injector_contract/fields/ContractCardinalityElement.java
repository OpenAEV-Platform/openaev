package io.openaev.injector_contract.fields;

import io.openaev.injector_contract.ContractCardinality;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class ContractCardinalityElement extends ContractElement {

  private final ContractCardinality cardinality;

  @Setter private List<String> defaultValue = new ArrayList<>();

  public ContractCardinalityElement(String key, String label, ContractCardinality cardinality) {
    super(key, label);
    this.cardinality = cardinality;
  }
}
