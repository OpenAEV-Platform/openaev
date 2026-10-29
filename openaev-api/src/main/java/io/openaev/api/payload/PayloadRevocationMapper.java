package io.openaev.api.payload;

import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.Payload;
import io.openaev.database.model.User;

public class PayloadRevocationMapper {

  private PayloadRevocationMapper() {}

  public static PayloadRevocationOutput toOutput(ContentRevocation revocation) {
    return toOutput(revocation, null);
  }

  /** The revocation as seen from this payload: a customized one may inherit its base's. */
  public static PayloadRevocationOutput toOutput(ContentRevocation revocation, String payloadId) {
    User revokedBy = revocation.getRevokedBy();
    Payload revoked = revocation.getPayload();
    boolean inherited = payloadId != null && revoked != null && !payloadId.equals(revoked.getId());
    return new PayloadRevocationOutput(
        revocation.getId(),
        revocation.getReason(),
        revocation.getCreatedAt(),
        revokedBy != null ? revokedBy.getName() : null,
        revocation.getTenant() != null
            ? PayloadRevocationOutput.SOURCE_TENANT
            : PayloadRevocationOutput.SOURCE_REPOSITORY,
        inherited ? revoked.getVersion() : null);
  }
}
