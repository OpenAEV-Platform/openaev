package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.EndpointRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetCorrelationResolver")
class AssetCorrelationResolverTest {

  private static final String TENANT_ID = "tenant-1";

  @Mock private EndpointRepository endpointRepository;
  @InjectMocks private AssetCorrelationResolver resolver;

  private Endpoint endpointWithId(String id) {
    Endpoint ep = new Endpoint();
    ep.setId(id);
    return ep;
  }

  @Nested
  @DisplayName("IPv4 resolution")
  class Ipv4Resolution {

    @Test
    @DisplayName("Unique IP match promotes to asset_id")
    void given_uniqueIpv4Match_should_promoteToAssetId() {
      String ip = "192.168.1.10";
      when(endpointRepository.findByAtLeastOneIp(new String[] {ip}, TENANT_ID))
          .thenReturn(List.of(endpointWithId("asset-123")));

      Optional<String> result = resolver.resolveAssetId(ip, TENANT_ID);

      assertThat(result).contains("asset-123");
      verify(endpointRepository).findByAtLeastOneIp(new String[] {ip}, TENANT_ID);
      verify(endpointRepository, never()).findByHostname(anyString(), anyString());
    }

    @Test
    @DisplayName("Multiple IP matches do not promote")
    void given_multipleIpv4Matches_should_returnEmpty() {
      String ip = "10.0.0.1";
      when(endpointRepository.findByAtLeastOneIp(new String[] {ip}, TENANT_ID))
          .thenReturn(List.of(endpointWithId("a1"), endpointWithId("a2")));

      Optional<String> result = resolver.resolveAssetId(ip, TENANT_ID);

      assertThat(result).isEmpty();
      verify(endpointRepository).findByAtLeastOneIp(new String[] {ip}, TENANT_ID);
      verify(endpointRepository, never()).findByHostname(anyString(), anyString());
    }

    @Test
    @DisplayName("Zero IP matches do not promote")
    void given_zeroIpv4Matches_should_returnEmpty() {
      String ip = "172.16.0.99";
      when(endpointRepository.findByAtLeastOneIp(new String[] {ip}, TENANT_ID))
          .thenReturn(Collections.emptyList());

      Optional<String> result = resolver.resolveAssetId(ip, TENANT_ID);

      assertThat(result).isEmpty();
      verify(endpointRepository).findByAtLeastOneIp(new String[] {ip}, TENANT_ID);
      verify(endpointRepository, never()).findByHostname(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("IPv6 resolution")
  class Ipv6Resolution {

    @Test
    @DisplayName("IPv6 address is discriminated and looked up via IP path")
    void given_ipv6Address_should_useIpLookup() {
      String ip = "2001:db8::1";
      when(endpointRepository.findByAtLeastOneIp(new String[] {ip}, TENANT_ID))
          .thenReturn(List.of(endpointWithId("asset-ipv6")));

      Optional<String> result = resolver.resolveAssetId(ip, TENANT_ID);

      assertThat(result).contains("asset-ipv6");
      verify(endpointRepository).findByAtLeastOneIp(new String[] {ip}, TENANT_ID);
      verify(endpointRepository, never()).findByHostname(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("Hostname resolution")
  class HostnameResolution {

    @Test
    @DisplayName("Unique hostname match promotes to asset_id")
    void given_uniqueHostnameMatch_should_promoteToAssetId() {
      String hostname = "workstation-42.corp.local";
      when(endpointRepository.findByHostname(hostname, TENANT_ID))
          .thenReturn(List.of(endpointWithId("asset-host")));

      Optional<String> result = resolver.resolveAssetId(hostname, TENANT_ID);

      assertThat(result).contains("asset-host");
      verify(endpointRepository).findByHostname(hostname, TENANT_ID);
      verify(endpointRepository, never()).findByAtLeastOneIp(any(), anyString());
    }

    @Test
    @DisplayName("Multiple hostname matches do not promote")
    void given_multipleHostnameMatches_should_returnEmpty() {
      String hostname = "shared-host";
      when(endpointRepository.findByHostname(hostname, TENANT_ID))
          .thenReturn(List.of(endpointWithId("a1"), endpointWithId("a2")));

      Optional<String> result = resolver.resolveAssetId(hostname, TENANT_ID);

      assertThat(result).isEmpty();
      verify(endpointRepository).findByHostname(hostname, TENANT_ID);
      verify(endpointRepository, never()).findByAtLeastOneIp(any(), anyString());
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("Null host returns empty")
    void given_nullHost_should_returnEmpty() {
      assertThat(resolver.resolveAssetId(null, TENANT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Blank host returns empty")
    void given_blankHost_should_returnEmpty() {
      assertThat(resolver.resolveAssetId("  ", TENANT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Null tenant returns empty")
    void given_nullTenant_should_returnEmpty() {
      assertThat(resolver.resolveAssetId("192.168.1.1", null)).isEmpty();
    }
  }
}
