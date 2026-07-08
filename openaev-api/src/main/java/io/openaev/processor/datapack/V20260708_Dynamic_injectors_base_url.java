package io.openaev.processor.datapack;

import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.service.DataPackService;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V20260708_Dynamic_injectors_base_url extends DataPack {

  private final InjectorRepository injectorRepository;

  @Value("${openaev.base-url:#{null}}")
  private String baseUrl;

  public V20260708_Dynamic_injectors_base_url(
      DataPackService dataPackService, InjectorRepository injectorRepository) {
    super(dataPackService);
    this.injectorRepository = injectorRepository;
  }

  @Override
  protected boolean doProcess() {
    if (baseUrl == null || baseUrl.isBlank()) {
      return true;
    }
    String formattedBaseUrl = formatBaseUrl(baseUrl);
    if (formattedBaseUrl.isEmpty()) {
      return true;
    }

    List<Injector> injectors = injectorRepository.findAll();
    List<Injector> updatedInjectors =
        injectors.stream()
            .filter(injector -> replaceBaseUrlInInjectorCommands(injector, formattedBaseUrl))
            .toList();

    if (!updatedInjectors.isEmpty()) {
      injectorRepository.saveAll(updatedInjectors);
      log.info("Updated baseUrl placeholders in {} injectors.", updatedInjectors.size());
    }

    return true;
  }

  private boolean replaceBaseUrlInInjectorCommands(Injector injector, String formattedBaseUrl) {
    boolean executorCommandsUpdated =
        replaceBaseUrlInCommandMap(injector.getExecutorCommands(), formattedBaseUrl);
    boolean executorClearCommandsUpdated =
        replaceBaseUrlInCommandMap(injector.getExecutorClearCommands(), formattedBaseUrl);
    return executorCommandsUpdated || executorClearCommandsUpdated;
  }

  private boolean replaceBaseUrlInCommandMap(
      Map<String, String> commands, String formattedBaseUrl) {
    if (commands == null || commands.isEmpty()) {
      return false;
    }

    boolean isUpdated = false;
    for (Map.Entry<String, String> entry : commands.entrySet()) {
      String value = entry.getValue();
      if (value != null && value.contains(formattedBaseUrl)) {
        entry.setValue(value.replace(formattedBaseUrl, "#{baseUrl}"));
        isUpdated = true;
      }
    }
    return isUpdated;
  }

  private String formatBaseUrl(String value) {
    String trimmedBaseUrl = value.trim();
    if (trimmedBaseUrl.endsWith("/")) {
      return trimmedBaseUrl.substring(0, trimmedBaseUrl.length() - 1);
    }
    return trimmedBaseUrl;
  }
}
