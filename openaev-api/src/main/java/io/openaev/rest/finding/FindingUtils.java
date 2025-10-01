package io.openaev.rest.finding;

import io.openaev.database.model.*;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import org.jetbrains.annotations.NotNull;

public class FindingUtils {

  public static Finding createFinding(@NotNull final InjectorContractContentOutputElement element) {
    Finding finding = new Finding();
    finding.setType(element.getType());
    finding.setField(element.getField());
    finding.setLabels(element.getLabels());
    return finding;
  }
}
