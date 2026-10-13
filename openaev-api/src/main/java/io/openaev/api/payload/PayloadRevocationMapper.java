package io.openaev.api.payload;

import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.User;

public class PayloadRevocationMapper {

  private PayloadRevocationMapper() {}

  public static PayloadRevocationOutput toOutput(ContentRevocation revocation) {
    User revokedBy = revocation.getRevokedBy();
    return new PayloadRevocationOutput(
        revocation.getId(),
        revocation.getReason(),
        revocation.getCreatedAt(),
        revokedBy != null ? revokedBy.getName() : null,
        revocation.getTenant() != null
            ? PayloadRevocationOutput.SOURCE_TENANT
            : PayloadRevocationOutput.SOURCE_REPOSITORY);
  }
}
