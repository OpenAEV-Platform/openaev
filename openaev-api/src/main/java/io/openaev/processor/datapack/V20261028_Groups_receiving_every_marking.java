package io.openaev.processor.datapack;

import static io.openaev.service.account.AbstractPrivilegeService.getUUIDFromName;

import io.openaev.database.model.Tenant;
import io.openaev.service.DataPackService;
import io.openaev.service.TenantGroupService;
import io.openaev.service.account.ReservedServiceAccount;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Makes the existing service account and "Admin" groups receive every marking of the tenant. */
@Component
@Slf4j
public class V20261028_Groups_receiving_every_marking extends DataPack {

  private final TenantGroupService tenantGroupService;

  public V20261028_Groups_receiving_every_marking(
      DataPackService dataPackService, TenantGroupService tenantGroupService) {
    super(dataPackService);
    this.tenantGroupService = tenantGroupService;
  }

  @Override
  protected boolean doProcess(Tenant tenant) {
    try {
      String tenantId = tenant.getId();
      for (ReservedServiceAccount account :
          List.of(
              ReservedServiceAccount.AGENT,
              ReservedServiceAccount.OPENCTI_CONNECTOR,
              ReservedServiceAccount.CATALOG_CONNECTOR)) {
        tenantGroupService
            .findByIdAndTenant(getUUIDFromName(account.access().groupId(), tenantId), tenantId)
            .ifPresent(group -> tenantGroupService.receiveEveryMarking(group, tenantId));
      }
      tenantGroupService
          .findByNameAndTenantId(PresetTenantData.ADMIN, tenantId)
          .ifPresent(group -> tenantGroupService.receiveEveryMarking(group, tenantId));
      return true;
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20261028 initialization.", e);
      return false;
    }
  }
}
