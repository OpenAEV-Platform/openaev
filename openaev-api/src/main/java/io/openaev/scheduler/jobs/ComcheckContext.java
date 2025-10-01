package io.openaev.scheduler.jobs;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ComcheckContext {

  private String url;

  @Override
  public String toString() {
    return url;
  }
}
