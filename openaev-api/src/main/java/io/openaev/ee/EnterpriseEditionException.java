package io.openaev.ee;

import lombok.Getter;

@Getter
public class EnterpriseEditionException extends RuntimeException {

  public EnterpriseEditionException() {
    super();
  }

  public EnterpriseEditionException(String errorMessage) {
    super(errorMessage);
  }
}
