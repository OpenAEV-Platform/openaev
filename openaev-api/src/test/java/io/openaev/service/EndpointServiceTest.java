package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EndpointServiceTest {

  private static final String TENANT_ID = "tenant-test-id";

  @Mock private EndpointRepository endpointRepository;
  @Mock private TagRepository tagRepository;
  @Mock private AgentService agentService;
  @Mock private AssetService assetService;

  @InjectMocks private EndpointService endpointService;

  @Nested
  @DisplayName("syncAgentsEndpoints - source tag")
  class SyncAgentsEndpointsSourceTag {

    private Executor createExecutor(String name, String type) {
      Executor executor = new Executor();
      executor.setName(name);
      executor.setType(type);
      executor.setBackgroundColor("#FF0000");
      executor.setTenantId(TENANT_ID);
      return executor;
    }

    private AgentRegisterInput createAgentRegisterInput(Executor executor, String externalRef) {
      AgentRegisterInput input = new AgentRegisterInput();
      input.setExecutor(executor);
      input.setExternalReference(externalRef);
      input.setName("test-host");
      input.setHostname("test-host");
      input.setIps(new String[] {"10.0.0.1"});
      input.setMacAddresses(new String[] {"AA:BB:CC:DD:EE:FF"});
      input.setSeenIp("10.0.0.1");
      input.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
      input.setArch(Endpoint.PLATFORM_ARCH.x86_64);
      input.setElevated(true);
      input.setService(true);
      input.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);
      input.setLastSeen(Instant.now());
      return input;
    }

    @Test
    @DisplayName("given new endpoint should add source tag for executor")
    void given_newCrowdStrikeEndpoint_should_addSourceTag() {
      // Arrange
      Executor csExecutor = createExecutor("CrowdStrike", "openaev_crowdstrike");
      AgentRegisterInput input = createAgentRegisterInput(csExecutor, "cs-device-001");

      Tag sourceTag = new Tag();
      sourceTag.setName("source:crowdstrike");
      sourceTag.setColor("#FF0000");

      when(tagRepository.findByNameAndTenantId("source:crowdstrike", TENANT_ID))
          .thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class))).thenReturn(sourceTag);
      when(endpointRepository.findByAtleastOneMacAddress(any(), eq(TENANT_ID)))
          .thenReturn(List.of());
      when(agentService.saveAllAgents(any())).thenAnswer(inv -> inv.getArgument(0));

      // Act
      endpointService.syncAgentsEndpoints(new ArrayList<>(List.of(input)), List.of(), TENANT_ID);

      // Assert
      ArgumentCaptor<List<Asset>> savedEndpoints = ArgumentCaptor.forClass(List.class);
      verify(assetService).saveAllAssets(savedEndpoints.capture());

      List<Asset> saved = savedEndpoints.getValue();
      assertThat(saved).hasSize(1);
      Endpoint savedEndpoint = (Endpoint) saved.getFirst();
      assertThat(savedEndpoint.getTags()).extracting(Tag::getName).contains("source:crowdstrike");
    }

    @Test
    @DisplayName("given existing source tag should reuse it without creating duplicate")
    void given_existingSourceTag_should_notCreateDuplicate() {
      // Arrange
      Executor csExecutor = createExecutor("CrowdStrike", "openaev_crowdstrike");
      AgentRegisterInput input = createAgentRegisterInput(csExecutor, "cs-device-002");

      Tag existingTag = new Tag();
      existingTag.setId(UUID.randomUUID().toString());
      existingTag.setName("source:crowdstrike");
      existingTag.setColor("#FF0000");

      when(tagRepository.findByNameAndTenantId("source:crowdstrike", TENANT_ID))
          .thenReturn(Optional.of(existingTag));
      when(endpointRepository.findByAtleastOneMacAddress(any(), eq(TENANT_ID)))
          .thenReturn(List.of());
      when(agentService.saveAllAgents(any())).thenAnswer(inv -> inv.getArgument(0));

      // Act
      endpointService.syncAgentsEndpoints(new ArrayList<>(List.of(input)), List.of(), TENANT_ID);

      // Assert
      verify(tagRepository, never()).save(any(Tag.class));

      ArgumentCaptor<List<Asset>> savedEndpoints = ArgumentCaptor.forClass(List.class);
      verify(assetService).saveAllAssets(savedEndpoints.capture());
      Endpoint savedEndpoint = (Endpoint) savedEndpoints.getValue().getFirst();
      assertThat(savedEndpoint.getTags()).contains(existingTag);
    }

    @Test
    @DisplayName(
        "given endpoint with another executor source tag should add new tag and preserve existing")
    void given_endpointWithOtherExecutorSourceTag_should_addNewTagAndPreserveExisting() {
      // Arrange
      Executor csExecutor = createExecutor("CrowdStrike", "openaev_crowdstrike");
      AgentRegisterInput input = createAgentRegisterInput(csExecutor, "cs-device-003");

      Endpoint existingEndpoint = EndpointFixture.createEndpoint();
      existingEndpoint.setId("existing-endpoint-id");
      Tag otherExecutorTag = new Tag();
      otherExecutorTag.setName("source:tanium");
      existingEndpoint.setTags(new HashSet<>(Set.of(otherExecutorTag)));

      Agent existingAgent = AgentFixture.createAgent(existingEndpoint, "cs-device-003");
      existingAgent.setExecutor(csExecutor);

      Tag csTag = new Tag();
      csTag.setName("source:crowdstrike");
      when(tagRepository.findByNameAndTenantId("source:crowdstrike", TENANT_ID))
          .thenReturn(Optional.of(csTag));
      when(agentService.saveAllAgents(any())).thenAnswer(inv -> inv.getArgument(0));

      // Act
      endpointService.syncAgentsEndpoints(
          new ArrayList<>(List.of(input)), List.of(existingAgent), TENANT_ID);

      // Assert: both source tags must be present (endpoint has multiple active executors)
      ArgumentCaptor<List<Asset>> savedEndpoints = ArgumentCaptor.forClass(List.class);
      verify(assetService).saveAllAssets(savedEndpoints.capture());
      Endpoint savedEndpoint = (Endpoint) savedEndpoints.getValue().getFirst();
      assertThat(savedEndpoint.getTags())
          .extracting(Tag::getName)
          .contains("source:crowdstrike", "source:tanium");
    }

    @Test
    @DisplayName("given inactive agent should remove source tag and not add it")
    void given_inactiveAgent_should_removeSourceTagAndNotAdd() {
      // Arrange
      Executor csExecutor = createExecutor("CrowdStrike", "openaev_crowdstrike");
      AgentRegisterInput input = createAgentRegisterInput(csExecutor, "cs-device-inactive");
      input.setLastSeen(Instant.now().minusSeconds(7200));

      Endpoint existingEndpoint = EndpointFixture.createEndpoint();
      existingEndpoint.setId("inactive-endpoint-id");
      existingEndpoint.setTenant(new Tenant(TENANT_ID));

      Agent existingAgent = AgentFixture.createAgent(existingEndpoint, "cs-device-inactive");
      existingAgent.setExecutor(csExecutor);

      Tag csTag = new Tag();
      csTag.setName("source:crowdstrike");
      existingEndpoint.setTags(new HashSet<>(Set.of(csTag)));

      when(agentService.saveAllAgents(any())).thenAnswer(inv -> inv.getArgument(0));
      when(endpointRepository.findAllByIdInWithTags(Set.of("inactive-endpoint-id")))
          .thenReturn(List.of(existingEndpoint));

      // Act
      endpointService.syncAgentsEndpoints(
          new ArrayList<>(List.of(input)), List.of(existingAgent), TENANT_ID);

      // Assert
      verify(tagRepository, never()).findByNameAndTenantId("source:crowdstrike", TENANT_ID);
      ArgumentCaptor<List<Endpoint>> savedEndpoints = ArgumentCaptor.forClass(List.class);
      verify(endpointRepository).saveAll(savedEndpoints.capture());
      assertThat(savedEndpoints.getValue().getFirst().getTags())
          .extracting(Tag::getName)
          .doesNotContain("source:crowdstrike");
    }
  }

  @Nested
  @DisplayName("removeSourceTagsFromAgentEndpoints")
  class RemoveSourceTagsFromAgentEndpoints {

    private static final String ENDPOINT_ID = "endpoint-test-id";

    @Test
    @DisplayName("given agents with executor tag should remove only that executor tag")
    void given_agentsWithExecutorTag_should_removeOnlyThatTag() {
      // Arrange
      Executor csExecutor = new Executor();
      csExecutor.setName("CrowdStrike");

      Tag csTag = new Tag();
      csTag.setName("source:crowdstrike");
      Tag taniumTag = new Tag();
      taniumTag.setName("source:tanium");

      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setId(ENDPOINT_ID);
      endpoint.setTenant(new Tenant(TENANT_ID));

      Agent agent = new Agent();
      agent.setAsset(endpoint);
      agent.setExecutor(csExecutor);
      endpoint.setTags(new HashSet<>(Set.of(csTag, taniumTag)));

      when(endpointRepository.findAllByIdInWithTags(Set.of(ENDPOINT_ID)))
          .thenReturn(List.of(endpoint));

      // Act
      endpointService.removeSourceTagsFromAgentEndpoints(List.of(agent));

      // Assert
      ArgumentCaptor<List<Endpoint>> captor = ArgumentCaptor.forClass(List.class);
      verify(endpointRepository).saveAll(captor.capture());
      Endpoint saved = captor.getValue().getFirst();
      assertThat(saved.getTags())
          .extracting(Tag::getName)
          .contains("source:tanium")
          .doesNotContain("source:crowdstrike");
    }

    @Test
    @DisplayName("given agents without matching executor tag should not save endpoints")
    void given_agentsWithoutMatchingTag_should_notSaveEndpoints() {
      // Arrange
      Executor csExecutor = new Executor();
      csExecutor.setName("CrowdStrike");

      Tag taniumTag = new Tag();
      taniumTag.setName("source:tanium");

      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setId(ENDPOINT_ID);
      endpoint.setTenant(new Tenant(TENANT_ID));

      Agent agent = new Agent();
      agent.setAsset(endpoint);
      agent.setExecutor(csExecutor);
      endpoint.setTags(new HashSet<>(Set.of(taniumTag)));

      when(endpointRepository.findAllByIdInWithTags(Set.of(ENDPOINT_ID)))
          .thenReturn(List.of(endpoint));

      // Act
      endpointService.removeSourceTagsFromAgentEndpoints(List.of(agent));

      // Assert: no matching tag removed, save never called
      verify(endpointRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("given agents with no tags on endpoint should not fail")
    void given_agentsWithNoTags_should_notFail() {
      // Arrange
      Executor csExecutor = new Executor();
      csExecutor.setName("CrowdStrike");

      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setId(ENDPOINT_ID);
      endpoint.setTenant(new Tenant(TENANT_ID));

      Agent agent = new Agent();
      agent.setAsset(endpoint);
      agent.setExecutor(csExecutor);
      endpoint.setTags(new HashSet<>());

      when(endpointRepository.findAllByIdInWithTags(Set.of(ENDPOINT_ID)))
          .thenReturn(List.of(endpoint));

      // Act
      endpointService.removeSourceTagsFromAgentEndpoints(List.of(agent));

      // Assert
      verify(endpointRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("given empty agent list should do nothing")
    void given_emptyAgentList_should_doNothing() {
      // Act
      endpointService.removeSourceTagsFromAgentEndpoints(List.of());

      // Assert
      verifyNoInteractions(endpointRepository, tagRepository);
    }
  }
}
