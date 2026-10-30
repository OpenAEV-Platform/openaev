package io.openaev.processor.core;

import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.Tenant;
import io.openaev.service.DataPackService;
import io.openaev.service.account.CatalogConnectorPrivilegeService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * One-shot migration moving the connectors already deployed from the catalog to the tenant's
 * catalog connector service account.
 */
@Component
public class V20261030_Move_catalog_connectors_to_service_account extends RuntimeMigration {

  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorPrivilegeService catalogConnectorPrivilegeService;

  public V20261030_Move_catalog_connectors_to_service_account(
      DataPackService dataPackService,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorPrivilegeService catalogConnectorPrivilegeService) {
    super(dataPackService);
    this.connectorInstanceService = connectorInstanceService;
    this.catalogConnectorPrivilegeService = catalogConnectorPrivilegeService;
  }

  @Override
  protected boolean doMigrate(Tenant tenant) {
    List<ConnectorInstanceConfiguration> tokenConfigurations =
        connectorInstanceService.findOpenAevTokenConfigurations(tenant.getId());
    if (tokenConfigurations.isEmpty()) {
      return true; // nothing to migrate: the account is created at the first deployment
    }
    String token = catalogConnectorPrivilegeService.getOrCreateToken(tenant.getId());
    connectorInstanceService.rotateOpenAevToken(tokenConfigurations, token);
    return true;
  }
}
