package io.openaev.utils.fixtures;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_TYPE;
import io.openaev.database.model.SecretReference;

/** Fixtures for {@link SecretReference} instances used by the secret handlers. */
public class CredentialSecretReferenceFixture {

  public static final String CREDENTIAL_REFERENCE_NAME = "AWS credential";
  public static final String CREDENTIAL_REFERENCE_CONNECTOR_INSTANCE_ID = "connector-instance-id";

  private CredentialSecretReferenceFixture() {}

  public static CredentialSecretReference getCredentialSecretReference(
      CREDENTIAL_TYPE credentialType, CREDENTIAL_AUTH_METHOD credentialAuthMethod) {
    CredentialSecretReference reference = new CredentialSecretReference();
    reference.setName(CREDENTIAL_REFERENCE_NAME);
    reference.setConnectorInstanceId(CREDENTIAL_REFERENCE_CONNECTOR_INSTANCE_ID);
    reference.setCredentialType(credentialType);
    reference.setCredentialAuthMethod(credentialAuthMethod);
    return reference;
  }

  public static CredentialSecretReference getAwsAccessKeyReference() {
    return getCredentialSecretReference(
        CREDENTIAL_TYPE.CLOUD_AWS, CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY);
  }

  public static CredentialSecretReference getAwsAssumeRoleReference() {
    return getCredentialSecretReference(
        CREDENTIAL_TYPE.CLOUD_AWS, CREDENTIAL_AUTH_METHOD.AWS_ASSUME_ROLE);
  }

  public static CredentialSecretReference getAzureServicePrincipalReference() {
    return getCredentialSecretReference(
        CREDENTIAL_TYPE.CLOUD_AZURE, CREDENTIAL_AUTH_METHOD.AZURE_SERVICE_PRINCIPAL);
  }

  public static CredentialSecretReference getAzureManagedIdentityReference() {
    return getCredentialSecretReference(
        CREDENTIAL_TYPE.CLOUD_AZURE, CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY);
  }

  public static CredentialSecretReference getUsernamePasswordReference() {
    return getCredentialSecretReference(
        CREDENTIAL_TYPE.IDENTITY, CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD);
  }

  public static CredentialSecretReference getHashReference() {
    return getCredentialSecretReference(CREDENTIAL_TYPE.IDENTITY, CREDENTIAL_AUTH_METHOD.HASH);
  }

  /** A reference that is not a {@link CredentialSecretReference}, used for negative cases. */
  public static SecretReference getNonCredentialReference() {
    SecretReference reference = new SecretReference();
    reference.setName(CREDENTIAL_REFERENCE_NAME);
    reference.setConnectorInstanceId(CREDENTIAL_REFERENCE_CONNECTOR_INSTANCE_ID);
    return reference;
  }
}
