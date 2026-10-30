package io.openaev.processor.datapack;

import io.openaev.database.model.Tenant;
import io.openaev.service.DataPackService;
import io.openaev.service.account.AgentServiceAccountPrivilegeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V20260518_Service_Account extends DataPack {
  private final AgentServiceAccountPrivilegeService agentServiceAccountPrivilegeService;

  public V20260518_Service_Account(
      DataPackService dataPackService,
      AgentServiceAccountPrivilegeService agentServiceAccountPrivilegeService) {
    super(dataPackService);
    this.agentServiceAccountPrivilegeService = agentServiceAccountPrivilegeService;
  }

  @Override
  protected boolean doProcess(Tenant tenant) {
    try {
      agentServiceAccountPrivilegeService.ensurePrivilegedUserExists(tenant.getId());
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20260518 initialization.", e);
      return false;
    }
    return true;
  }
}
