package io.openbas.healthcheck.dto;

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

  private Type type;

  private Detail detail;

  private Status status;

  private Date creationDate;
}
