package io.openaev.engine.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EsSeriesData {
  private String key;
  private String label;
  private long value;

  public EsSeriesData(String key, String label, long value) {
    this.key = key;
    this.label = label;
    this.value = value;
  }
}
