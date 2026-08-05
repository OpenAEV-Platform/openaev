package io.openaev.utils.fixtures;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;

public class CredentialFixture {

  private static final String DEFAULT_CONNECTOR_INSTANCE_ID = "local-secrets-provider";

  public static CredentialSecretReference createCredentialReference(
      CredentialSecretReference.CREDENTIAL_AUTH_METHOD authMethod,
      String name,
      Tenant tenant,
      Set<Tag> tags) {
    CredentialSecretReference credentialSecretReference = new CredentialSecretReference();
    credentialSecretReference.setCredentialType(CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY);
    credentialSecretReference.setCredentialAuthMethod(authMethod);
    credentialSecretReference.setName(name);
    credentialSecretReference.setDescription("description-" + name);
    credentialSecretReference.setTenant(tenant);
    credentialSecretReference.setTags(tags);
    credentialSecretReference.setConnectorInstanceId(DEFAULT_CONNECTOR_INSTANCE_ID);
    credentialSecretReference.setStatus(SecretReference.SECRET_STATUS.UNSET);
    return credentialSecretReference;
  }

  public static CredentialSecretReference createDefaultUsernameCredentialReference(Tenant tenant) {
    return createDefaultUsernameCredentialReference(
        RandomStringUtils.random(10, true, true), tenant);
  }

  public static CredentialSecretReference createDefaultUsernameCredentialReference(
      String name, Tenant tenant) {
    return createCredentialReference(
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
        name,
        tenant,
        new HashSet<>());
  }

  public static CredentialSecretReference createDefaultHashCredential(Tenant tenant) {
    return createCredentialReference(
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH,
        RandomStringUtils.randomAlphabetic(24),
        tenant,
        new HashSet<>());
  }
}
