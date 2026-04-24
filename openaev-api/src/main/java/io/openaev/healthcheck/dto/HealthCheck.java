package io.openaev.healthcheck.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@Slf4j
public class HealthCheck {

  public enum Status {
    ERROR,
    WARNING,
  }

  public enum Detail {
    SERVICE_UNAVAILABLE,
    NOT_READY,
    EMPTY,
    MANDATORY_CONTENT,
  }

  public enum Type {
    SMTP("smtp"),
    IMAP("imap"),
    AGENT_OR_EXECUTOR("agent_or_executor"),
    SECURITY_SYSTEM_COLLECTOR("security_system_collector"),
    INJECT("inject"),
    TEAMS("teams"),
    NMAP("nmap"),
    NUCLEI("nuclei"),
    INJECTOR_CONTRACT("injector_contract"),
    ASSETS("assets"),
    ASSET_GROUPS("asset_groups"),
    SUBJECT("subject"),
    BODY("body"),
    OPTIONAL_ARGS("optional_args"),
    MESSAGE("message"),
    SCOPE_DEFINITION("scope_definition"),
    UNKNOWN("unknown");

    private final String value;

    public String getValue() {
      return value;
    }

    Type(String value) {
      this.value = value;
    }

    public static Type fromValue(String value) {
      for (Type type : Type.values()) {
        if (type.value.equalsIgnoreCase(value)) {
          return type;
        }
      }
      log.warn(String.format("Unknown HealthCheck Type: %s", value));
      return UNKNOWN;
    }
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
