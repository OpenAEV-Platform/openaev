package io.openaev.integration.impl.secrets.local;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.database.repository.SecretsRepository;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.secrets.provider.impl.LocalSecretsProvider;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.NativeEncryptionService;

public class LocalSecretsProviderIntegration extends Integration {
  public static final String LOCAL_SECRETS_PROVIDER_ID = "8c703d47-b6a7-472e-ace1-85ce7e216a89";
  public static final String LOCAL_SECRETS_PROVIDER_NAME = "Local Secrets Provider";

  @QualifiedComponent(identifier = "secrets-provider")
  private LocalSecretsProvider localSecretsProvider;

  private final NativeEncryptionService nativeEncryptionService;
  private final SecretsRepository secretsRepository;
  private final SecretReferenceRepository secretReferenceRepository;

  public LocalSecretsProviderIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ComponentRequestEngine componentRequestEngine,
      NativeEncryptionService nativeEncryptionService,
      SecretsRepository secretsRepository,
      SecretReferenceRepository secretReferenceRepository) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.nativeEncryptionService = nativeEncryptionService;
    this.secretsRepository = secretsRepository;
    this.secretReferenceRepository = secretReferenceRepository;
  }

  @Override
  protected void innerStart() throws Exception {
    this.localSecretsProvider =
        new LocalSecretsProvider(
            LOCAL_SECRETS_PROVIDER_ID,
            LOCAL_SECRETS_PROVIDER_NAME,
            nativeEncryptionService,
            secretsRepository,
            secretReferenceRepository);
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
