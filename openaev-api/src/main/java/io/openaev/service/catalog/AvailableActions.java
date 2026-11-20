package io.openaev.service.catalog;

import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.catalog.CatalogPayload;
import java.util.List;

/** What the catalogue offers a tenant: payloads to install, actions of integrations to deploy. */
public record AvailableActions(
    List<CatalogPayload> payloads, List<CatalogConnectorInjectorContract> contracts) {}
