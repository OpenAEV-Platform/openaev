package io.openaev.service.catalog.action;

import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.catalog.CatalogPayload;
import java.util.List;
import org.springframework.data.domain.Pageable;

/**
 * A page of what the catalogue offers a tenant: payloads to install, then the actions of
 * integrations to deploy once the payloads run out.
 */
public record AvailableActions(
    List<CatalogPayload> payloads,
    List<CatalogConnectorInjectorContract> contracts,
    Pageable pageable,
    long total) {}
