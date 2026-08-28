package io.openaev.integration;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;

/**
 * Base class for built-in integration factories whose connectors must be registered for every
 * tenant. Subclasses contain only the DB-registration logic (the same calls that {@code
 * innerStart()} does on the {@link Integration} side), without creating in-memory executor objects.
 *
 * <p>{@link ManagerFactory} discovers all {@link BuiltinTenantRegistrable} beans and is called once
 * per new tenant after switching the tenant context.
 */
public abstract class BuiltinIntegrationFactory extends IntegrationFactory
    implements BuiltinTenantRegistrable {

  protected BuiltinIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
  }

  /**
   * Registers the built-in connector (injector / executor) for the given tenant. Must be idempotent
   * — safe to call even if the connector already exists (upsert semantics).
   */
  public abstract void registerConnectorForTenant(String tenantId) throws Exception;

  @Override
  public void registerForTenant(String tenantId) throws Exception {
    registerConnectorForTenant(tenantId);
  }
}
