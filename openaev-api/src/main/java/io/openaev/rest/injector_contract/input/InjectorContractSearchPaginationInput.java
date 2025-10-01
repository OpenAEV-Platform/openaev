package io.openaev.rest.injector_contract.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class InjectorContractSearchPaginationInput extends SearchPaginationInput {
  @JsonProperty("include_full_details")
  private boolean includeFullDetails = true;
}
