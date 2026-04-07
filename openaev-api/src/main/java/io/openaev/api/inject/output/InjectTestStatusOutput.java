package io.openaev.api.inject.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.atomic_testing.form.InjectStatusOutput;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@SuperBuilder
public class InjectTestStatusOutput extends InjectStatusOutput {

  @JsonProperty("inject_id")
  @NotNull
  private String injectId;

  @JsonProperty("inject_title")
  @NotNull
  private String injectTitle;

  @JsonProperty("inject_type")
  private String injectType;
}
