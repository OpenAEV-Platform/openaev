package io.openaev.executors.paloaltocortex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaloAltoCortexEndpoint {

  private String endpoint_id;
  private String endpoint_name;
  private String os_type;
  private String endpoint_version;
  private String public_ip; // seenIp
  private long last_seen;
  private String[] ip;
  private String[] mac_address;
}
