package io.openaev.datapack;

import static io.openaev.opencti.connectors.Constants.PROCESS_STIX_GROUP_ID;
import static io.openaev.service.AbstractPrivilegeService.getUUIDFromName;
import static io.openaev.service.account.Constants.SERVICE_GROUP_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.processor.datapack.V20261028_Groups_receiving_every_marking;
import io.openaev.service.DataPackService;
import io.openaev.service.TenantGroupService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("V20261028 groups receiving every marking datapack")
class V20261028GroupsReceivingEveryMarkingTest {

  private static final Tenant TENANT = new Tenant("tenant-1");

  @Mock private DataPackService dataPackService;
  @Mock private TenantGroupService tenantGroupService;

  @InjectMocks private V20261028_Groups_receiving_every_marking dataPack;

  @Test
  @DisplayName("given the agent and OpenCTI groups, should make both receive every marking")
  void given_agentAndOpenCtiGroups_should_makeBothReceiveEveryMarking() {
    // Arrange
    Group agentGroup = groupWithId(getUUIDFromName(SERVICE_GROUP_ID, TENANT.getId()));
    Group openCtiGroup = groupWithId(getUUIDFromName(PROCESS_STIX_GROUP_ID, TENANT.getId()));
    when(tenantGroupService.findByIdAndTenant(agentGroup.getId(), TENANT.getId()))
        .thenReturn(Optional.of(agentGroup));
    when(tenantGroupService.findByIdAndTenant(openCtiGroup.getId(), TENANT.getId()))
        .thenReturn(Optional.of(openCtiGroup));

    // Act
    dataPack.process(TENANT);

    // Assert
    verify(tenantGroupService).receiveEveryMarking(agentGroup, TENANT.getId());
    verify(tenantGroupService).receiveEveryMarking(openCtiGroup, TENANT.getId());
    verify(dataPackService).registerDataPack(dataPack.getPackId(), TENANT);
  }

  @Test
  @DisplayName("given a failure, should stay unregistered to retry")
  void given_failure_should_stayUnregistered() {
    // Arrange
    when(tenantGroupService.findByIdAndTenant(anyString(), anyString()))
        .thenThrow(new IllegalStateException("boom"));

    // Act
    dataPack.process(TENANT);

    // Assert
    verify(dataPackService, never()).registerDataPack(anyString(), any(Tenant.class));
  }

  private Group groupWithId(String id) {
    Group group = new Group();
    group.setId(id);
    return group;
  }
}
