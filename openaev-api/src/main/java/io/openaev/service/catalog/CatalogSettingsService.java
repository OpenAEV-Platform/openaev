package io.openaev.service.catalog;

import static io.openaev.database.model.TenantSettingKeys.CATALOG_PAYLOAD_AUTO_INSTALL;

import io.openaev.context.TxCtx;
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

  /** Whether the payloads a release brings and the tenant never installed install themselves. */
  @Transactional(readOnly = true)
  public boolean isPayloadAutoInstall(TxCtx ctx, String tenantId) {
    return Boolean.parseBoolean(
        tenantSettingsService.resolveSettingValue(tenantId, CATALOG_PAYLOAD_AUTO_INSTALL));
  }

  // -- UPDATE --

  @Transactional(rollbackFor = Exception.class)
  public boolean setPayloadAutoInstall(TxCtx ctx, String tenantId, boolean enabled) {
    tenantSettingsService.updateCatalogPayloadAutoInstall(tenantId, enabled);
    return enabled;
  }
}
