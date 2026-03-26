package io.openaev.rest.injector_contract.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class InjectorContractSearchPaginationInput extends SearchPaginationInput {

  @JsonProperty("output_mode")
  private OutputMode outputMode;

  public enum OutputMode {
    FULL,
    THREAT_ARSENAL,
    BASE
  }
}
