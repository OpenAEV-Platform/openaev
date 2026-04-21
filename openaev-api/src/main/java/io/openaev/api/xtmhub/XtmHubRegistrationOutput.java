package io.openaev.api.xtmhub;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public class XtmHubRegistrationOutput {

  @JsonProperty("tenant_xtmhub_registration_id")
  private String id;

  @JsonProperty("tenant_xtmhub_registration_token")
  private String token;

  @JsonProperty("tenant_xtmhub_registration_date")
  private LocalDateTime registrationDate;

  @JsonProperty("tenant_xtmhub_registration_status")
  private XtmHubRegistrationStatus registrationStatus;

  @JsonProperty("tenant_xtmhub_registration_user_id")
  private String registrationUserId;

  @JsonProperty("tenant_xtmhub_registration_user_name")
  private String registrationUserName;

  @JsonProperty("tenant_xtmhub_registration_last_connectivity_check")
  private LocalDateTime lastConnectivityCheck;
}
