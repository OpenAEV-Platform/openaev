package io.openaev.api.catalog.scenario;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.catalog.CatalogFeature;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioInstallInput;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioInstallOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.catalog.scenario.CatalogScenarioInstaller;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Adds catalogue scenarios to the tenant; deleting one uninstalls it. */
@RestController
@RequiredArgsConstructor
public class CatalogScenarioInstallApi {

  private final CatalogScenarioInstaller catalogScenarioInstaller;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final CatalogFeature catalogFeature;

  // -- CREATE --

  @LogExecutionTime
  @Operation(
      summary = "Install a catalogue scenario version",
      description =
          "Installs its payload versions first; refused if installed or the platform lacks a need.")
  @PostMapping(TENANT_CATALOG_URI + "/scenarios/{externalId}/versions/{version}/install")
  @ResponseStatus(HttpStatus.CREATED)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
  @Transactional(propagation = Propagation.SUPPORTS)
  public CatalogScenarioInstallOutput installScenario(
      TxCtx ctx,
      @PathVariable String externalId,
      @PathVariable String version,
      @Valid @RequestBody(required = false) CatalogScenarioInstallInput input)
      throws Exception {
    catalogFeature.require();
    return new CatalogScenarioInstallOutput(
        catalogScenarioInstaller
            .install(
                ctx,
                writeScopeResolver.tenantForWrite(ctx, null),
                externalId,
                version,
                targets(input))
            .getId());
  }

  private static TargetChoice targets(CatalogScenarioInstallInput input) {
    return input == null
        ? new TargetChoice(List.of(), List.of(), List.of(), List.of())
        : new TargetChoice(
            orEmpty(input.assetIds()),
            orEmpty(input.assetGroupIds()),
            orEmpty(input.teamIds()),
            orEmpty(input.manualTargets()));
  }

  private static List<String> orEmpty(List<String> values) {
    return values == null ? List.of() : values;
  }
}
