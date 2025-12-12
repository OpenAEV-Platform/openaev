package io.openaev.engine.query;

import io.openaev.database.model.InjectExpectation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EsExpectationsAvgData {

  private InjectExpectation.EXPECTATION_TYPE expectationType;
  private Float avg;
}
