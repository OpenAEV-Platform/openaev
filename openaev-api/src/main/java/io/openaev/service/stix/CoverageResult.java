package io.openaev.service.stix;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoverageResult {
  private String name;

  private double score;

  public CoverageResult(String name, double successRate) {
    this.name = name;
    this.score = successRate;
  }
}
