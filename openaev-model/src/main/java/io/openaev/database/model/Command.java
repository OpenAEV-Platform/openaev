package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(Command.COMMAND_TYPE)
@EntityListeners(ModelBaseListener.class)
public class Command extends Payload {

  public static final String COMMAND_TYPE = "Command";

  @JsonProperty("payload_type")
  private String type = COMMAND_TYPE;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "command_executor")
  @JsonProperty("command_executor")
  @NotNull
  private String executor;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "command_content")
  @JsonProperty("command_content")
  @NotNull
  private String content;

  public Command() {}

  public Command(String id, String type, String name) {
    super(id, type, name);
  }
}
