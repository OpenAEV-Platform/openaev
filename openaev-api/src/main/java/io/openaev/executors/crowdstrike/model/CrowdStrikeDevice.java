package io.openaev.executors.crowdstrike.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrowdStrikeDevice {

  private String device_id;
  private String hostname;
  private String platform_name;
  private String os_version;
  private String external_ip;
  private String local_ip;
  private String connection_ip;
  private String mac_address;
  private String os_product_name;
  private String last_seen;
}
