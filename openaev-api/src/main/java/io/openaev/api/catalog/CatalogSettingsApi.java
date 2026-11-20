package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.api.catalog.dto.CatalogSettingsInput;
import io.openaev.api.catalog.dto.CatalogSettingsOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.catalog.CatalogSettings;
import io.openaev.service.catalog.CatalogSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The catalogue settings of the tenant. */
@RestController
@RequiredArgsConstructor
public class CatalogSettingsApi {

  public static final String CATALOG_SETTINGS_URI = TENANT_CATALOG_URI + "/settings";

  private final CatalogSettingsService catalogSettingsService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final CatalogFeature catalogFeature;

  // -- READ --

  @Operation(summary = "Catalogue settings of the tenant")
  @GetMapping(CATALOG_SETTINGS_URI)
  @Transactional(readOnly = true)
  // Shown on the Threat Arsenal page: every user of the tenant reads it.
  @AccessControl(skipRBAC = true)
  public CatalogSettingsOutput settings(TxCtx ctx) {
    catalogFeature.require();
    return toOutput(
        catalogSettingsService.settings(ctx, writeScopeResolver.tenantForWrite(ctx, null)));
  }

  // -- UPDATE --

  @Operation(
      summary = "Turn on or off the install of what a release brings",
      description = "Newer versions of the installed payloads always install themselves.")
  @PutMapping(CATALOG_SETTINGS_URI)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  @Transactional(rollbackFor = Exception.class)
  public CatalogSettingsOutput updateSettings(
      TxCtx ctx, @Valid @RequestBody CatalogSettingsInput input) {
    catalogFeature.require();
    return toOutput(
        catalogSettingsService.update(
            ctx,
            writeScopeResolver.tenantForWrite(ctx, null),
            new CatalogSettings(input.payloadAutoInstall(), input.scenarioAutoInstall())));
  }

  private static CatalogSettingsOutput toOutput(CatalogSettings settings) {
    return new CatalogSettingsOutput(settings.payloadAutoInstall(), settings.scenarioAutoInstall());
  }
}
