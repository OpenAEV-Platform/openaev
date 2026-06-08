package io.openaev.integration.impl.secrets.local;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class LocalSecretsProviderIntegrationFactory extends BuiltinIntegrationFactory {
  private final ComponentRequestEngine componentRequestEngine;

  public LocalSecretsProviderIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ComponentRequestEngine componentRequestEngine,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
  }

  @Override
  protected final String getClassName() {
    return LocalSecretsProviderIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            LocalSecretsProviderIntegration.LOCAL_SECRETS_PROVIDER_ID,
            this.getClassName(),
            ConnectorType.SECRETS_PROVIDER));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new LocalSecretsProviderIntegration(
        instance, connectorInstanceService, componentRequestEngine);
  }

  @Override
  public void registerConnectorForTenant() throws Exception {
    // noop
  }
}
