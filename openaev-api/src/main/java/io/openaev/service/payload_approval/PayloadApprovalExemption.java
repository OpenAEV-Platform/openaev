package io.openaev.service.payload_approval;

import io.openaev.database.model.Payload;

/**
 * Lets a feature exempt its own system payloads from the approval check of {@link
 * PayloadApprovalGate}: an exempt payload runs whatever its approval status. Implementations are
 * Spring beans; the gate consults all of them.
 *
 * <p>Meant for payloads that carry their own, stricter approval flow (for example the payloads of
 * an approved IOC validation), never for user content.
 */
public interface PayloadApprovalExemption {

  /** Whether this payload is exempt from the payload approval check. */
  boolean isExempt(Payload payload);
}
