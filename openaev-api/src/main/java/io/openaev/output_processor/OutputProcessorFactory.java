package io.openaev.output_processor;

import io.openaev.database.model.ContractOutputType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory for retrieving {@link OutputProcessor} instances by {@link ContractOutputType}.
 *
 * <p>This factory is initialized with all available OutputProcessor beans and provides a lookup
 * method to retrieve the appropriate processor for a given output type. Throws an exception if no
 * processor is found for the requested type.
 */
@Slf4j
@Component
public class OutputProcessorFactory {

  private final Map<ContractOutputType, OutputProcessor> outputProcessorHandlerMap;

  /**
   * Constructs the factory and registers all available output processors by their type.
   *
   * @param handlers the list of available OutputProcessor beans
   */
  public OutputProcessorFactory(List<OutputProcessor> handlers) {
    this.outputProcessorHandlerMap =
        handlers.stream().collect(Collectors.toMap(OutputProcessor::getType, Function.identity()));
  }

  /**
   * Retrieves the {@link OutputProcessor} for the given output type.
   *
   * @param type the contract output type
   * @return the corresponding OutputProcessor
   * @throws IllegalArgumentException if no processor is found for the given type
   */
  public Optional<OutputProcessor> getProcessor(ContractOutputType type) {
    OutputProcessor processor = outputProcessorHandlerMap.get(type);
    if (processor == null) {
      log.warn(
          "No processor found for type: {}. Available types: {}",
          type,
          outputProcessorHandlerMap.keySet());
      return Optional.empty();
    }
    return Optional.of(processor);
  }
}
