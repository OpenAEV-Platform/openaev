package io.openaev.service.payload_approval;

import lombok.Getter;

/**
 * An edit would send an approved payload in use back to pending, and the caller asked to be warned
 * first (HTTP 409 with the usage). Saving again without asking for the check goes through.
 */
@Getter
public class PayloadApprovalImpactException extends RuntimeException {

  private final transient PayloadUsage usage;

  public PayloadApprovalImpactException(PayloadUsage usage) {
    super(
        "Saving will send this payload back to Pending approval and block the launch of "
            + usage.describe()
            + " until it is approved again.");
    this.usage = usage;
  }
}
