package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AssetOutputProcessorHandler extends AbstractStructuredOutputProcessorHandler
    implements AssetCapable {

  public AssetOutputProcessorHandler() {
    super(
        "asset",
        ContractOutputType.Asset,
        ContractOutputTechnicalType.Object,
        Set.of(),
        false,
        Set.of(ProcessingContext.ASSET));
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode != null;
  }

  @Override
  public Asset toAsset(JsonNode jsonNode) {
    return new Asset();
  }
}
