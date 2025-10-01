package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(Executable.EXECUTABLE_TYPE)
@EntityListeners(ModelBaseListener.class)
public class Executable extends Payload {

  public static final String EXECUTABLE_TYPE = "Executable";

  @JsonProperty("payload_type")
  private String type = EXECUTABLE_TYPE;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "executable_file")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("executable_file")
  @Schema(type = "string")
  @NotNull
  private Document executableFile;

  public Executable() {}

  public Executable(String id, String type, String name) {
    super(id, type, name);
  }

  @Override
  @JsonIgnore
  public Optional<Document> getAttachedDocument() {
    return Optional.of(this.getExecutableFile());
  }
}
