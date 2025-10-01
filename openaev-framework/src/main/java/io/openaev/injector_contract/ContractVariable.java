package io.openaev.injector_contract;

import io.openaev.database.model.Variable.VariableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;

@Getter
public class ContractVariable {

  @NotBlank private final String key;

  @NotBlank private final String label;

  @NotNull private final VariableType type;

  @NotNull private final ContractCardinality cardinality;

  private final List<ContractVariable> children;

  private ContractVariable(
      @NotBlank final String key,
      @NotBlank final String label,
      @NotNull final VariableType type,
      @NotNull final ContractCardinality cardinality,
      final List<ContractVariable> children) {
    this.key = key;
    this.label = label;
    this.type = type;
    this.cardinality = cardinality;
    this.children = children;
  }

  public static ContractVariable variable(
      @NotBlank final String key,
      @NotBlank final String label,
      @NotNull final VariableType type,
      @NotNull final ContractCardinality cardinality) {
    return new ContractVariable(key, label, type, cardinality, List.of());
  }

  public static ContractVariable variable(
      @NotBlank final String key,
      @NotBlank final String label,
      @NotNull final VariableType type,
      @NotNull final ContractCardinality cardinality,
      final List<ContractVariable> children) {
    return new ContractVariable(key, label, type, cardinality, children);
  }
}
