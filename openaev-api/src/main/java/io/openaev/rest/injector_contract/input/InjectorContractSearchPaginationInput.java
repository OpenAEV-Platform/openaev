package io.openaev.rest.injector_contract.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.specification.InjectorContractSpecification;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.*;
import org.springframework.data.jpa.domain.Specification;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class InjectorContractSearchPaginationInput extends SearchPaginationInput {

  @Schema(description = "Allow the return of a full object if true, partial object if false")
  @JsonProperty("include_full_details")
  private boolean includeFullDetails = true;

  @Schema(
      description = "Include the injector contract content on the returned object if set to true")
  @JsonProperty("include_content_details")
  private boolean includeContentDetails = false;

  @Schema(
      description =
          "Return only the actions that can be picked for an inject: payload-less actions and"
              + " actions whose payload is approved")
  @JsonProperty("approved_payloads_only")
  private boolean approvedPayloadsOnly = false;

  @Schema(description = "List of all the ids to ignore on the search")
  @JsonProperty("injector_contract_ids_to_ignore")
  private List<String> injectorContractIdsToIgnore;

  @Schema(description = "List of all the ids to include on the search")
  @JsonProperty("injector_contract_ids_to_process")
  private List<String> injectorContractIdsToProcess;

  /** Applies {@link #approvedPayloadsOnly} to a search specification. */
  public Specification<InjectorContract> restrictToPickable(
      final Specification<InjectorContract> spec) {
    return approvedPayloadsOnly
        ? spec.and(InjectorContractSpecification.withApprovedOrNoPayload())
        : spec;
  }
}
