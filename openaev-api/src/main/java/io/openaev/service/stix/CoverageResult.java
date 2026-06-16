package io.openaev.service.stix;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoverageResult {
  private String name;

  private int score;

  public CoverageResult(String name, int successRate) {
    this.name = name;
    this.score = successRate;
  }
}
