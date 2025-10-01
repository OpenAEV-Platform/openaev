package io.openaev.executors.tanium.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaniumEndpoint {

  private String id;
  private String computerID;
  private String name;
  private String[] ipAddresses;
  private String[] macAddresses;
  private Os os;
  private Processor processor;
  private String eidLastSeen;
}
