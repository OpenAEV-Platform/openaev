package io.openaev.service.catalog;

import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.service.settings.TenantSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The catalogue settings of a tenant. */
@Service
@RequiredArgsConstructor
public class CatalogSettingsService {

  private final TenantSettingsService tenantSettingsService;

  // -- LIST --

  @Transactional(readOnly = true)
  public CatalogSettings settings(TxCtx ctx, String tenantId) {
    return new CatalogSettings(
        isAutoInstall(tenantId, ContentType.PAYLOAD),
        isAutoInstall(tenantId, ContentType.SCENARIO));
  }

  /** Whether the content of this type a release brings and the tenant never had installs itself. */
  @Transactional(readOnly = true)
  public boolean isAutoInstall(TxCtx ctx, String tenantId, ContentType type) {
    return isAutoInstall(tenantId, type);
  }

  // -- UPDATE --

  @Transactional(rollbackFor = Exception.class)
  public CatalogSettings update(TxCtx ctx, String tenantId, CatalogSettings settings) {
    setAutoInstall(tenantId, ContentType.PAYLOAD, settings.payloadAutoInstall());
    setAutoInstall(tenantId, ContentType.SCENARIO, settings.scenarioAutoInstall());
    return settings;
  }

  // -- STEPS --

  private boolean isAutoInstall(String tenantId, ContentType type) {
    return Boolean.parseBoolean(
        tenantSettingsService.resolveSettingValue(tenantId, type.autoInstallSetting()));
  }

  private void setAutoInstall(String tenantId, ContentType type, boolean enabled) {
    tenantSettingsService.updateCatalogAutoInstall(tenantId, type.autoInstallSetting(), enabled);
  }
}
