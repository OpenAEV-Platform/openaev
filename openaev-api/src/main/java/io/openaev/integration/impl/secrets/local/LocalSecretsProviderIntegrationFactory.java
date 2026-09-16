package io.openaev.integration.impl.secrets.local;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.secrets.provider.SecretsProviderType;
import io.openaev.secrets.provider.impl.handlers.SecretHandlerResolver;
import io.openaev.secrets.service.SecretReferenceService;
import io.openaev.secrets.service.SecretService;
import io.openaev.service.FileService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.io.InputStream;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LocalSecretsProviderIntegrationFactory extends BuiltinIntegrationFactory {
  private final ComponentRequestEngine componentRequestEngine;
  private final SecretService secretService;
  private final SecretReferenceService secretReferenceService;
  private final PreviewFeatureService previewFeatureService;
  private final FileService fileService;
  private final SecretHandlerResolver secretHandlerResolver;

  public LocalSecretsProviderIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ComponentRequestEngine componentRequestEngine,
      HttpClientFactory httpClientFactory,
      SecretService secretService,
      SecretReferenceService secretReferenceService,
      PreviewFeatureService previewFeatureService,
      FileService fileService,
      SecretHandlerResolver secretHandlerResolver) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.secretService = secretService;
    this.secretReferenceService = secretReferenceService;
    this.previewFeatureService = previewFeatureService;
    this.fileService = fileService;
    this.secretHandlerResolver = secretHandlerResolver;
  }

  @Override
  protected final String getClassName() {
    return LocalSecretsProviderIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations(String tenantId) throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances(String tenantId) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.CREDENTIAL_ASSET)) {
      return List.of();
    }
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
        secretService,
        secretReferenceService,
        secretHandlerResolver);
  }

  @Override
  public void registerConnectorForTenant(String tenantId) throws Exception {
    try (InputStream iconStream =
        getClass().getResourceAsStream("/img/icon-local-secret-provider.png")) {
      if (iconStream == null) {
        log.warn(
            "Local secrets provider icon not found in classpath: /img/icon-local-secret-provider.png");
        return;
      }
      fileService.uploadStream(
          FileService.SECRETS_PROVIDERS_IMAGES_ICONS_BASE_PATH,
          SecretsProviderType.LOCAL.type + FileService.EXT_PNG,
          iconStream);
    }

    // No-op: LocalSecretsProvider is an in-memory autostart integration with no DB entity
    // (unlike Executor/Injector). The autostart instance is created by findRelatedInstances().
  }
}
