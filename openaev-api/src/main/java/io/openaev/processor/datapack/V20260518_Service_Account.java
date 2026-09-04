package io.openaev.processor.datapack;

import io.openaev.database.model.Tenant;
import io.openaev.service.DataPackService;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V20260518_Service_Account extends DataPack {
  private final ServiceAccountPrivilegeService privilegeService;

  public V20260518_Service_Account(
      DataPackService dataPackService, ServiceAccountPrivilegeService privilegeService) {
    super(dataPackService);
    this.privilegeService = privilegeService;
  }

  @Override
  protected boolean doProcess(Tenant tenant) {
    try {
      privilegeService.ensurePrivilegedUserExists(tenant.getId());
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20260518 initialization.", e);
      return false;
    }
    return true;
  }
}
