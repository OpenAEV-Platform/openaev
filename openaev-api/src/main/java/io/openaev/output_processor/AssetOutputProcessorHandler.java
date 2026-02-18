package io.openaev.output_processor;

import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AssetOutputProcessorHandler extends AbstractOutputProcessorHandler {

  public AssetOutputProcessorHandler() {
    super(ContractOutputType.Asset, ContractOutputTechnicalType.Object, List.of(), false);
  }
}
