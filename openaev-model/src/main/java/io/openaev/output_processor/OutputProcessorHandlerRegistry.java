package io.openaev.output_processor;

import io.openaev.database.model.ContractOutputType;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class OutputProcessorHandlerRegistry {

  private final Map<ContractOutputType, OutputProcessorHandler> cache;

  public OutputProcessorHandlerRegistry(ApplicationContext applicationContext) {
    this.cache =
        Arrays.stream(ContractOutputType.values())
            .collect(
                Collectors.toUnmodifiableMap(
                    type -> type, type -> applicationContext.getBean(type.handlerClass)));
  }

  public OutputProcessorHandler getHandler(ContractOutputType type) {
    return cache.get(type);
  }
}
