package io.openaev.rest.finding;

import io.openaev.database.model.Finding;
import io.openaev.rest.inject.service.ContractOutputContext;
import org.jetbrains.annotations.NotNull;

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
