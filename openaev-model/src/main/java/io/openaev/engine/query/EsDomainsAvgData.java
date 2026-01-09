package io.openaev.engine.query;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EsDomainsAvgData {

  private String label;
  private List<EsSeries> data = new ArrayList<>();
}
