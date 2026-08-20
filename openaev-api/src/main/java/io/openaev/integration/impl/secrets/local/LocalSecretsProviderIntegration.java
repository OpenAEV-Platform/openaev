package io.openaev.integration.impl.secrets.local;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.impl.LocalSecretsProvider;
import io.openaev.secrets.provider.impl.handlers.SecretHandler;
import io.openaev.secrets.service.SecretReferenceService;
import io.openaev.secrets.service.SecretService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class LocalSecretsProviderIntegration extends IntegrationInMemory {
  public static final String LOCAL_SECRETS_PROVIDER_ID = "8c703d47-b6a7-472e-ace1-85ce7e216a89";
  public static final String LOCAL_SECRETS_PROVIDER_NAME = "Local Secrets Provider";

  @QualifiedComponent(identifier = SecretsProvider.SERVICE_NAME)
  private LocalSecretsProvider localSecretsProvider;

  private final SecretService secretService;
  private final SecretReferenceService secretReferenceService;
  private final List<SecretHandler> secretHandlers;

  public LocalSecretsProviderIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ComponentRequestEngine componentRequestEngine,
      SecretService secretService,
      SecretReferenceService secretReferenceService,
      List<SecretHandler> secretHandlers) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.secretService = secretService;
    this.secretReferenceService = secretReferenceService;
    this.secretHandlers = secretHandlers;
  }

  @Override
  protected void innerStart() throws Exception {
    this.localSecretsProvider =
        new LocalSecretsProvider(
            LOCAL_SECRETS_PROVIDER_ID,
            LOCAL_SECRETS_PROVIDER_NAME,
            secretService,
            secretReferenceService,
            secretHandlers);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
