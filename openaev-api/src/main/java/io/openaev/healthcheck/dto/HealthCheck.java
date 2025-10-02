package io.openaev.healthcheck.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class HealthCheck {

  public enum Status {
    ERROR,
    WARNING,
  }

  public enum Detail {
    SERVICE_UNAVAILABLE,
    NOT_READY,
    EMPTY,
  }

  public enum Type {
    SMTP,
    IMAP,
    AGENT_OR_EXECUTOR,
    SECURITY_SYSTEM_COLLECTOR,
    INJECT,
    TEAMS,
  }

  @Schema(description = "Type of the check, could be a service, an attribute, etc")
  @JsonProperty("type")
  private Type type;

  @Schema(description = "Detail of the check failure")
  @JsonProperty("detail")
  private Detail detail;

  @Schema(description = "Define if it's an error or a warning")
  @JsonProperty("status")
  private Status status;

  @Schema(description = "Date when the failure have been found")
  @JsonProperty("creation_date")
  private Date creationDate;
}
