package io.openaev.healthcheck.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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
    NMAP,
    NUCLEI,
  }

  @Schema(description = "Type of the check, could be a service, an attribute, etc")
  @JsonProperty("type")
  @NotNull
  private Type type;

  @Schema(description = "Detail of the check failure")
  @JsonProperty("detail")
  @NotNull
  private Detail detail;

  @Schema(description = "Define if it's an error or a warning")
  @JsonProperty("status")
  @NotNull
  private Status status;

  @Schema(description = "Date when the failure have been found")
  @JsonProperty("creation_date")
  @NotNull
  private Instant creationDate;
}
