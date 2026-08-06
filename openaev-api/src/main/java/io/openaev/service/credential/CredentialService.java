package io.openaev.service.credential;

import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.api.credentials.CredentialMapper;
import io.openaev.api.credentials.form.*;
import io.openaev.context.TxCtx;
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
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SearchPaginationInputMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
   * @return matching credential
   */
  public CredentialSecretReference getCredentialById(@NotBlank final String credentialId) {
    return credentialSecretReferenceRepository
        .findById(credentialId)
        .orElseThrow(() -> new ElementNotFoundException("Credential not found"));
  }

  /**
   * Retrieve full credential output enriched with non-sensitive secret metadata.
   *
   * @param credentialId credential identifier
   * @return full credential output
   */
  public CredentialFullOutput getCredentialFullOutputInformation(
      @NotBlank final String credentialId) {
    CredentialSecretReference credential = getCredentialById(credentialId);
    SecretsProvider secretProvider =
        resolveProviderByConnectorInstanceId(
            credential.getConnectorInstanceId(), credential.getTenant().getId());
    SecretMetadata secretMetadata = secretProvider.getSecretMetadata(credential);
    return credentialMapper.toFullOutput(credential, secretMetadata);
  }

  /**
   * Searches tenant credentials using pageable query input.
   *
   * @param ctx transaction context carrying tenant scope
   * @param searchPaginationInput pagination and filters
   * @return page of matching credentials
   */
  public Page<CredentialSecretReference> searchCredentials(
      @NotNull final TxCtx ctx, @NotNull final SearchPaginationInput searchPaginationInput) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    SearchPaginationInput normalizedInput =
        SearchPaginationInputMapper.translateFields(
            searchPaginationInput, CREDENTIAL_QUERY_FIELD_MAPPING);
    return buildPaginationJPA(
        (specification, pageable) -> findAllByTenantIds(tenantIds, specification, pageable),
        normalizedInput,
        CredentialSecretReference.class);
  }

  private Page<CredentialSecretReference> findAllByTenantIds(
      Set<String> tenantIds,
      Specification<CredentialSecretReference> specification,
      Pageable pageable) {
    if (tenantIds.isEmpty()) {
      return Page.empty(pageable);
    }
    Specification<CredentialSecretReference> tenantSpecification =
        (root, query, criteriaBuilder) -> root.get("tenant").get("id").in(tenantIds);
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
    LocalSecretsProvider provider = getLocalProvider(tenantId);

    // Build Credential Reference
    CredentialSecretReference credential = new CredentialSecretReference();
    applyCreateInputToCredential(credential, input, provider.getId(), tenantId);

    // Store credential reference with it's secret
    return (CredentialSecretReference)
        provider.store(credential, convertCredentialInputToSecretStoreRequest(input));
  }

  /**
   * Updates credential metadata and optionally replaces secret payload according to mode.
   *
   * @param credentialId credential identifier
   * @param input credential update payload
   * @return updated credential full output
   */
  public CredentialFullOutput updateCredential(String credentialId, CredentialInput input) {

    CredentialSecretReference credential = getCredentialById(credentialId);
    SecretsProvider secretProvider =
        resolveProviderByConnectorInstanceId(
            credential.getConnectorInstanceId(), credential.getTenant().getId());

    applyMetadataInputToCredential(credential, input);
    secretProvider.update(credential, convertCredentialInputToSecretStoreRequest(input));

    SecretMetadata secretMetadata = secretProvider.getSecretMetadata(credential);

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
    credential.setStatus(SecretReference.SECRET_STATUS.UNSET);
    credential.setCreatedBy(userService.currentUserOrNull());
  }

  private void applyMetadataInputToCredential(
      CredentialSecretReference credential, CredentialInput input) {
    credential.setName(Objects.requireNonNull(input.credentialName(), "name must not be null"));
    credential.setDescription(input.credentialDescription());
    List<String> tagIds = Objects.requireNonNullElse(input.credentialTagIds(), List.of());
    credential.setTags(
        tagIds.isEmpty() ? new HashSet<>() : iterableToSet(tagRepository.findAllById(tagIds)));
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
   */
  public void deleteCredential(String credentialId) {
    CredentialSecretReference credential = getCredentialById(credentialId);
    LocalSecretsProvider provider =
        resolveProviderByConnectorInstanceId(
            credential.getConnectorInstanceId(), credential.getTenant().getId());
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
              .filter(
                  provider -> Objects.equals(provider.getType(), SecretsProviderType.LOCAL.type))
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

  private LocalSecretsProvider resolveProviderByConnectorInstanceId(
      String connectorInstanceId, String tenantId) {
    try {
      ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
      instance.setId(connectorInstanceId);
      SecretsProvider provider =
          managerFactory.getManager(tenantId).requestForInstance(instance, SecretsProvider.class);
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
