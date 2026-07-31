package io.openaev.service.credential;

import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.api.credentials.CredentialMapper;
import io.openaev.api.credentials.form.*;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.CredentialSecretReferenceRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.SecretsProviderType;
import io.openaev.secrets.provider.impl.LocalSecretsProvider;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SearchPaginationInputMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CredentialService {

  private final CredentialMapper credentialMapper;
  private final TagRepository tagRepository;

  private static final Map<String, String> CREDENTIAL_QUERY_FIELD_MAPPING =
      Map.ofEntries(
          Map.entry("credential_name", "secret_reference_name"),
          Map.entry("credential_type", "secret_reference_credential_type"),
          Map.entry("credential_auth_method", "secret_reference_credential_auth_method"),
          Map.entry("credential_status", "secret_reference_status"),
          Map.entry("credential_created_by", "secret_reference_created_by"),
          Map.entry("credential_created_at", "secret_reference_created_at"),
          Map.entry("credential_updated_at", "secret_reference_updated_at"),
          Map.entry("credential_last_verified_at", "secret_reference_last_verified_at"),
          Map.entry("credential_tags_ids", "secret_reference_tags"),
          Map.entry("credential_connector_instance_id", "secret_reference_connector_instance_id"));

  private final CredentialSecretReferenceRepository credentialSecretReferenceRepository;
  private final ManagerFactory managerFactory;
  private final UserService userService;

  /**
   * Returns supported credential form contracts.
   *
   * @return supported credential contracts
   */
  public List<CredentialContractOutput> credentialContracts() {
    return List.of(
        new CredentialContractOutput(
            CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
            CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
            List.of(
                new CredentialContractOutput.CredentialContractField(
                    "credential_username",
                    CredentialContractOutput.CredentialContractFieldType.text,
                    true,
                    null),
                new CredentialContractOutput.CredentialContractField(
                    "credential_password",
                    CredentialContractOutput.CredentialContractFieldType.password,
                    true,
                    null))),
        new CredentialContractOutput(
            CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
            CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH,
            List.of(
                new CredentialContractOutput.CredentialContractField(
                    "credential_hash",
                    CredentialContractOutput.CredentialContractFieldType.password,
                    true,
                    null),
                new CredentialContractOutput.CredentialContractField(
                    "credential_hash_algorithm",
                    CredentialContractOutput.CredentialContractFieldType.select,
                    true,
                    List.of(
                        HashSecret.HASH_ALGORITHM.SHA.name(),
                        HashSecret.HASH_ALGORITHM.NTLM.name())))));
  }

  /**
   * Retrieve credential by id within the given tenant.
   *
   * @param credentialId credential identifier
   * @param tenantId tenant identifier
   * @return matching credential
   */
  public CredentialSecretReference getCredentialById(
      @NotBlank final String credentialId, @NotNull final String tenantId) {
    return credentialSecretReferenceRepository
        .findByIdAndTenantId(credentialId, tenantId)
        .orElseThrow(() -> new ElementNotFoundException("Credential not found"));
  }

  /**
   * Retrieve full credential output enriched with non-sensitive secret metadata.
   *
   * @param credentialId credential identifier
   * @param tenantId tenant identifier
   * @return full credential output
   */
  public CredentialFullOutput getCredentialFullOutputInformation(
      @NotBlank final String credentialId, @NotNull final String tenantId) {
    CredentialSecretReference credential = getCredentialById(credentialId, tenantId);
    SecretsProvider secretProvider =
        resolveProviderByConnectorInstanceId(credential.getConnectorInstanceId());
    SecretMetadata secretMetadata = secretProvider.getSecretMetada(credential);
    return credentialMapper.toFullOutput(credential, secretMetadata);
  }

  //  private record CredentialAndSecretMetadata(
  //          CredentialSecretReference credential,
  //          SecretMetadata secretMetadata) {}
  //
  //  private CredentialAndSecretMetadata getCredentialSecretMetadata(@NotBlank final String
  // credentialId, @NotNull final String tenantId) {
  //
  //    return new CredentialAndSecretMetadata(credential, secretMetadata);
  //  }
  //
  /**
   * Searches tenant credentials using pageable query input.
   *
   * @param searchPaginationInput pagination and filters
   * @param tenantId tenant identifier
   * @return page of matching credentials
   */
  public Page<CredentialSecretReference> searchCredentials(
      @NotNull final SearchPaginationInput searchPaginationInput, @NotNull final String tenantId) {
    SearchPaginationInput normalizedInput =
        SearchPaginationInputMapper.translateFields(
            searchPaginationInput, CREDENTIAL_QUERY_FIELD_MAPPING);
    return buildPaginationJPA(
        (specification, pageable) -> findAllByTenant(tenantId, specification, pageable),
        normalizedInput,
        CredentialSecretReference.class);
  }

  private Page<CredentialSecretReference> findAllByTenant(
      String tenantId, Specification<CredentialSecretReference> specification, Pageable pageable) {
    Specification<CredentialSecretReference> tenantSpecification =
        (root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("tenant").get("id"), tenantId);
    return credentialSecretReferenceRepository.findAll(
        tenantSpecification.and(specification), pageable);
  }

  /**
   * Creates a credential and stores its secret payload through the provider.
   *
   * @param input credential input payload
   * @param tenantId tenant identifier
   * @return created credential reference
   */
  public CredentialSecretReference createCredential(CredentialInput input, String tenantId) {
    // Validate credential input schema
    validateCredentialInputForCreation(input);
    LocalSecretsProvider provider = getLocalProvider(tenantId);

    // Build Credential Reference
    CredentialSecretReference credential = new CredentialSecretReference();
    applyCreateInputToCredential(credential, input, provider.getId(), tenantId);

    // Store credential reference with it's secret
    return (CredentialSecretReference)
        provider.store(credential, convertCredentialInputToSecretStoreRequest(input));
  }

  private void validateCredentialInputForUsernamePassword(CredentialInput input) {
    if (!hasText(input.credentialUsername())) {
      throw new IllegalArgumentException(
          "credentialUsername is required for USERNAME_PASSWORD auth method");
    }
    if (!hasText(input.credentialPassword())) {
      throw new IllegalArgumentException(
          "credentialPassword is required for USERNAME_PASSWORD auth method");
    }
  }

  private void validateCredentialInputForHash(CredentialInput input) {
    if (!hasText(input.credentialHash())) {
      throw new IllegalArgumentException("credentialHash is required for HASH auth method");
    }
    if (input.credentialHashAlgorithm() == null) {
      throw new IllegalArgumentException(
          "credentialHashAlgorithm is required for HASH auth method");
    }
  }

  private void validateCredentialInputForCreation(CredentialInput input) {
    if (!CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY.equals(input.credentialType())) {
      throw new IllegalArgumentException("Unsupported credential type: " + input.credentialType());
    }

    switch (input.credentialAuthMethod()) {
      case USERNAME_PASSWORD -> validateCredentialInputForUsernamePassword(input);
      case HASH -> validateCredentialInputForHash(input);
      default ->
          throw new IllegalArgumentException(
              "Unsupported credential auth method: " + input.credentialAuthMethod());
    }
  }

  /**
   * Updates credential metadata and optionally replaces secret payload according to mode.
   *
   * @param credentialId credential identifier
   * @param input credential update payload
   * @param tenantId tenant identifier
   * @return updated credential full output
   */
  public CredentialFullOutput updateCredential(
      String credentialId, CredentialInput input, String tenantId) {

    CredentialSecretReference credential = getCredentialById(credentialId, tenantId);
    SecretsProvider secretProvider =
        resolveProviderByConnectorInstanceId(credential.getConnectorInstanceId());

    applyMetadataInputToCredential(credential, input);
    secretProvider.update(credential, convertCredentialInputToSecretStoreRequest(input));

    SecretMetadata secretMetadata = secretProvider.getSecretMetada(credential);

    return credentialMapper.toFullOutput(credential, secretMetadata);
  }

  private void applyCreateInputToCredential(
      CredentialSecretReference credential,
      CredentialInput input,
      String providerId,
      String tenantId) {
    applyMetadataInputToCredential(credential, input);
    credential.setConnectorInstanceId(providerId);
    credential.setTenant(new Tenant(tenantId));
    credential.setCreatedBy(userService.currentUserOrNull());
  }

  private void applyMetadataInputToCredential(
      CredentialSecretReference credential, CredentialInput input) {
    credential.setName(Objects.requireNonNull(input.credentialName(), "name must not be null"));
    credential.setDescription(input.credentialDescription());
    credential.setTags(iterableToSet(tagRepository.findAllById(input.credentialTagIds())));
    credential.setCredentialAuthMethod(input.credentialAuthMethod());
    credential.setCredentialType(input.credentialType());
  }

  private SecretStoreRequest convertCredentialInputToSecretStoreRequest(CredentialInput input) {
    return new SecretStoreRequest(
        input.credentialUsername(),
        input.credentialPassword(),
        input.credentialHash(),
        input.credentialHashAlgorithm());
  }

  /**
   * Deletes a credential and its stored secret.
   *
   * @param credentialId credential identifier
   * @param tenantId tenant identifier
   */
  public void deleteCredential(String credentialId, String tenantId) {
    CredentialSecretReference credential = getCredentialById(credentialId, tenantId);
    LocalSecretsProvider provider =
        resolveProviderByConnectorInstanceId(credential.getConnectorInstanceId());
    provider.delete(credential);
  }

  private LocalSecretsProvider getLocalProvider(String tenantId) {
    try {
      return (LocalSecretsProvider)
          managerFactory
              .getManager(tenantId)
              .requestManyAllStates(
                  new ComponentRequest(SecretsProvider.SERVICE_NAME), SecretsProvider.class)
              .stream()
              .filter(provider -> provider.getProviderType() == SecretsProviderType.LOCAL)
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "No secrets provider found for type " + SecretsProviderType.LOCAL));
    } catch (Exception e) {
      throw new IllegalStateException(
          "No secrets provider is available for type "
              + SecretsProviderType.LOCAL
              + " in current tenant",
          e);
    }
  }

  private LocalSecretsProvider resolveProviderByConnectorInstanceId(String connectorInstanceId) {
    try {
      ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
      instance.setId(connectorInstanceId);
      SecretsProvider provider =
          managerFactory
              .getManager(TenantContext.getCurrentTenant())
              .requestForInstance(instance, SecretsProvider.class);
      if (provider instanceof LocalSecretsProvider localSecretsProvider) {
        return localSecretsProvider;
      }
      throw new IllegalStateException(
          "Expected LocalSecretsProvider for connector instance "
              + connectorInstanceId
              + ", got: "
              + provider.getClass().getSimpleName());
    } catch (Exception e) {
      throw new IllegalStateException(
          "No local secrets provider is available for connector instance " + connectorInstanceId,
          e);
    }
  }
}
