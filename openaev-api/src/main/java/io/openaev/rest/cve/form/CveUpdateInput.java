package io.openaev.rest.cve.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payload to update a CVE")
public class CveUpdateInput extends CveInput {
  // Inherits all fields from CveInput
}
