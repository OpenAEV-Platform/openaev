package io.openbas.rest.inject.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openbas.rest.atomic_testing.form.InjectExecutionOutput;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@SuperBuilder
public class InjectTestExecutionOutput extends InjectExecutionOutput {

  @JsonProperty("inject_id")
  @NotNull
  private String injectId;

  @JsonProperty("inject_title")
  @NotNull
  private String injectTitle;

  @JsonProperty("inject_type")
  private String injectType;
}
