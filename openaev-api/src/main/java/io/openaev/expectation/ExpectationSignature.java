package io.openaev.expectation;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExpectationSignature {

  private String type;

  private String value;
}
