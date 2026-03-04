package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.mapper.EndpointMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EndpointServiceTest {

  @Mock private EndpointRepository endpointRepository;
  @Mock private ExecutorRepository executorRepository;
  @Mock private AssetGroupRepository assetGroupRepository;
  @Mock private AssetAgentJobRepository assetAgentJobRepository;
  @Mock private TagRepository tagRepository;
  @Mock private AgentService agentService;
  @Mock private AssetService assetService;
  @Mock private EndpointMapper endpointMapper;

  @InjectMocks private EndpointService endpointService;

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
      when(endpointRepository.findById("ep-1")).thenReturn(Optional.of(endpoint));

      // -------- Act --------
      Endpoint result = endpointService.endpoint("ep-1");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals("ep-1", result.getId());
    }

    @Test
    void shouldThrowElementNotFoundException_whenNotFound() {
      // -------- Prepare --------
      when(endpointRepository.findById("missing")).thenReturn(Optional.empty());

      // -------- Act / Assert --------
      assertThrows(ElementNotFoundException.class, () -> endpointService.endpoint("missing"));
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
      when(endpointRepository.findByHostnameAndAtleastOneIp("host1", ips)).thenReturn(List.of(ep));

      // -------- Act --------
      List<Endpoint> result = endpointService.findEndpointByHostnameAndAtLeastOneIp("host1", ips);

      // -------- Assert --------
      assertEquals(1, result.size());
    }

    @Test
    void shouldFindByHostnameAndMacAddress() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      String[] macs = {"AA:BB:CC:DD:EE:FF"};
      when(endpointRepository.findByHostnameAndAtleastOneMacAddress("host1", macs))
          .thenReturn(List.of(ep));

      // -------- Act --------
      List<Endpoint> result =
          endpointService.findEndpointByHostnameAndAtLeastOneMacAddress("host1", macs);

      // -------- Assert --------
      assertEquals(1, result.size());
    }

    @Test
    void shouldFindByExternalReference() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      when(endpointRepository.findByExternalReference("ext-ref")).thenReturn(List.of(ep));

      // -------- Act --------
      Optional<Endpoint> result = endpointService.findEndpointByExternalReference("ext-ref");

      // -------- Assert --------
      assertTrue(result.isPresent());
    }

    @Test
    void shouldFindByMacAddress() {
      // -------- Prepare --------
      Endpoint ep = new Endpoint();
      String[] macs = {"AA:BB:CC:DD:EE:FF"};
      when(endpointRepository.findByAtleastOneMacAddress(macs)).thenReturn(List.of(ep));

      // -------- Act --------
      Optional<Endpoint> result = endpointService.findEndpointByAtLeastOneMacAddress(macs);

      // -------- Assert --------
      assertTrue(result.isPresent());
    }

    @Test
    void shouldReturnEmptyOptional_whenNoMacAddressMatch() {
      // -------- Prepare --------
      String[] macs = {"00:00:00:00:00:00"};
      when(endpointRepository.findByAtleastOneMacAddress(macs)).thenReturn(Collections.emptyList());

      // -------- Act --------
      Optional<Endpoint> result = endpointService.findEndpointByAtLeastOneMacAddress(macs);

      // -------- Assert --------
      assertTrue(result.isEmpty());
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
      when(endpointRepository.findById("ep-1")).thenReturn(Optional.of(existing));

      EndpointInput input = new EndpointInput();
      input.setName("updated-endpoint");
      input.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
      input.setArch(Endpoint.PLATFORM_ARCH.x86_64);
      input.setHostname("host-updated");
      input.setEol(false);
      input.setTagIds(List.of());
      when(tagRepository.findAllById(List.of())).thenReturn(Collections.emptyList());
      when(endpointRepository.save(any(Endpoint.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Endpoint result = endpointService.updateEndpoint("ep-1", input);

      // -------- Assert --------
      assertNotNull(result);
      assertFalse(result.isEoL());
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
