package io.openaev.output_processor;

import io.openaev.database.model.ContractOutputType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OutputProcessorFactory {

  private final Map<ContractOutputType, OutputProcessor> outputProcessorHandlerMap;

  public OutputProcessorFactory(List<OutputProcessor> handlers) {
    this.outputProcessorHandlerMap =
        handlers.stream().collect(Collectors.toMap(OutputProcessor::getType, Function.identity()));
  }

  public OutputProcessor getHandler(ContractOutputType type) {
    return Optional.ofNullable(outputProcessorHandlerMap.get(type))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No handler found for type: "
                        + type
                        + ". Available types: "
                        + outputProcessorHandlerMap.keySet()));
  }
}
