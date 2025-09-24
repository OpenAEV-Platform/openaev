package io.openbas.healthcheck.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class HealthCheck {

    public enum Status {
        ERROR,
        WARNING,
    }

    public enum Detail {
        MISSING_MANDATORY_PARAMETER,
        MISSING_CONNECTION,
        MISSING_EXECUTOR,
        MISSING_SECURITY_SYSTEM_COLLECTOR,
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

    private Date creationDate = new Date();
}
