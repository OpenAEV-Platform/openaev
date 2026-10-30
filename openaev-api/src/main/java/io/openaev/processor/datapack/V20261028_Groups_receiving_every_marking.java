package io.openaev.processor.datapack;

import static io.openaev.opencti.connectors.Constants.PROCESS_STIX_GROUP_ID;
import static io.openaev.service.AbstractPrivilegeService.getUUIDFromName;
import static io.openaev.service.account.Constants.SERVICE_GROUP_ID;

import io.openaev.database.model.Tenant;
import io.openaev.service.DataPackService;
import io.openaev.service.TenantGroupService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Makes the existing agent and OpenCTI groups receive every marking of the tenant. */
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
      for (String wellKnownId : List.of(SERVICE_GROUP_ID, PROCESS_STIX_GROUP_ID)) {
        tenantGroupService
            .findByIdAndTenant(getUUIDFromName(wellKnownId, tenantId), tenantId)
            .ifPresent(group -> tenantGroupService.receiveEveryMarking(group, tenantId));
      }
      return true;
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20261028 initialization.", e);
      return false;
    }
  }
}
