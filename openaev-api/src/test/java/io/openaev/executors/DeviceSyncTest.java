package io.openaev.executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.model.DeviceGroup;
import io.openaev.executors.model.RemoteDevice;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceSyncTest {

  private static final String TENANT_ID = "tenant-id";
  private static final DeviceGroup GROUP_A = new DeviceGroup("group-a", "Group A", null);
  private static final DeviceGroup GROUP_B = new DeviceGroup("group-b", "Group B", "From the EDR");

  @Mock private EndpointService endpointService;
  @Mock private AgentService agentService;
  @Mock private AssetGroupService assetGroupService;
  @Mock private TenantScopedTransaction tenantTx;

  private DeviceSync deviceSync;
  private Executor executor;

  @BeforeEach
  void setUp() {
    deviceSync = new DeviceSync(endpointService, agentService, assetGroupService, tenantTx);
    executor = new Executor();
    executor.setId("executor-id");
    executor.setTenantId(TENANT_ID);
    lenient()
        .when(tenantTx.execute(any(), any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
  }

  private static AgentRegisterInput agentInput(String externalReference) {
    AgentRegisterInput input = new AgentRegisterInput();
    input.setExternalReference(externalReference);
    return input;
  }

  @Nested
  @DisplayName("Syncing the devices of an executor")
  class Sync {

    @Test
    void given_deviceInTwoGroups_should_syncItOnceAndPutEachEndpointInItsGroups() {
      // Arrange
      Endpoint shared = EndpointFixture.createEndpoint();
      Endpoint onlyInA = EndpointFixture.createEndpoint();
      when(endpointService.syncAgentsEndpoints(any(), any(), eq(TENANT_ID)))
          .thenReturn(
              List.of(
                  AgentFixture.createAgent(shared, "shared"),
                  AgentFixture.createAgent(onlyInA, "only-in-a")));
      when(assetGroupService.findByExternalReference(anyString(), eq(TENANT_ID)))
          .thenReturn(Optional.empty());
      List<RemoteDevice> devices =
          List.of(
              new RemoteDevice(agentInput("shared"), Set.of(GROUP_A)),
              new RemoteDevice(agentInput("only-in-a"), Set.of(GROUP_A)),
              new RemoteDevice(agentInput("shared"), Set.of(GROUP_B)));

      // Act
      deviceSync.sync(executor, () -> devices);

      // Assert
      ArgumentCaptor<List<AgentRegisterInput>> inputs = ArgumentCaptor.forClass(List.class);
      verify(endpointService).syncAgentsEndpoints(inputs.capture(), any(), eq(TENANT_ID));
      assertThat(inputs.getValue())
          .extracting(AgentRegisterInput::getExternalReference)
          .containsExactly("shared", "only-in-a");
      Map<String, List<Asset>> assetsByGroup = savedAssetGroups();
      assertThat(assetsByGroup.get("group-a")).containsExactlyInAnyOrder(shared, onlyInA);
      assertThat(assetsByGroup.get("group-b")).containsExactly(shared);
    }

    @Test
    void given_existingAndNewGroups_should_reuseTheExistingOneAndCreateTheOtherInTheTenant() {
      // Arrange
      AssetGroup existing = AssetGroupFixture.createDefaultAssetGroup("Old name");
      existing.setDescription("Written by a user");
      when(assetGroupService.findByExternalReference(GROUP_A.externalReference(), TENANT_ID))
          .thenReturn(Optional.of(existing));
      when(assetGroupService.findByExternalReference(GROUP_B.externalReference(), TENANT_ID))
          .thenReturn(Optional.empty());
      when(endpointService.syncAgentsEndpoints(any(), any(), eq(TENANT_ID))).thenReturn(List.of());
      List<RemoteDevice> devices =
          List.of(new RemoteDevice(agentInput("device"), Set.of(GROUP_A, GROUP_B)));

      // Act
      deviceSync.sync(executor, () -> devices);

      // Assert
      ArgumentCaptor<AssetGroup> saved = ArgumentCaptor.forClass(AssetGroup.class);
      verify(assetGroupService, times(2))
          .createOrUpdateAssetGroupWithoutDynamicAssets(saved.capture(), eq(TENANT_ID));
      AssetGroup savedA = saved.getAllValues().get(0);
      AssetGroup savedB = saved.getAllValues().get(1);
      assertThat(savedA).isSameAs(existing);
      assertThat(savedA.getName()).isEqualTo("Group A");
      assertThat(savedA.getDescription()).isEqualTo("Written by a user");
      assertThat(savedB.getExternalReference()).isEqualTo("group-b");
      assertThat(savedB.getDescription()).isEqualTo("From the EDR");
      assertThat(savedB.getTenant().getId()).isEqualTo(TENANT_ID);
    }

    @Test
    void given_devicesRead_should_readThemBeforeOpeningTheTransaction() {
      // Arrange
      Supplier<List<RemoteDevice>> devices =
          () -> {
            verify(tenantTx, never()).execute(any(), any(Supplier.class));
            return List.of(new RemoteDevice(agentInput("device"), Set.of()));
          };

      // Act
      deviceSync.sync(executor, devices);

      // Assert
      verify(tenantTx).execute(any(), any(Supplier.class));
      verifyNoInteractions(assetGroupService);
    }

    @Test
    void given_noDevice_should_writeNothing() {
      // Act
      deviceSync.sync(executor, List::of);

      // Assert
      verifyNoInteractions(tenantTx, endpointService, agentService, assetGroupService);
    }

    private Map<String, List<Asset>> savedAssetGroups() {
      ArgumentCaptor<AssetGroup> saved = ArgumentCaptor.forClass(AssetGroup.class);
      verify(assetGroupService, atLeastOnce())
          .createOrUpdateAssetGroupWithoutDynamicAssets(saved.capture(), eq(TENANT_ID));
      return saved.getAllValues().stream()
          .collect(Collectors.toMap(AssetGroup::getExternalReference, AssetGroup::getAssets));
    }
  }

  @Nested
  @DisplayName("Reading the devices group by group")
  class ReadEach {

    @Test
    void given_aGroupFailing_should_skipItAndReadTheOthers() {
      // Arrange
      RemoteDevice device = new RemoteDevice(agentInput("device"), Set.of(GROUP_B));

      // Act
      List<RemoteDevice> devices =
          DeviceGroup.readEach(
              List.of("failing", "group-b"),
              groupId -> {
                if (groupId.equals("failing")) {
                  throw new IllegalStateException("EDR unavailable");
                }
                return List.of(device);
              });

      // Assert
      assertThat(devices).containsExactly(device);
    }
  }
}
