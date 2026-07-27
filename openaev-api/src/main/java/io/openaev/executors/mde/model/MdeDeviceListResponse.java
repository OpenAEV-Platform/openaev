package io.openaev.executors.mde.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdeDeviceListResponse {

  private List<MdeDevice> value;
}
