package io.openaev.api.credentials;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.credentials.form.*;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.credential.CredentialService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({CredentialApi.TENANT_CREDENTIALS_URI})
@Tag(name = "Credential API", description = "Operations related to credentials")
public class CredentialApi extends RestBehavior {
  public static final String TENANT_CREDENTIALS_URI = TENANT_PREFIX + "/credentials";

  private final CredentialService credentialService;
  private final CredentialMapper credentialMapper;
  private final TenantWriteScopeResolver writeScopeResolver;

  @GetMapping("/contracts")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.CREDENTIAL_ASSET)
  @Operation(summary = "Retrieve credential form contracts")
  public List<CredentialContractOutput> credentialContracts(TxCtx ctx) {
    return credentialService.credentialContracts();
  }

  @LogExecutionTime
  @PostMapping("/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.CREDENTIAL_ASSET)
  public Page<CredentialOutput> credentials(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    Page<CredentialSecretReference> credentialPage =
        credentialService.searchCredentials(ctx, searchPaginationInput);
    return credentialPage.map(credentialMapper::toOutput);
  }

  @GetMapping("/{credentialId}")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#credentialId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.CREDENTIAL_ASSET)
  @Operation(summary = "Retrieve a credential")
  public CredentialFullOutput getCredential(TxCtx ctx, @PathVariable String credentialId) {
    return credentialService.getCredentialFullOutputInformation(credentialId);
  }

  @PostMapping
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.CREDENTIAL_ASSET)
  @Operation(summary = "Create a credential")
  public CredentialOutput createCredential(TxCtx ctx, @Valid @RequestBody CredentialInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return credentialMapper.toOutput(credentialService.createCredential(input, tenantId));
  }

  @PutMapping("/{credentialId}")
  @Transactional
  @AccessControl(
      resourceId = "#credentialId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.CREDENTIAL_ASSET)
  @Operation(summary = "Update a credential with explicit secret update mode")
  public CredentialFullOutput updateCredential(
      TxCtx ctx, @PathVariable String credentialId, @Valid @RequestBody CredentialInput input) {
    return credentialService.updateCredential(credentialId, input);
  }

  @DeleteMapping("/{credentialId}")
  @Transactional
  @AccessControl(
      resourceId = "#credentialId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.CREDENTIAL_ASSET)
  @Operation(summary = "Delete a credential")
  public void deleteCredential(TxCtx ctx, @PathVariable String credentialId) {
    credentialService.deleteCredential(credentialId);
  }
}
