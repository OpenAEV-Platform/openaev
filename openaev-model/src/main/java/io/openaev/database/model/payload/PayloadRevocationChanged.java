package io.openaev.database.model.payload;

/** A payload version revoked or lifted in a tenant, by a user or by the catalogue. */
public record PayloadRevocationChanged(
    String tenantId, String payloadId, boolean revoked, String reason, boolean byCatalogue) {}
