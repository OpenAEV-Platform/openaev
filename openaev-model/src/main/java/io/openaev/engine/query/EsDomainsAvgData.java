package io.openaev.engine.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EsDomainsAvgData {

  private String key;
  private String domain;
  private Float avg;
}
