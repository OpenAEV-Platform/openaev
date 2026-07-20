package io.openaev.service.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetAgentJob;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.AgentService;
import io.openaev.service.AssetService;
import io.openaev.service.EndpointService;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetAgentJobFixture;
import io.openaev.utils.mapper.EndpointMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EndpointServiceTest {

  private static final int MAX_SIMULTANEOUS_JOBS = 5;

  @Mock private EndpointRepository endpointRepository;
  @Mock private ExecutorRepository executorRepository;
  @Mock private AssetGroupRepository assetGroupRepository;
  @Mock private AssetAgentJobRepository assetAgentJobRepository;
  @Mock private TagRepository tagRepository;
  @Mock private AgentService agentService;
  @Mock private AssetService assetService;
  @Mock private EndpointMapper endpointMapper;
  @Mock private ServiceAccountPrivilegeService privilegeService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private EndpointService endpointService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(endpointService, "maxSimultaneousJobs", MAX_SIMULTANEOUS_JOBS);
  }

  @Nested
  class CreateEndpoint {

    @Test
    void shouldSaveEndpoint_whenGivenEntity() {
      // -------- Prepare --------
      Endpoint endpoint = new Endpoint();
      endpoint.setHostname("host1");
      when(endpointRepository.save(endpoint)).thenReturn(endpoint);

      // -------- Act --------
      Endpoint result = endpointService.createEndpoint(endpoint);

      // -------- Assert --------
      assertNotNull(result);
      assertEquals("host1", result.getHostname());
      verify(endpointRepository).save(endpoint);
    }

    @Test
    void shouldCreateEndpointFromInput_withIpsAndMacAndTags() {
      // -------- Prepare --------
      EndpointInput input = new EndpointInput();
      input.setName("test-endpoint");
      input.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
      input.setArch(Endpoint.PLATFORM_ARCH.x86_64);
      input.setHostname("host1");
      input.setIps(new String[] {"10.0.0.1"});
      input.setMacAddresses(new String[] {"AA:BB:CC:DD:EE:FF"});
      input.setTagIds(List.of("tag-1"));
      input.setEol(true);

      Tag tag = new Tag();
      tag.setId("tag-1");
      when(tagRepository.findAllById(List.of("tag-1"))).thenReturn(List.of(tag));
      when(endpointRepository.save(any(Endpoint.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Endpoint result = endpointService.createEndpoint(input);

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEoL());
      assertThat(result.getSeenIp()).isEqualTo("10.0.0.1");
      verify(tagRepository).findAllById(List.of("tag-1"));
      verify(endpointRepository).save(any(Endpoint.class));
    }
  }

  @Nested
  class ReadEndpoint {

    @Test
    void shouldReturnEndpoint_whenFound() {
      // -------- Prepare --------
      Endpoint endpoint = new Endpoint();
      endpoint.setId("ep-1");
      when(endpointRepository.findByIdAndTenantId("ep-1", "tenant1"))
          .thenReturn(Optional.of(endpoint));

      // -------- Act --------
      Endpoint result = endpointService.endpoint("ep-1", "tenant1");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals("ep-1", result.getId());
    }

    @Test
    void shouldThrowElementNotFoundException_whenNotFound() {
      // -------- Prepare --------
      when(endpointRepository.findByIdAndTenantId("missing", "tenant1"))
          .thenReturn(Optional.empty());

      // -------- Act / Assert --------
      assertThrows(
          ElementNotFoundException.class, () -> endpointService.endpoint("missing", "tenant1"));
    }

    @Test
    void shouldReturnAllEndpoints() {
      // -------- Prepare --------
      Endpoint ep1 = new Endpoint();
      Endpoint ep2 = new Endpoint();
      when(endpointRepository.findAll()).thenReturn(List.of(ep1, ep2));

      // -------- Act --------
      List<Endpoint> result = endpointService.endpoints();

      // -------- Assert --------
      assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEndpointsByIds() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      ep.setId("ep-1");
      when(endpointRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
          .thenReturn(List.of(ep));

      // -------- Act --------
      List<Endpoint> result = endpointService.endpoints(List.of("ep-1"));

      // -------- Assert --------
      assertEquals(1, result.size());
    }

    @Test
    void shouldFindByHostnameAndIp() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      String[] ips = {"10.0.0.1"};
      when(endpointRepository.findByHostnameAndAtleastOneIp(
              "host1", ips, TenantContext.getCurrentTenant()))
          .thenReturn(List.of(ep));

      // -------- Act --------
      List<Endpoint> result =
          endpointService.findEndpointByHostnameAndAtLeastOneIp(
              "host1", ips, TenantContext.getCurrentTenant());

      // -------- Assert --------
      assertEquals(1, result.size());
    }

    @Test
    void shouldFindByHostnameAndMacAddress() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      String[] macs = {"AA:BB:CC:DD:EE:FF"};
      when(endpointRepository.findByHostnameAndAtleastOneMacAddress("host1", macs, "tenant1"))
          .thenReturn(List.of(ep));

      // -------- Act --------
      List<Endpoint> result =
          endpointService.findEndpointByHostnameAndAtLeastOneMacAddress("host1", macs, "tenant1");

      // -------- Assert --------
      assertEquals(1, result.size());
    }

    @Test
    void shouldFindByExternalReference() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      when(endpointRepository.findByExternalReference("ext-ref", "tenant1"))
          .thenReturn(List.of(ep));

      // -------- Act --------
      Optional<Endpoint> result =
          endpointService.findEndpointByExternalReference("ext-ref", "tenant1");

      // -------- Assert --------
      assertTrue(result.isPresent());
    }

    @Test
    void shouldFindByMacAddress() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      String[] macs = {"AA:BB:CC:DD:EE:FF"};
      when(endpointRepository.findByAtleastOneMacAddress(macs, "tenant1")).thenReturn(List.of(ep));

      // -------- Act --------
      Optional<Endpoint> result =
          endpointService.findEndpointByAtLeastOneMacAddress(macs, "tenant1");

      // -------- Assert --------
      assertTrue(result.isPresent());
    }

    @Test
    void shouldReturnEmptyOptional_whenNoMacAddressMatch() {
      // -------- Prepare --------
      String[] macs = {"00:00:00:00:00:00"};
      when(endpointRepository.findByAtleastOneMacAddress(macs, "tenant1"))
          .thenReturn(Collections.emptyList());

      // -------- Act --------
      Optional<Endpoint> result =
          endpointService.findEndpointByAtLeastOneMacAddress(macs, "tenant1");

      // -------- Assert --------
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  class TenantIsolation {

    @Test
    @DisplayName("Endpoint lookup should use current tenant and return endpoint from same tenant")
    void given_endpointInTenantX_should_beReadableFromTenantX() {
      // -------- Prepare --------
      Endpoint endpoint = new Endpoint();
      endpoint.setId("ep-tenant-x");
      when(endpointRepository.findByIdAndTenantId("ep-tenant-x", "tenant-x"))
          .thenReturn(Optional.of(endpoint));

      // -------- Act --------
      Endpoint result = endpointService.endpoint("ep-tenant-x", "tenant-x");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals("ep-tenant-x", result.getId());
      verify(endpointRepository).findByIdAndTenantId("ep-tenant-x", "tenant-x");
    }

    @Test
    @DisplayName("Endpoint lookup should NOT return endpoint from another tenant")
    void given_endpointInTenantX_should_notBeReadableFromTenantY() {
      // -------- Prepare --------
      when(endpointRepository.findByIdAndTenantId("ep-tenant-x", "tenant-y"))
          .thenReturn(Optional.empty());

      // -------- Act / Assert --------
      assertThrows(
          ElementNotFoundException.class,
          () -> endpointService.endpoint("ep-tenant-x", "tenant-y"));
      verify(endpointRepository).findByIdAndTenantId("ep-tenant-x", "tenant-y");
    }

    @Test
    @DisplayName("Hostname and IP lookup with explicit tenant should use provided tenant")
    void given_hostnameAndIpLookup_should_useProvidedTenant() {
      // -------- Prepare --------
      String[] ips = {"10.0.0.1"};
      when(endpointRepository.findByHostnameAndAtleastOneIp("host-x", ips, "tenant-x"))
          .thenReturn(List.of(new Endpoint()));

      // -------- Act --------
      List<Endpoint> result =
          endpointService.findEndpointByHostnameAndAtLeastOneIp("host-x", ips, "tenant-x");

      // -------- Assert --------
      assertThat(result).hasSize(1);
      verify(endpointRepository).findByHostnameAndAtleastOneIp("host-x", ips, "tenant-x");
    }

    @Test
    @DisplayName("Hostname and IP lookup with explicit tenant should use provided tenant")
    void given_hostnameAndIpLookupWithTenantArgument_should_useProvidedTenant() {
      // -------- Prepare --------
      String[] ips = {"10.0.0.2"};
      when(endpointRepository.findByHostnameAndAtleastOneIp("host-explicit", ips, "tenant-z"))
          .thenReturn(List.of(new Endpoint()));

      // -------- Act --------
      List<Endpoint> result =
          endpointService.findEndpointByHostnameAndAtLeastOneIp("host-explicit", ips, "tenant-z");

      // -------- Assert --------
      assertThat(result).hasSize(1);
      verify(endpointRepository).findByHostnameAndAtleastOneIp("host-explicit", ips, "tenant-z");
    }
  }

  @Nested
  @DisplayName("getEndpointJobs")
  class GetEndpointJobs {

    @Test
    void given_serviceModeInput_should_returnMatchingJobs() {
      // Arrange
      Agent agent = AgentFixture.createDefaultAgentService();
      AssetAgentJob job = AssetAgentJobFixture.createDefaultAssetAgentJob(agent);

      EndpointRegisterInput input = new EndpointRegisterInput();
      input.setExternalReference("ref-001");
      input.setService(true);
      input.setElevated(true);
      input.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);

      when(assetAgentJobRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(job)));

      // Act
      List<AssetAgentJob> result = endpointService.getEndpointJobs(input);

      // Assert
      assertThat(result).containsExactly(job);
    }

    @Test
    void given_noMatchingJobs_should_returnEmptyList() {
      // Arrange
      EndpointRegisterInput input = new EndpointRegisterInput();
      input.setExternalReference("ref-unknown");
      input.setService(true);
      input.setElevated(true);
      input.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);

      when(assetAgentJobRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of()));

      // Act
      List<AssetAgentJob> result = endpointService.getEndpointJobs(input);

      // Assert
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class UpdateEndpoint {

    @Test
    void shouldUpdateEndpointAndSetTimestamp() {
      // -------- Prepare --------
      Endpoint endpoint = new Endpoint();
      endpoint.setId("ep-1");
      when(endpointRepository.save(any(Endpoint.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Endpoint result = endpointService.updateEndpoint(endpoint);

      // -------- Assert --------
      assertNotNull(result.getUpdatedAt());
      verify(endpointRepository).save(endpoint);
    }

    @Test
    void shouldUpdateEndpointFromInput() {
      // -------- Prepare --------
      Endpoint existing = new Endpoint();
      existing.setId("ep-1");
      when(endpointRepository.findByIdAndTenantId("ep-1", "tenant1"))
          .thenReturn(Optional.of(existing));

      EndpointInput input = new EndpointInput();
      input.setName("updated-endpoint");
      input.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
      input.setArch(Endpoint.PLATFORM_ARCH.x86_64);
      input.setHostname("host-updated");
      input.setIps(new String[] {"10.0.0.1"});
      input.setEol(false);
      input.setTagIds(List.of());
      when(tagRepository.findAllById(List.of())).thenReturn(Collections.emptyList());
      when(endpointRepository.save(any(Endpoint.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Endpoint result = endpointService.updateEndpoint("ep-1", input, "tenant1");

      // -------- Assert --------
      assertNotNull(result);
      assertFalse(result.isEoL());
      assertThat(result.getSeenIp()).isEqualTo("10.0.0.1");
      verify(endpointRepository).save(any(Endpoint.class));
    }
  }

  @Nested
  class DeleteEndpoint {

    @Test
    void shouldDeleteEndpointById() {
      // -------- Prepare --------
      // no special setup needed

      // -------- Act --------
      endpointService.deleteEndpoint("ep-1");

      // -------- Assert --------
      verify(endpointRepository).deleteById("ep-1");
    }
  }

  @Nested
  class InstallationDirectory {

    @Test
    void shouldReturnWindowsServiceDir_whenPlatformIsWindows() {
      String result = endpointService.generateInstallationDir("Windows", "service", null);
      assertEquals(EndpointService.OPENAEV_INSTALL_DIR_WINDOWS_SERVICE, result);
    }

    @Test
    void shouldReturnWindowsServiceUserDir_whenModeIsServiceUser() {
      String result = endpointService.generateInstallationDir("Windows", "service-user", null);
      assertEquals(EndpointService.OPENAEV_INSTALL_DIR_WINDOWS_SERVICE_USER, result);
    }

    @Test
    void shouldReturnWindowsSessionUserDir_whenModeIsSessionUser() {
      String result = endpointService.generateInstallationDir("Windows", "session-user", null);
      assertEquals(EndpointService.OPENAEV_INSTALL_DIR_WINDOWS_SESSION_USER, result);
    }

    @Test
    void shouldReturnUnixServiceDir_whenPlatformIsLinux() {
      String result = endpointService.generateInstallationDir("Linux", "service", null);
      assertEquals(EndpointService.OPENAEV_INSTALL_DIR_UNIX_SERVICE, result);
    }

    @Test
    void shouldReturnUnixServiceUserDir() {
      String result = endpointService.generateInstallationDir("Linux", "service-user", null);
      assertEquals(EndpointService.OPENAEV_INSTALL_DIR_UNIX_SERVICE_USER, result);
    }

    @Test
    void shouldReturnUnixSessionUserDir() {
      String result = endpointService.generateInstallationDir("Linux", "session-user", null);
      assertEquals(EndpointService.OPENAEV_INSTALL_DIR_UNIX_SESSION_USER, result);
    }

    @Test
    void shouldReturnCustomDir_whenProvided() {
      String result = endpointService.generateInstallationDir("Windows", "service", "/custom/dir");
      assertEquals("/custom/dir", result);
    }
  }

  @Nested
  class ServiceName {

    @Test
    void shouldReturnWindowsServiceName_whenPlatformIsWindows() {
      String result = endpointService.generateServiceNameOrPrefix("Windows", "service", null);
      assertEquals(EndpointService.OPENAEV_SERVICE_NAME_WINDOWS_SERVICE, result);
    }

    @Test
    void shouldReturnWindowsServiceUserName() {
      String result = endpointService.generateServiceNameOrPrefix("Windows", "service-user", null);
      assertEquals(EndpointService.OPENAEV_SERVICE_NAME_WINDOWS_SERVICE_USER, result);
    }

    @Test
    void shouldReturnWindowsSessionUserName() {
      String result = endpointService.generateServiceNameOrPrefix("Windows", "session-user", null);
      assertEquals(EndpointService.OPENAEV_SERVICE_NAME_WINDOWS_SESSION_USER, result);
    }

    @Test
    void shouldReturnUnixServiceName() {
      String result = endpointService.generateServiceNameOrPrefix("Linux", "service", null);
      assertEquals(EndpointService.OPENAEV_SERVICE_NAME_UNIX_SERVICE, result);
    }

    @Test
    void shouldReturnUnixSessionUserName() {
      String result = endpointService.generateServiceNameOrPrefix("Linux", "session-user", null);
      assertEquals(EndpointService.OPENAEV_SERVICE_NAME_UNIX_SESSION_USER, result);
    }

    @Test
    void shouldReturnCustomName_whenProvided() {
      String result =
          endpointService.generateServiceNameOrPrefix("Windows", "service", "my-service");
      assertEquals("my-service", result);
    }
  }

  @Nested
  class ScenarioAndSimulationEndpoints {

    @Test
    void shouldReturnEndpointsForScenario() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      when(endpointRepository.findDistinctByInjectsScenarioId("sc-1")).thenReturn(List.of(ep));

      // -------- Act --------
      List<Endpoint> result = endpointService.endpointsForScenario("sc-1");

      // -------- Assert --------
      assertEquals(1, result.size());
    }

    @Test
    void shouldReturnEndpointsForSimulation() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      when(endpointRepository.findDistinctByInjectsExerciseId("sim-1")).thenReturn(List.of(ep));

      // -------- Act --------
      List<Endpoint> result = endpointService.endpointsForSimulation("sim-1");

      // -------- Assert --------
      assertEquals(1, result.size());
    }

    @Test
    void shouldReturnEndpointOutputsForScenario() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      EndpointOutput output = EndpointOutput.builder().id("ep-1").build();
      when(endpointRepository.findDistinctByInjectsScenarioIdAndIdIn("sc-1", List.of("ep-1")))
          .thenReturn(List.of(ep));
      when(endpointMapper.toEndpointOutput(ep)).thenReturn(output);

      // -------- Act --------
      List<EndpointOutput> result =
          endpointService.endpointsByIdsForScenario("sc-1", List.of("ep-1"));

      // -------- Assert --------
      assertEquals(1, result.size());
      assertEquals("ep-1", result.getFirst().getId());
    }

    @Test
    void shouldReturnEndpointOutputsForSimulation() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      EndpointOutput output = EndpointOutput.builder().id("ep-2").build();
      when(endpointRepository.findDistinctByInjectsExerciseIdAndIdIn("sim-1", List.of("ep-2")))
          .thenReturn(List.of(ep));
      when(endpointMapper.toEndpointOutput(ep)).thenReturn(output);

      // -------- Act --------
      List<EndpointOutput> result =
          endpointService.endpointsByIdsForSimulation("sim-1", List.of("ep-2"));

      // -------- Assert --------
      assertEquals(1, result.size());
      assertEquals("ep-2", result.getFirst().getId());
    }
  }
}
