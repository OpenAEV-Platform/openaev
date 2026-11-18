package io.openaev.service.catalog.event;

import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;

/** A version of catalogue content installed or uninstalled in a tenant, by a user or by itself. */
public record CatalogContentChanged(
    String tenantId,
    ContentType type,
    String repository,
    String externalId,
    String version,
    CatalogHistory.Action action,
    CatalogHistory.Mode mode,
    String userId) {}
