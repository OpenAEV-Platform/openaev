package io.openaev.database.model.payload;

/** A payload version revoked or lifted in a tenant, announced once its transaction commits. */
public record PayloadRevocationChanged(
    String tenantId, String payloadId, boolean revoked, String reason, boolean byCatalogue) {}
