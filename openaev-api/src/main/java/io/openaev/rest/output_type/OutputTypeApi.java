package io.openaev.rest.output_type;

import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.output_processor.OutputProcessor;
import io.openaev.output_processor.OutputProcessorFactory;
import io.openaev.rest.helper.RestBehavior;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OutputTypeApi extends RestBehavior {

  public static final String OUTPUT_TYPE_URI = "/api/output_types";

  private final OutputProcessorFactory outputProcessorFactory;

  @GetMapping(OUTPUT_TYPE_URI)
  @Operation(
      summary = "Get all output types with their sub-fields",
      description =
          "Returns the catalog of all ContractOutputType values with their technical type and"
              + " sub-fields. Used by the chaining UI to build data_source bindings.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "List of output types with fields")
      })
  public List<OutputTypeDescriptor> outputTypes() {
    Map<ContractOutputType, OutputProcessor> handlers =
        outputProcessorFactory.getAllHandlers();

    return handlers.entrySet().stream()
        .sorted(Comparator.comparing(e -> e.getKey().getLabel()))
        .map(
            entry -> {
              OutputProcessor processor = entry.getValue();
              List<OutputFieldDescriptor> fields =
                  processor.getFields().stream()
                      .map(
                          f ->
                              new OutputFieldDescriptor(
                                  f.getKey(), f.getType(), f.isRequired()))
                      .toList();
              return new OutputTypeDescriptor(
                  entry.getKey(),
                  processor.getTechnicalType(),
                  processor.isFindingCompatible(),
                  fields);
            })
        .toList();
  }

  public record OutputFieldDescriptor(
      String key, ContractOutputTechnicalType type, boolean required) {}

  public record OutputTypeDescriptor(
      ContractOutputType outputType,
      ContractOutputTechnicalType technicalType,
      boolean findingCompatible,
      List<OutputFieldDescriptor> fields) {}
}
