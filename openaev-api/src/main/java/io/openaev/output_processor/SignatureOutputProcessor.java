package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
public class SignatureOutputProcessor extends AbstractOutputProcessor {

  protected SignatureOutputProcessor() {
    super(ContractOutputType.Signature, ContractOutputTechnicalType.Object, new ArrayList<>());
  }

  @Override
  public void process(
      ExecutionProcessingContext ctx,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode) {
    // Stub processor
  }
}
