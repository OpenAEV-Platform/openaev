package io.openaev.integration.impl.secrets.local;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.database.repository.SecretsRepository;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.NativeEncryptionService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class LocalSecretsProviderIntegrationFactory extends BuiltinIntegrationFactory {
  private final ComponentRequestEngine componentRequestEngine;
  private final NativeEncryptionService nativeEncryptionService;
  private final SecretsRepository secretsRepository;
  private final SecretReferenceRepository secretReferenceRepository;

  public LocalSecretsProviderIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ComponentRequestEngine componentRequestEngine,
      HttpClientFactory httpClientFactory,
      NativeEncryptionService nativeEncryptionService,
      SecretsRepository secretsRepository,
      SecretReferenceRepository secretReferenceRepository) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.nativeEncryptionService = nativeEncryptionService;
    this.secretsRepository = secretsRepository;
    this.secretReferenceRepository = secretReferenceRepository;
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
  public List<ConnectorInstance> findRelatedInstances(String tenantId) {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            LocalSecretsProviderIntegration.LOCAL_SECRETS_PROVIDER_ID,
            this.getClassName(),
            ConnectorType.SECRETS_PROVIDER));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new LocalSecretsProviderIntegration(
        instance,
        connectorInstanceService,
        componentRequestEngine,
        nativeEncryptionService,
        secretsRepository,
        secretReferenceRepository);
  }

  @Override
  public void registerConnectorForTenant(String tenantId) {
    // No-op: LocalSecretsProvider is an in-memory autostart integration with no DB entity
    // (unlike Executor/Injector). The autostart instance is created by findRelatedInstances().
  }
}
