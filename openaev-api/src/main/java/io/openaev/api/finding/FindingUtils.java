package io.openaev.api.finding;

import io.openaev.api.inject.service.ContractOutputContext;
import io.openaev.database.model.*;
import jakarta.validation.constraints.NotNull;

public final class FindingUtils {

  private FindingUtils() {}

  public static Finding createFinding(@NotNull final ContractOutputContext element) {
    Finding finding = new Finding();
    finding.setType(element.type());
    finding.setField(element.key());
    finding.setLabels(element.labels()); // TODO: Set tags
    return finding;
  }
}
