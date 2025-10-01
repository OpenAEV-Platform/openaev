package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class InjectExpectationBulkUpdateInput {

  /** Map of expectation IDs to their corresponding update inputs. */
  @NotNull
  @JsonProperty("inputs")
  private Map<String, InjectExpectationUpdateInput> inputs;
}
