package io.openaev.database.model.attackpath.projection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row per simulation for the front's simulation picker (issue 6647): its id, its distinct
 * endpoint count and its execution count, so an option reads "id — N endpoints". Produced by a
 * {@code GROUP BY simulation_id}, tenant-filtered by the inspector.
 *
 * <p>{@code tenantId} is the tenant the row was read in, so the caller's right on the simulation is
 * decided there rather than in the ambient tenant: a request scope can name several tenants and the
 * rows then come from all of them. It is a read-side key, never an API field, so it stays out of
 * the response and out of the OpenAPI schema.
 */
public record AttackPathSimSummaryRow(
    String simulationId,
    long endpointCount,
    long executionCount,
    @JsonIgnore @Schema(hidden = true) String tenantId) {}
