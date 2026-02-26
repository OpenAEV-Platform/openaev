package io.openaev.executors.model;

import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.helper.AgentHelper;
import io.openaev.utils.mapper.EndpointMapper;
import java.time.Instant;
import lombok.Data;

@Data
public class AgentRegisterInput {

  private String name;
  private String[] ips;
  private String seenIp;
  private String hostname;
  private String agentVersion;
  private Endpoint.PLATFORM_TYPE platform;
  private Endpoint.PLATFORM_ARCH arch;
  private String[] macAddresses;
  private Instant lastSeen;
  private String externalReference;
  private boolean isService;
  private boolean isElevated;
  private String executedByUser;
  private Executor executor;
  private String processName;
  private String installationMode;
  private String installationDirectory;
  private String serviceName;

  public void setMacAddresses(String[] macAddresses) {
    this.macAddresses = EndpointMapper.setMacAddresses(macAddresses);
  }

  public void setIps(String[] ips) {
    this.ips = EndpointMapper.setIps(ips);
  }

  public void setHostname(String hostname) {
    this.hostname = hostname.toLowerCase();
  }

  public boolean isActive() {
    return new AgentHelper().isAgentActiveFromLastSeen(this.getLastSeen());
  }
}
