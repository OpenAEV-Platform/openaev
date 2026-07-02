package io.openaev.executors.mde.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdeDevice {

  /** Machine ID as returned by the MDE API (40-char lowercase hex, no hyphens). */
  private String id;

  private String computerDnsName;
  private String osPlatform;
  private String osArchitecture;
  private String version;
  private String lastIpAddress;
  private String lastExternalIpAddress;
  private String rbacGroupId;
  private String rbacGroupName;
  private String healthStatus;
  private String lastSeen;
  private String agentVersion;
}
