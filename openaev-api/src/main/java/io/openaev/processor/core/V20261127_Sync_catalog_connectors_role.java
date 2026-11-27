package io.openaev.processor.core;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.service.DataPackService;
import io.openaev.service.account.CatalogConnectorPrivilegeService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import org.springframework.stereotype.Component;

/**
 * Gives the catalog connector role of the tenants that already deployed connectors from the catalog
 * the capabilities added since: the role is otherwise synced only at the next deployment.
 */
@Component
public class V20261127_Sync_catalog_connectors_role extends RuntimeMigration {

  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorPrivilegeService catalogConnectorPrivilegeService;

  public V20261127_Sync_catalog_connectors_role(
      DataPackService dataPackService,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorPrivilegeService catalogConnectorPrivilegeService) {
    super(dataPackService);
    this.connectorInstanceService = connectorInstanceService;
    this.catalogConnectorPrivilegeService = catalogConnectorPrivilegeService;
  }

  @Override
  protected boolean doMigrate(Tenant tenant) {
    if (connectorInstanceService.findOpenAevTokenConfigurations(tenant.getId()).isEmpty()) {
      return true; // nothing deployed from the catalog: the role is created at the first deployment
    }
    catalogConnectorPrivilegeService.syncRole(TxCtx.forTenant(tenant.getId()), tenant.getId());
    return true;
  }
}
