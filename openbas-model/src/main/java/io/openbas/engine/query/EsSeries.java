package io.openbas.engine.query;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EsSeries {
  private String label;
  private String color;
  private List<EsSeriesData> data = new ArrayList<>();

  public EsSeries(String label) {
    this.label = label;
  }

  public EsSeries(String label, List<EsSeriesData> data) {
    this.label = label;
    this.data = data;
  }
}
