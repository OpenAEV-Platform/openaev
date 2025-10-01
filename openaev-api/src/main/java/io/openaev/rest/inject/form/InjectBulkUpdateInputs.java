package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk update call for injects */
@Setter
@Getter
public class InjectBulkUpdateInputs extends InjectBulkProcessingInput {

  /** The operations to perform to update injects */
  @JsonProperty("update_operations")
  private List<InjectBulkUpdateOperation> updateOperations;
}
