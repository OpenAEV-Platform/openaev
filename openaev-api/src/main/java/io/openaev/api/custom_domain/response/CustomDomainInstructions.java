package io.openaev.api.custom_domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The DNS records a customer must publish to point a custom domain at the platform and prove
 * ownership: a routing record (CNAME to the platform host, or an A record to its IP) and the TXT
 * ownership challenge that gates activation.
 */
@Getter
@AllArgsConstructor
public class CustomDomainInstructions {

  @JsonProperty("hostname")
  @Schema(description = "The custom hostname these instructions apply to")
  private String hostname;

  @JsonProperty("cname_record_name")
  @Schema(description = "Name of the CNAME record to create (the custom hostname itself)")
  private String cnameRecordName;

  @JsonProperty("cname_record_value")
  @Schema(description = "Target the CNAME should point to (the platform host)")
  private String cnameRecordValue;

  @JsonProperty("txt_record_name")
  @Schema(description = "Name of the TXT ownership challenge record")
  private String txtRecordName;

  @JsonProperty("txt_record_value")
  @Schema(description = "Value the TXT ownership challenge record must carry")
  private String txtRecordValue;
}
