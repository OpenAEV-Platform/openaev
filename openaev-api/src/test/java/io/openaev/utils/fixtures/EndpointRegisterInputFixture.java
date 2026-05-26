package io.openaev.utils.fixtures;

import io.openaev.database.model.Endpoint;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import java.util.List;

public class EndpointRegisterInputFixture {
  // asset input defaults
  public static final String DEFAULT_ASSET_NAME = "openaev-default-asset-name";
  public static final String DEFAULT_ASSET_DESCRIPTION = "Description of default asset for OpenAEV";
  public static final List<String> DEFAULT_ASSET_TAGS = List.of();
  public static final String DEFAULT_ASSET_EXTERNAL_REFERENCE =
      "dd96ad6f-f82e-4606-98a4-60dc862a8c99";

  // endpoint input defaults
  public static final Endpoint.PLATFORM_TYPE DEFAULT_ENDPOINT_PLATFORM =
      Endpoint.PLATFORM_TYPE.Linux;
  public static final Endpoint.PLATFORM_ARCH DEFAULT_ENDPOINT_ARCH = Endpoint.PLATFORM_ARCH.x86_64;
  public static final List<String> DEFAULT_ENDPOINT_IPS = List.of("120.0.0.1");
  public static final String DEFAULT_ENDPOINT_HOSTNAME = "openaev-default-endpoint-hostname";
  public static final String DEFAULT_ENDPOINT_AGENT_VERSION = "Testing";
  public static final List<String> DEFAULT_ENDPOINT_MACS = List.of("00:ab:ad:c0:ff:ee");
  public static final Boolean DEFAULT_ENDPOINT_EOL = false;

  // register input defaults
  public static final Boolean DEFAULT_ENDPOINT_IS_SERVICE = true;
  public static final Boolean DEFAULT_ENDPOINT_IS_ELEVATED = false;
  public static final String DEFAULT_ENDPOINT_EXECUTED_BY_USER = "openaev-root";
  // would use the AgentUtils.INSTALLATION_MODE enum but currently private and not worth exposing
  // yet
  public static final String DEFAULT_ENDPOINT_INSTALLATION_MODE = "service";
  public static final String DEFAULT_ENDPOINT_INSTALLATION_DIRECTORY =
      "local-openaev-agent-service-dir";
  public static final String DEFAULT_ENDPOINT_SERVICE_NAME = "openaev-agent-for-test-service";

  public static EndpointInput getDefaultEndpointInput() {
    EndpointInput input = new EndpointInput();
    // asset input parts
    input.setExternalReference(DEFAULT_ASSET_EXTERNAL_REFERENCE);
    input.setName(DEFAULT_ASSET_NAME);
    input.setDescription(DEFAULT_ASSET_DESCRIPTION);
    input.setTagIds(DEFAULT_ASSET_TAGS);

    // endpoint input parts
    input.setPlatform(DEFAULT_ENDPOINT_PLATFORM);
    input.setArch(DEFAULT_ENDPOINT_ARCH);
    input.setHostname(DEFAULT_ENDPOINT_HOSTNAME);
    input.setAgentVersion(DEFAULT_ENDPOINT_AGENT_VERSION);
    input.setIps(DEFAULT_ENDPOINT_IPS.toArray(new String[0]));
    input.setMacAddresses(DEFAULT_ENDPOINT_MACS.toArray(new String[0]));
    input.setEol(DEFAULT_ENDPOINT_EOL);
    return input;
  }

  public static EndpointRegisterInput getDefaultEndpointRegisterInput() {
    EndpointRegisterInput input = EndpointRegisterInput.from(getDefaultEndpointInput());
    input.setElevated(DEFAULT_ENDPOINT_IS_ELEVATED);
    input.setService(DEFAULT_ENDPOINT_IS_SERVICE);
    input.setExternalReference(DEFAULT_ASSET_EXTERNAL_REFERENCE); // repeated in subclass
    input.setExecutedByUser(DEFAULT_ENDPOINT_EXECUTED_BY_USER);
    input.setInstallationMode(DEFAULT_ENDPOINT_INSTALLATION_MODE);
    input.setInstallationDirectory(DEFAULT_ENDPOINT_INSTALLATION_DIRECTORY);
    input.setServiceName(DEFAULT_ENDPOINT_SERVICE_NAME);
    return input;
  }
}
