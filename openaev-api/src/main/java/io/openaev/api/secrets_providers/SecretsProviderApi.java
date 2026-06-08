package io.openaev.api.secrets_providers;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.secrets_providers.form.SecretsProviderOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({
  SecretsProviderApi.SECRETS_PROVIDER_URI,
  SecretsProviderApi.TENANT_SECRETS_PROVIDER_URI
})
@Tag(name = "Secrets Providers API", description = "Operations related to Secrets Providers")
public class SecretsProviderApi extends RestBehavior {
  public static final String SECRETS_PROVIDER_URI = "/api/secrets_providers";
  public static final String TENANT_SECRETS_PROVIDER_URI = TENANT_PREFIX + "/secrets_providers";

  private final SecretsProviderService secretsProviderService;

  @GetMapping
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
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
}
