package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SignatureOutputProcessor extends AbstractOutputProcessor {

  protected SignatureOutputProcessor() {
    super(ContractOutputType.ExpectationSignature, ContractOutputTechnicalType.Object, List.of());
  }

  @Override
  public void process(
      ExecutionProcessingContext ctx,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode) {
    // Stub processor
  }
}
