package io.openaev.rest.inject.service;

import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Tag;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;

public record ContractOutputContext(
    String key, // maps to contractOutputElement.getKey() / contentOutputElement.getField()
    String name, // display name / label
    ContractOutputType type,
    boolean isMultiple,
    String[] tagIds,
    String[] labels) {

  public static ContractOutputContext from(ContractOutputElement element) {
    return new ContractOutputContext(
        element.getKey(),
        element.getName(),
        element.getType(),
        true,
        element.getTags().isEmpty()
            ? new String[0]
            : element.getTags().stream().map(Tag::getId).toArray(String[]::new),
        new String[0]);
  }

  public static ContractOutputContext from(InjectorContractContentOutputElement element) {
    return new ContractOutputContext(
        element.getField(),
        element.getField(), // or derive name differently
        element.getType(),
        element.isMultiple(),
        new String[0], // tags not available here yet
        element.getLabels());
  }
}
