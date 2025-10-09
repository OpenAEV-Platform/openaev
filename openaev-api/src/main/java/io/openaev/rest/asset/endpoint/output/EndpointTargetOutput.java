package io.openaev.rest.asset.endpoint.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@SuperBuilder
public class EndpointTargetOutput extends EndpointBaseOutput {

  @Schema(description = "Hostname")
  @JsonProperty("endpoint_hostname")
  private String hostname;

  @Schema(description = "List IPs")
  @JsonProperty("endpoint_ips")
  private Set<String> ips;

  @Schema(description = "Seen IP")
  @JsonProperty("endpoint_seen_ip")
  private String seenIp;
}
