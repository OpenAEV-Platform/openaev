package io.openaev.executors.caldera.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Agent {

  private String paw;
  private String host;
  private String last_seen;
  private String platform;
  private String username;
  private String privilege;
  private String architecture;
  private String[] host_ip_addrs;
  private String exe_name;
}
