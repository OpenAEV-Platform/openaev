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
    /** Asset/IP-centric steps exist but the scope allowlist has no technical entry. */
    MISSING_TECHNICAL_TARGETS,
    /** Audience-centric (tabletop) steps exist but the scope allowlist has no team/player entry. */
    MISSING_AUDIENCE_TARGETS,
    /** The scope allowlist has technical entries but no step consumes them. */
    INEFFECTIVE_TECHNICAL_TARGETS,
    /** The scope allowlist has team/player entries but no step consumes them. */
    INEFFECTIVE_AUDIENCE_TARGETS,
    /** An enabled inject runs a payload version revoked in its tenant. */
    REVOKED,
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
    CREDENTIAL_REFERENCE("credential_reference"),
    PAYLOAD("payload"),
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

  @Schema(
      description =
          "Key of the injector contract field the check is about, set on a missing mandatory"
              + " field (its type can be unknown when the key is not a known check type)")
  @JsonProperty("field")
  private String field;

  public HealthCheck(Type type, Detail detail, Status status, Instant creationDate) {
    this(type, detail, status, creationDate, null);
  }

  /** A mandatory field of the injector contract that the inject leaves empty. */
  public static HealthCheck missingMandatoryField(String fieldKey, Instant creationDate) {
    return new HealthCheck(
        Type.fromValue(fieldKey), Detail.MANDATORY_CONTENT, Status.ERROR, creationDate, fieldKey);
  }
}
