package io.openaev.executors.mde.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** One row of an MDE Advanced Hunting {@code DeviceInfo} query: latest activity per device. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdeDeviceActivity {

  @JsonProperty("DeviceId")
  private String deviceId;

  @JsonProperty("LastSeen")
  private String lastSeen;
}
