package io.openaev.database.model;

/**
 * What makes two detections the same finding: the unique key {@code findings_identity_unique} of
 * {@code findings}, the tenant aside.
 */
public record FindingKey(ContractOutputType type, String valueHash, String locationId) {

  // getId() on the lazy location reads the foreign key without loading the location
  public static FindingKey of(Finding finding) {
    return new FindingKey(finding.getType(), finding.getValueHash(), finding.getLocation().getId());
  }
}
