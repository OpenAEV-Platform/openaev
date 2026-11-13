package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyOutputProcessor extends FindingCapableOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String KEY = "key";
  private static final String VALUE = "value";
  private static final String HOST = "host";

  public PasswordPolicyOutputProcessor(FindingService findingService) {
    super(
        ContractOutputType.PasswordPolicy,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(KEY, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(VALUE, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, false)),
        findingService);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(KEY) && jsonNode.hasNonNull(VALUE);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String key = buildString(jsonNode, KEY);
    String value = buildString(jsonNode, VALUE);
    return key + ": " + value;
  }
}
