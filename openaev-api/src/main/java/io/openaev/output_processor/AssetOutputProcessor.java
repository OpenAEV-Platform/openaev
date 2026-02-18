package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AssetOutputProcessor extends AbstractOutputProcessor {

  public AssetOutputProcessor() {
    super(ContractOutputType.Asset, ContractOutputTechnicalType.Object, List.of(), false);
  }

  @Override
  public void process(
      ExecutionProcessingContext ctx,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode) {
    // The current implementation of the AssetOutputProcessor does not generate findings, but it can
    // be extended in the future to do so if needed.
    // For now, it simply validates the input and does not perform any additional processing.
  }
}
