package io.openaev.processor.core;

import static io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegration.LOCAL_SECRETS_PROVIDER_ID;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.Tenant;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.secrets.provider.impl.handlers.ApiTokenHandler;
import io.openaev.secrets.service.SecretReferenceService;
import io.openaev.secrets.service.SecretService;
import io.openaev.service.DataPackService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Moves the token stored on each AI target into an encrypted API token credential of the local
 * secrets provider, then links the AI target to it.
 */
@Slf4j
@Component
public class V20261116_Move_ai_target_tokens_to_credentials extends RuntimeMigration {

  private final ApiTokenHandler apiTokenHandler;
  private final SecretService secretService;
  private final SecretReferenceService secretReferenceService;

  @PersistenceContext private EntityManager entityManager;

  public V20261116_Move_ai_target_tokens_to_credentials(
      DataPackService dataPackService,
      ApiTokenHandler apiTokenHandler,
      SecretService secretService,
      SecretReferenceService secretReferenceService) {
    super(dataPackService);
    this.apiTokenHandler = apiTokenHandler;
    this.secretService = secretService;
    this.secretReferenceService = secretReferenceService;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected boolean doMigrate(Tenant tenant) {
    List<Object[]> aiTargets =
        entityManager
            .createNativeQuery(
                "SELECT asset_id, asset_name, ai_target_token FROM assets"
                    + " WHERE tenant_id = :tenantId AND ai_target_token IS NOT NULL"
                    + " AND ai_target_token <> ''")
            .setParameter("tenantId", tenant.getId())
            .getResultList();
    for (Object[] aiTarget : aiTargets) {
      SecretReference credential =
          storeCredential(tenant, aiTarget[1] + " token", (String) aiTarget[2]);
      entityManager
          .createNativeQuery(
              "UPDATE assets SET ai_target_credential_id = :credentialId, ai_target_token = NULL"
                  + " WHERE asset_id = :assetId")
          .setParameter("credentialId", credential.getId())
          .setParameter("assetId", aiTarget[0])
          .executeUpdate();
      log.info("AI target {} now uses credential {}", aiTarget[0], credential.getId());
    }
    return true;
  }

  private SecretReference storeCredential(Tenant tenant, String name, String token) {
    CredentialSecretReference credential = new CredentialSecretReference();
    credential.setName(name);
    credential.setCredentialType(CredentialSecretReference.CREDENTIAL_TYPE.API);
    credential.setCredentialAuthMethod(CredentialSecretReference.CREDENTIAL_AUTH_METHOD.API_TOKEN);
    credential.setConnectorInstanceId(LOCAL_SECRETS_PROVIDER_ID);
    credential.setStatus(SecretReference.SECRET_STATUS.UNSET);
    credential.setTenant(tenant);

    Secret secret =
        apiTokenHandler.buildOrUpdate(null, SecretStoreRequest.builder().apiToken(token).build());
    secret.setTenant(tenant);
    credential.setLocation(secretService.save(secret).getId());
    return secretReferenceService.save(credential);
  }
}
