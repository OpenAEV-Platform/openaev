package io.openaev.datapack;

import static io.openaev.service.account.AbstractPrivilegeService.getUUIDFromName;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.processor.datapack.PresetTenantData;
import io.openaev.processor.datapack.V20261028_Groups_receiving_every_marking;
import io.openaev.service.DataPackService;
import io.openaev.service.TenantGroupService;
import io.openaev.service.account.ReservedServiceAccount;
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
  @DisplayName(
      "given the service account and Admin groups, should make them all receive every marking")
  void given_serviceAccountAndAdminGroups_should_makeThemAllReceiveEveryMarking() {
    // Arrange
    Group agentGroup =
        groupWithId(
            getUUIDFromName(ReservedServiceAccount.AGENT.access().groupId(), TENANT.getId()));
    Group openCtiGroup =
        groupWithId(
            getUUIDFromName(
                ReservedServiceAccount.OPENCTI_CONNECTOR.access().groupId(), TENANT.getId()));
    Group catalogGroup =
        groupWithId(
            getUUIDFromName(
                ReservedServiceAccount.CATALOG_CONNECTOR.access().groupId(), TENANT.getId()));
    Group adminGroup = groupWithId("admin-group");
    when(tenantGroupService.findByIdAndTenant(agentGroup.getId(), TENANT.getId()))
        .thenReturn(Optional.of(agentGroup));
    when(tenantGroupService.findByIdAndTenant(openCtiGroup.getId(), TENANT.getId()))
        .thenReturn(Optional.of(openCtiGroup));
    when(tenantGroupService.findByIdAndTenant(catalogGroup.getId(), TENANT.getId()))
        .thenReturn(Optional.of(catalogGroup));
    when(tenantGroupService.findByNameAndTenantId(PresetTenantData.ADMIN, TENANT.getId()))
        .thenReturn(Optional.of(adminGroup));

    // Act
    dataPack.process(TENANT);

    // Assert
    verify(tenantGroupService).receiveEveryMarking(TENANT.getId(), agentGroup);
    verify(tenantGroupService).receiveEveryMarking(TENANT.getId(), openCtiGroup);
    verify(tenantGroupService).receiveEveryMarking(TENANT.getId(), catalogGroup);
    verify(tenantGroupService).receiveEveryMarking(TENANT.getId(), adminGroup);
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
