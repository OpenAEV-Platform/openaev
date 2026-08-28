package io.openaev.integration.impl.secrets.local;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.impl.LocalSecretsProvider;
import io.openaev.secrets.provider.impl.handlers.SecretHandlerResolver;
import io.openaev.secrets.service.SecretReferenceService;
import io.openaev.secrets.service.SecretService;
import io.openaev.service.connector_instances.ConnectorInstanceService;

public class LocalSecretsProviderIntegration extends IntegrationInMemory {
  public static final String LOCAL_SECRETS_PROVIDER_ID = "8c703d47-b6a7-472e-ace1-85ce7e216a89";
  public static final String LOCAL_SECRETS_PROVIDER_NAME = "Local Secrets Provider";

  @QualifiedComponent(identifier = SecretsProvider.SERVICE_NAME)
  private LocalSecretsProvider localSecretsProvider;

  private final SecretService secretService;
  private final SecretReferenceService secretReferenceService;
  private final SecretHandlerResolver secretHandlerResolver;

  public LocalSecretsProviderIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ComponentRequestEngine componentRequestEngine,
      SecretService secretService,
      SecretReferenceService secretReferenceService,
      SecretHandlerResolver secretHandlerResolver) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.secretService = secretService;
    this.secretReferenceService = secretReferenceService;
    this.secretHandlerResolver = secretHandlerResolver;
  }

  @Override
  protected void innerStart() throws Exception {
    this.localSecretsProvider =
        new LocalSecretsProvider(
            LOCAL_SECRETS_PROVIDER_ID,
            LOCAL_SECRETS_PROVIDER_NAME,
            secretService,
            secretReferenceService,
            secretHandlerResolver);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
