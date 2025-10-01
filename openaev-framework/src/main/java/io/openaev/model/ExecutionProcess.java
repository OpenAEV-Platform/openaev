package io.openaev.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExecutionProcess {

  private boolean async;

  public ExecutionProcess(boolean async) {
    this.async = async;
  }
}
