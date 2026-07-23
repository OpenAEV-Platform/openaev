package io.openaev.executors.mde.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdeDeviceGroup {

  private int id;
  private String name;
  private String description;
}
