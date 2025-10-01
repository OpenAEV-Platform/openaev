package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent an operation to perform on a list of injects to update them */
@Setter
@Getter
public class InjectBulkUpdateOperation {

  /** The operations to perform to update injects */
  @JsonProperty("operation")
  private InjectBulkUpdateSupportedOperations operation;

  /** The field to update in the injects */
  @JsonProperty("field")
  private InjectBulkUpdateSupportedFields field;

  /** The values involved in the update operation for given field */
  @JsonProperty("values")
  private List<String> values;
}
