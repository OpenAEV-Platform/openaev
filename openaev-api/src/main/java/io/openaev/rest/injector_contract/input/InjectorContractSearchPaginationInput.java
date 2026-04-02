package io.openaev.rest.injector_contract.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import lombok.*;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class InjectorContractSearchPaginationInput extends SearchPaginationInput {

  @JsonProperty("output_mode")
  private OutputMode outputMode;

  @JsonProperty("injector_contract_ids_to_ignore")
  private List<String> injectorContractIdsToIgnore;

  @JsonProperty("injector_contract_ids_to_process")
  private List<String> injectorContractIdsToProcess;

  public enum OutputMode {
    FULL,
    THREAT_ARSENAL,
    BASE
  }
}
