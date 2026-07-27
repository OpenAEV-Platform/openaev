package io.openaev.api.secrets_providers;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.secrets_providers.form.SecretsProviderOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.secrets.provider.SecretsProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({
  SecretsProviderApi.TENANT_SECRETS_PROVIDER_URI
})
@Tag(name = "Secrets Providers API", description = "Operations related to Secrets Providers")
public class SecretsProviderApi extends RestBehavior {
  public static final String TENANT_SECRETS_PROVIDER_URI = TENANT_PREFIX + "/secrets_providers";

  private final SecretsProviderService secretsProviderService;

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
      @Parameter(
              name = "includeNext",
              description = "Include secrets providers pending deployment",
              required = false)
          @RequestParam(value = "include_next", required = false, defaultValue = "false")
          boolean includeNext) {
    return secretsProviderService.secretsProviderOutput(includeNext);
  }

  @GetMapping({"/{secretsProviderId}"})
  @Transactional
  @AccessControl(
      resourceId = "#secretsProviderId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SECRET_PROVIDER)
  public SecretsProvider getSecretsProvider(@PathVariable String secretsProviderId) {
    try {
      return secretsProviderService.getConnectorById(secretsProviderId);
    } catch (ElementNotFoundException e) {
      log.warn(
          "Secrets provider with id {} not found - This may be because the integration has never been started yet",
          secretsProviderId);
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "Secrets provider not found");
    }
  }

  @GetMapping("/{secretsProviderId}/related-ids")
  @Transactional
  @AccessControl(
      resourceId = "#secretsProviderId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SECRET_PROVIDER)
  @Operation(summary = "Retrieve secrets provider related ids")
  public ConnectorIds getExecutorRelatedIds(@PathVariable String secretsProviderId) {
    return secretsProviderService.getSecretsProviderRelationsId(secretsProviderId);
  }
}
