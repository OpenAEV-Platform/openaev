package io.openaev.injectors.caldera.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Agent {

  private String paw;
  private String host;
  private String last_seen;
  private String created;
  private String platform;
  private String username;
  private String privilege;
  private String[] host_ip_addrs;
  private String exe_name;
  private List<Link> links = new ArrayList<>();
}
