package io.openaev.executors.sentinelone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SentinelOneAgent {

  private String accountId;
  private String accountName;
  private String siteId;
  private String siteName;
  private String groupId;
  private String groupName;

  private String uuid;
  private String computerName;
  private String osType;
  private String osArch;
  private String agentVersion;
  private String externalIp; // seenIp
  private String lastActiveDate;
  private List<SentinelOneNetwork> networkInterfaces;
}
