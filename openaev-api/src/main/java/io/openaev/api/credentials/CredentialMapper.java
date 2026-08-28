package io.openaev.api.credentials;

import io.openaev.api.credentials.form.CredentialFullOutput;
import io.openaev.api.credentials.form.CredentialOutput;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.secrets.provider.SecretMetadata;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class CredentialMapper {
  public CredentialFullOutput toFullOutput(
      CredentialSecretReference credentialSecretReference, SecretMetadata secretMetadata) {
    return CredentialFullOutput.builder()
        .id(credentialSecretReference.getId())
        .name(credentialSecretReference.getName())
        .credentialType(credentialSecretReference.getCredentialType())
        .credentialAuthMethod(credentialSecretReference.getCredentialAuthMethod())
        .createdBy(toCreatedByOutput(credentialSecretReference.getCreatedBy()))
        .tags(toTagIds(credentialSecretReference))
        .lastVerifiedAt(credentialSecretReference.getLastVerifiedAt())
        .createdAt(credentialSecretReference.getCreatedAt())
        .status(credentialSecretReference.getStatus())
        .description(credentialSecretReference.getDescription())
        .username(secretMetadata.username())
        .hashAlgorithm(secretMetadata.hashAlgorithm())
        .awsDefaultRegion(secretMetadata.awsDefaultRegion())
        .awsAccessKeyId(secretMetadata.awsAccessKeyId())
        .awsRoleArn(secretMetadata.awsRoleArn())
        .awsSourceIdentityType(secretMetadata.awsSourceIdentityType())
        .awsSourceProfileAccessKeyId(secretMetadata.awsSourceProfileAccessKeyId())
        .azureEnvironment(secretMetadata.azureEnvironment())
        .azureClientId(secretMetadata.azureClientId())
        .azureTenantId(secretMetadata.azureTenantId())
        .azureSubscriptionId(secretMetadata.azureSubscriptionId())
        .build();
  }

  public CredentialOutput toOutput(CredentialSecretReference credential) {
    return CredentialOutput.builder()
        .id(credential.getId())
        .name(credential.getName())
        .credentialType(credential.getCredentialType())
        .credentialAuthMethod(credential.getCredentialAuthMethod())
        .status(credential.getStatus())
        .createdAt(credential.getCreatedAt())
        .createdBy(toCreatedByOutput(credential.getCreatedBy()))
        .lastVerifiedAt(credential.getLastVerifiedAt())
        .tags(toTagIds(credential))
        .build();
  }

  private CredentialOutput.CredentialCreatedByOutput toCreatedByOutput(User createdBy) {
    return createdBy == null
        ? null
        : new CredentialOutput.CredentialCreatedByOutput(
            createdBy.getId(), createdBy.getNameOrEmail());
  }

  private HashSet<String> toTagIds(CredentialSecretReference credential) {
    return new HashSet<>(credential.getTags().stream().map(Tag::getId).toList());
  }
}
