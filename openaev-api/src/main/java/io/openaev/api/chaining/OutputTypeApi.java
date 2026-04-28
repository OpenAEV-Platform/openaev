package io.openaev.api.chaining;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.chaining.dto.OutputTypeDescriptorOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ResourceType;
import io.openaev.output_processor.OutputProcessor;
import io.openaev.output_processor.OutputProcessorFactory;
import io.openaev.rest.helper.RestBehavior;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "Output Types API",
    description = "Catalog of available output types and their fields for chaining")
public class OutputTypeApi extends RestBehavior {

  public static final String OUTPUT_TYPES_URI = "/api/output_types";
  public static final String TENANT_OUTPUT_TYPES_URI = TENANT_PREFIX + "/output_types";

  private final OutputProcessorFactory outputProcessorFactory;

  @Operation(
      summary = "List all output types",
      description =
          "Returns a catalog of all available output types with their fields, for use in chaining condition rules and field binding.")
  @ApiResponse(responseCode = "200", description = "Output types catalog retrieved successfully")
  @GetMapping({OUTPUT_TYPES_URI, TENANT_OUTPUT_TYPES_URI})
  @AccessControl(
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  public List<OutputTypeDescriptorOutput> listOutputTypes() {
    return outputProcessorFactory.getAllProcessors().stream()
        .map(this::toDescriptor)
        .toList();
  }

  private OutputTypeDescriptorOutput toDescriptor(OutputProcessor processor) {
    return OutputTypeDescriptorOutput.builder()
        .type(processor.getType().getLabel())
        .label(processor.getType().getLabel())
        .fields(
            processor.getFields().stream()
                .map(this::toFieldDescriptor)
                .toList())
        .build();
  }

  private OutputTypeDescriptorOutput.FieldDescriptor toFieldDescriptor(ContractOutputField field) {
    return OutputTypeDescriptorOutput.FieldDescriptor.builder()
        .key(field.getKey())
        .label(field.getKey())
        .type(field.getType().label)
        .build();
  }
}
