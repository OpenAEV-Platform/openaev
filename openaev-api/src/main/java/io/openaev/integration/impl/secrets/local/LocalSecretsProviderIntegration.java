package io.openaev.integration.impl.secrets.local;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.secrets.provider.impl.local.LocalSecretsProvider;
import io.openaev.service.connector_instances.ConnectorInstanceService;

public class LocalSecretsProviderIntegration extends Integration {
  public static final String LOCAL_SECRETS_PROVIDER_ID = "8c703d47-b6a7-472e-ace1-85ce7e216a89";

  @QualifiedComponent(identifier = "secrets-provider")
  // @QualifiedComponent(identifier = "local_secrets_provider")
  private LocalSecretsProvider localSecretsProvider;

  public LocalSecretsProviderIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ComponentRequestEngine componentRequestEngine) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
  }

  @Override
  protected void innerStart() throws Exception {

    this.localSecretsProvider =
        new LocalSecretsProvider(LOCAL_SECRETS_PROVIDER_ID, "Local Secrets Provider");
  }

  @Override
  protected void refresh() throws Exception {
    // Nothing to refresh from DB
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
