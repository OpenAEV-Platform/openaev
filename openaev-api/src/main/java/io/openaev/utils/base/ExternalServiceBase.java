package io.openaev.utils.base;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SettingRepository;
import lombok.Getter;

@Getter
public abstract class ExternalServiceBase {

  public boolean serviceAvailable = false;

  public abstract SettingRepository getSettingRepository();

  public abstract TenantScopedTransaction getTenantScopedTransaction();

  /**
   * Persists the service's availability flag, a platform row of the dual-scope {@code parameters}
   * table ({@code tenant_id IS NULL}). Called from a {@code @PostConstruct}/{@code @Scheduled}
   * health check with no ambient transaction or scope, so it opens its own short, platform-write
   * transaction rather than relying on one.
   */
  protected void saveServiceState(String key, boolean state) {
    getTenantScopedTransaction()
        .executeAsPlatformWrite(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
            () -> {
              Setting setting =
                  getSettingRepository()
                      .findByKeyAndTenantIsNull(key)
                      .orElse(new Setting(key, null));
              setting.setValue(String.valueOf(state));
              getSettingRepository().save(setting);
            });
    this.serviceAvailable = state;
  }
}
