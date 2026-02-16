package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AssetOutputProcessorHandler extends AbstractOutputProcessorHandler {

  public AssetOutputProcessorHandler() {
    super(ContractOutputType.Asset, ContractOutputTechnicalType.Object, List.of(), false);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode != null;
  }

  @Override
  public Asset toAsset(JsonNode jsonNode) {
    // Creation asset
    return new Asset();
  }
}
