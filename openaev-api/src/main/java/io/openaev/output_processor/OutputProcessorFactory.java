package io.openaev.output_processor;

import io.openaev.database.model.ContractOutputType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OutputProcessorFactory {

  private final Map<ContractOutputType, OutputProcessorHandler> outputProcessorHandlerMap;

  public OutputProcessorFactory(List<OutputProcessorHandler> handlers) {
    this.outputProcessorHandlerMap =
        handlers.stream()
            .collect(Collectors.toMap(OutputProcessorHandler::getType, Function.identity()));
  }

  public OutputProcessorHandler getHandler(ContractOutputType type) {
    return outputProcessorHandlerMap.get(type);
  }
}
