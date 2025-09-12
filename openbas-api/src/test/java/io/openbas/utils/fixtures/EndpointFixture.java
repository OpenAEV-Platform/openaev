package io.openbas.utils.fixtures;

import io.openbas.database.model.Endpoint;
import io.openbas.rest.asset.endpoint.form.EndpointInput;
import io.openbas.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openbas.utils.mapper.EndpointMapper;
import java.time.Instant;
import java.util.List;

public class EndpointFixture {

  public static final String[] IPS = {"192.168.1.1"};
  public static final String[] MAC_ADDRESSES = {"00:1B:44:11:3A:B7"};
  public static final String WINDOWS_ASSET_NAME_INPUT = "Windows asset";
  public static final String SEEN_IP = "192.168.12.21";
  public static final String ENDPOINT_DESCRIPTION = "Endpoint description";
  public static final String WINDOWS_HOSTNAME = "Windows Hostname";

  public static EndpointInput createWindowsEndpointInput(List<String> tagIds) {
    EndpointInput input = new EndpointInput();
    input.setName(WINDOWS_ASSET_NAME_INPUT);
    input.setDescription("Description of Windows asset");
    input.setTagIds(tagIds);
    input.setIps(IPS);
    input.setHostname(WINDOWS_HOSTNAME);
    input.setAgentVersion("1.8.2");
    input.setMacAddresses(MAC_ADDRESSES);
    input.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    input.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    return input;
  }

  public static EndpointRegisterInput createWindowsEndpointRegisterInput(
      List<String> tagIds, String externalReference) {
    EndpointRegisterInput input = new EndpointRegisterInput();
    input.setName(WINDOWS_ASSET_NAME_INPUT);
    input.setDescription("Description of Windows asset");
    input.setTagIds(tagIds);
    input.setIps(IPS);
    input.setHostname(WINDOWS_HOSTNAME);
    input.setAgentVersion("1.8.2");
    input.setMacAddresses(MAC_ADDRESSES);
    input.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    input.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    input.setExternalReference(externalReference);
    return input;
  }

  public static Endpoint createEndpoint() {
    Endpoint endpoint = new Endpoint();
    endpoint.setCreatedAt(Instant.now());
    endpoint.setUpdatedAt(Instant.now());
    endpoint.setName("Endpoint test");
    endpoint.setDescription(ENDPOINT_DESCRIPTION);
    endpoint.setHostname(WINDOWS_HOSTNAME);
    endpoint.setIps(EndpointMapper.setIps(IPS));
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    endpoint.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    endpoint.setUpdatedAt(Instant.now());
    return endpoint;
  }

  public static Endpoint createEndpointWithPlatform(String name, Endpoint.PLATFORM_TYPE platform) {
    Endpoint endpoint = new Endpoint();
    endpoint.setName(name);
    endpoint.setDescription(ENDPOINT_DESCRIPTION);
    endpoint.setHostname(WINDOWS_HOSTNAME);
    endpoint.setIps(EndpointMapper.setIps(IPS));
    endpoint.setPlatform(platform);
    endpoint.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    return endpoint;
  }

  public static Endpoint createDefaultWindowsEndpointWithArch(Endpoint.PLATFORM_ARCH arch) {
    Endpoint endpoint = createEndpoint();
    endpoint.setArch(arch);
    return endpoint;
  }

  public static Endpoint createDefaultLinuxEndpointWithArch(Endpoint.PLATFORM_ARCH arch) {
    Endpoint endpoint = createEndpoint();
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setArch(arch);
    return endpoint;
  }

  public static Endpoint createEndpointOnlyWithHostname() {
    Endpoint endpoint = new Endpoint();
    endpoint.setName("Hostname");
    endpoint.setDescription(ENDPOINT_DESCRIPTION);
    endpoint.setIps(new String[0]);
    endpoint.setHostname("Linux Hostname");
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    return endpoint;
  }

  public static Endpoint createEndpointOnlyWithLocalIP() {
    Endpoint endpoint = new Endpoint();
    endpoint.setName("LocalIP");
    endpoint.setDescription(ENDPOINT_DESCRIPTION);
    endpoint.setIps(EndpointMapper.setIps(IPS));
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    return endpoint;
  }

  public static Endpoint createEndpointOnlyWithSeenIP() {
    Endpoint endpoint = new Endpoint();
    endpoint.setName("SeenIP");
    endpoint.setDescription(ENDPOINT_DESCRIPTION);
    endpoint.setIps(new String[0]);
    endpoint.setSeenIp(SEEN_IP);
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    return endpoint;
  }
}
