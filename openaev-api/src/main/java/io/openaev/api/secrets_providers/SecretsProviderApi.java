package io.openaev.api.secrets_providers;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.secrets_providers.form.SecretsProviderOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({SecretsProviderApi.TENANT_SECRETS_PROVIDER_URI})
@Tag(name = "Secrets Providers API", description = "Operations related to Secrets Providers")
public class SecretsProviderApi extends RestBehavior {
  public static final String TENANT_SECRETS_PROVIDER_URI = TENANT_PREFIX + "/secrets_providers";

  private final SecretsProviderService secretsProviderService;
  private final FileService fileService;

  @GetMapping
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SECRET_PROVIDER)
  @Operation(
      summary = "Retrieve secrets providers",
      description =
          "Retrieve all secrets providers and pending secrets providers if includeNext is true")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              array = @ArraySchema(schema = @Schema(implementation = SecretsProviderOutput.class))))
  public Iterable<SecretsProviderOutput> secretsProviders(
      TxCtx ctx,
      @Parameter(
              name = "includeNext",
              description = "Include secrets providers pending deployment",
              required = false)
          @RequestParam(value = "include_next", required = false, defaultValue = "false")
          boolean includeNext) {
    return secretsProviderService.secretsProviderOutput(ctx, includeNext);
  }

  @GetMapping({"/{secretsProviderId}"})
  @Transactional
  @AccessControl(
      resourceId = "#secretsProviderId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SECRET_PROVIDER)
  public SecretsProvider getSecretsProvider(TxCtx ctx, @PathVariable String secretsProviderId) {
    SecretsProvider secretsProvider =
        secretsProviderService.getConnectorById(ctx, secretsProviderId);
    if (secretsProvider == null) {
      log.warn(
          "Secrets provider with id {} not found - This may be because the integration has never been started yet",
          secretsProviderId);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Secrets provider not found");
    }
    return secretsProvider;
  }

  @GetMapping("/{secretsProviderId}/related-ids")
  @Transactional
  @AccessControl(
      resourceId = "#secretsProviderId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SECRET_PROVIDER)
  @Operation(summary = "Retrieve secrets provider related ids")
  public ConnectorIds getSecretsProviderRelatedIds(
      TxCtx ctx, @PathVariable String secretsProviderId) {
    return secretsProviderService.getSecretsProviderRelationsId(ctx, secretsProviderId);
  }

  @GetMapping(value = "/{secretProviderType}/image", produces = MediaType.IMAGE_PNG_VALUE)
  @AccessControl(skipRBAC = true)
  @Operation(summary = "Get injector image by type")
  @Transactional
  public ResponseEntity<InputStreamResource> getInjectorImage(
      @PathVariable String secretProviderType) {
    return fileService.getConnectorImage(ConnectorType.SECRETS_PROVIDER, secretProviderType);
  }
}
