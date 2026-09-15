package io.openaev.processor.datapack;

import io.openaev.database.model.Tenant;
import io.openaev.service.DataPackService;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link V20260518_Service_Account} provisioned the per-tenant service-account role before {@code
 * INSTALL_AGENT} existed, so tenants migrated before this datapack still have a role missing that
 * capability. Re-running {@link ServiceAccountPrivilegeService#ensurePrivilegedUserExists}
 * refreshes the role's capabilities in place (it does not touch the already-issued token).
 */
@Component
@Slf4j
public class V20260915_Service_Account_Install_Agent extends DataPack {
  private final ServiceAccountPrivilegeService privilegeService;

  public V20260915_Service_Account_Install_Agent(
      DataPackService dataPackService, ServiceAccountPrivilegeService privilegeService) {
    super(dataPackService);
    this.privilegeService = privilegeService;
  }

  @Override
  protected boolean doProcess(Tenant tenant) {
    try {
      privilegeService.ensurePrivilegedUserExists(tenant.getId());
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20260915 initialization.", e);
      return false;
    }
    return true;
  }
}
