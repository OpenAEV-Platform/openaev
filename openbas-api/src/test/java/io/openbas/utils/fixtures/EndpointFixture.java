package io.openbas.utils.fixtures;

import io.openbas.database.model.Endpoint;
import io.openbas.rest.asset.endpoint.form.EndpointInput;
import io.openbas.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openbas.utils.mapper.EndpointMapper;
import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.util.Strings;

public class EndpointFixture {

  public static final String[] IPS = {"192.168.1.1"};
  public static final String[] MAC_ADDRESSES = {"00:1B:44:11:3A:B7"};
  public static final String WINDOWS_ASSET_NAME_INPUT = "Windows asset";
  public static final String SEEN_IP = "192.168.12.21";
  public static final String ENDPOINT_DESCRIPTION = "Endpoint description";
  public static final String WINDOWS_HOSTNAME = "Windows Hostname";
  private static final String[] NO_LOCAL_IPS = new String[0];

  private static EndpointInput baseEndpointInput(List<String> tagIds) {
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

  public static EndpointInput createWindowsEndpointInput(List<String> tagIds) {
    return baseEndpointInput(tagIds);
  }

  public static EndpointRegisterInput createWindowsEndpointRegisterInput(
      List<String> tagIds, String externalReference) {
    EndpointRegisterInput input = new EndpointRegisterInput();
    // copy shared fields from base
    EndpointInput base = baseEndpointInput(tagIds);
    input.setName(base.getName());
    input.setDescription(base.getDescription());
    input.setTagIds(base.getTagIds());
    input.setIps(base.getIps());
    input.setHostname(base.getHostname());
    input.setAgentVersion(base.getAgentVersion());
    input.setMacAddresses(base.getMacAddresses());
    input.setPlatform(base.getPlatform());
    input.setArch(base.getArch());

    // specific field
    input.setExternalReference(externalReference);
    return input;
  }

  private static Endpoint baseEndpoint(String name, Endpoint.PLATFORM_TYPE platform) {
    Endpoint endpoint = new Endpoint();
    endpoint.setCreatedAt(Instant.now());
    endpoint.setUpdatedAt(Instant.now());
    endpoint.setName(name);
    endpoint.setDescription(ENDPOINT_DESCRIPTION);
    endpoint.setHostname(WINDOWS_HOSTNAME);
    endpoint.setIps(EndpointMapper.setIps(IPS));
    endpoint.setPlatform(platform);
    endpoint.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    return endpoint;
  }

  public static Endpoint createEndpoint() {
    return baseEndpoint("Endpoint test", Endpoint.PLATFORM_TYPE.Windows);
  }

  public static Endpoint createEndpointWithPlatform(String name, Endpoint.PLATFORM_TYPE platform) {
    return baseEndpoint(name, platform);
  }

  public static Endpoint createDefaultWindowsEndpointWithArch(Endpoint.PLATFORM_ARCH arch) {
    Endpoint endpoint = baseEndpoint("Endpoint test", Endpoint.PLATFORM_TYPE.Windows);
    endpoint.setArch(arch);
    return endpoint;
  }

  public static Endpoint createDefaultLinuxEndpointWithArch(Endpoint.PLATFORM_ARCH arch) {
    Endpoint endpoint = baseEndpoint("Endpoint test", Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setArch(arch);
    return endpoint;
  }

  public static Endpoint createEndpointOnlyWithHostname() {
    Endpoint endpoint = baseEndpoint("Hostname", Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setIps(NO_LOCAL_IPS);
    endpoint.setHostname("Linux Hostname");
    endpoint.setSeenIp(Strings.EMPTY);
    return endpoint;
  }

  public static Endpoint createEndpointOnlyWithLocalIP() {
    Endpoint endpoint = baseEndpoint("LocalIP", Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setIps(EndpointMapper.setIps(IPS));
    endpoint.setHostname(Strings.EMPTY);
    endpoint.setSeenIp(Strings.EMPTY);
    return endpoint;
  }

  public static Endpoint createEndpointOnlyWithSeenIP() {
    Endpoint endpoint = baseEndpoint("SeenIP", Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setIps(NO_LOCAL_IPS);
    endpoint.setSeenIp(SEEN_IP);
    endpoint.setHostname(Strings.EMPTY);
    return endpoint;
  }

  public static Endpoint createEndpointNotTargetProperty() {
    Endpoint endpoint = baseEndpoint("No target Property", Endpoint.PLATFORM_TYPE.Linux);
    endpoint.setIps(NO_LOCAL_IPS);
    endpoint.setHostname(Strings.EMPTY);
    endpoint.setSeenIp(Strings.EMPTY);
    return endpoint;
  }
}
