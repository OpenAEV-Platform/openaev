package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.ChainingConfigurationOutput;
import io.openaev.database.model.ChainingConfiguration;
import org.springframework.stereotype.Component;

@Component
public class ChainingConfigurationMapper {

  public ChainingConfigurationOutput toOutput(ChainingConfiguration chainingConfiguration) {
    ChainingConfigurationOutput output = new ChainingConfigurationOutput();
    return output;
  }
}
