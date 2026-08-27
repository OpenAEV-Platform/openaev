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
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.credential.CredentialService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({CredentialApi.TENANT_CREDENTIALS_URI})
@Tag(name = "Credential API", description = "Operations related to credentials")
public class CredentialApi extends RestBehavior {
  public static final String TENANT_CREDENTIALS_URI = TENANT_PREFIX + "/credentials";

  /** Name of the multipart part carrying the GCP service account key file. */
  public static final String GCP_PRIVATE_KEY_PART = "gcp_private_key_json";

  /**
   * Upper bound on the key file. A Google service account key weighs about 2.3 KB; 16 KB leaves
   * room for formatting variations while keeping the endpoint useless as an upload DoS vector.
   */
  public static final long MAX_GCP_PRIVATE_KEY_SIZE_BYTES = 16L * 1024;

  private final CredentialService credentialService;
  private final CredentialMapper credentialMapper;
  private final TenantWriteScopeResolver writeScopeResolver;

  @GetMapping("/contracts")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.CREDENTIAL)
  @Operation(summary = "Retrieve credential form contracts")
  public List<CredentialContractOutput> credentialContracts(TxCtx ctx) {
    return credentialService.credentialContracts();
  }

  @LogExecutionTime
  @PostMapping("/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.CREDENTIAL)
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
      resourceType = ResourceType.CREDENTIAL)
  @Operation(summary = "Retrieve a credential")
  public CredentialFullOutput getCredential(TxCtx ctx, @PathVariable String credentialId) {
    return credentialService.getCredentialFullOutputInformation(credentialId);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.CREDENTIAL)
  @Operation(summary = "Create a credential")
  public CredentialOutput createCredential(
      TxCtx ctx,
      @Valid @RequestPart("input") CredentialInput input,
      @RequestPart(value = GCP_PRIVATE_KEY_PART) Optional<MultipartFile> gcpPrivateKeyJson) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return credentialService.createCredential(input, tenantId, readKeyFile(gcpPrivateKeyJson));
  }

  @PutMapping(path = "/{credentialId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  @AccessControl(
      resourceId = "#credentialId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.CREDENTIAL)
  @Operation(summary = "Update a credential with explicit secret update mode")
  public CredentialFullOutput updateCredential(
      TxCtx ctx,
      @PathVariable String credentialId,
      @Valid @RequestPart("input") CredentialInput input,
      @RequestPart(value = GCP_PRIVATE_KEY_PART) Optional<MultipartFile> gcpPrivateKeyJson) {

    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return credentialService.updateCredential(credentialId, input, tenantId, readKeyFile(gcpPrivateKeyJson));
  }

  /**
   * Reads the optional key file part.
   *
   * <p>An absent or empty part yields {@code null}, which the handlers read as "left untouched by
   * the client" — exactly like a null text field. The size is bounded before anything is read into
   * memory: a service account key file weighs about 2 KB, so anything past {@link
   * #MAX_GCP_PRIVATE_KEY_SIZE_BYTES} is an abuse attempt, not a credential.
   */
  private byte[] readKeyFile(Optional<MultipartFile> keyFile) {
    MultipartFile file = keyFile.filter(part -> !part.isEmpty()).orElse(null);
    if (file == null) {
      return null;
    }
    if (file.getSize() > MAX_GCP_PRIVATE_KEY_SIZE_BYTES) {
      throw new BadRequestException(
          "The GCP service account key file must not exceed "
              + MAX_GCP_PRIVATE_KEY_SIZE_BYTES
              + " bytes");
    }
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new BadRequestException("Unable to read the GCP service account key file");
    }
  }

  @DeleteMapping("/{credentialId}")
  @Transactional
  @AccessControl(
      resourceId = "#credentialId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.CREDENTIAL)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a credential")
  public void deleteCredential(TxCtx ctx, @PathVariable String credentialId) {
    credentialService.deleteCredential(credentialId);
  }

  @LogExecutionTime
  @DeleteMapping
  @Transactional
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.CREDENTIAL)
  @Operation(summary = "Bulk delete credentials")
  public List<String> bulkDeleteCredentials(
      TxCtx ctx, @RequestBody @Valid CredentialBulkProcessingInput input) {
    return credentialService.bulkDelete(ctx, input);
  }
}
