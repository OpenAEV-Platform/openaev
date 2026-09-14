package io.openaev.processor.datapack;

import io.openaev.database.model.Injector;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.service.DataPackService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V20260708_Dynamic_injectors_base_url extends DataPack {

  private static final Pattern SERVER_URL_PATTERN =
      Pattern.compile("(server=\\\\?\")([^\"]*?)(\\\\?\")");
  private static final Pattern IMPLANT_API_URL_PATTERN =
      Pattern.compile("(\\\\?\")([^\"\\s]+)(/api/tenants/#\\{tenant}/implant/)");
  private static final Pattern MAX_SIZE_PATTERN =
      Pattern.compile("(max_size=\\\\?\")([^\"]*?)(\\\\?\")");
  private static final Pattern UNSECURED_CERTIFICATE_PATTERN =
      Pattern.compile("(unsecured_certificate=\\\\?\")([^\"]*?)(\\\\?\")");
  private static final Pattern WITH_PROXY_PATTERN =
      Pattern.compile("(with_proxy=\\\\?\")([^\"]*?)(\\\\?\")");

  private static final String SERVER_VAR_NAME = "#{baseUrl}";
  private static final String MAX_SIZE_VAR_NAME = "#{maxSize}";
  private static final String UNSECURED_CERTIFICATE_VAR_NAME = "#{unsecuredCertificate}";
  private static final String WITH_PROXY_VAR_NAME = "#{withProxy}";

  private final InjectorRepository injectorRepository;

  public V20260708_Dynamic_injectors_base_url(
      DataPackService dataPackService, InjectorRepository injectorRepository) {
    super(dataPackService);
    this.injectorRepository = injectorRepository;
  }

  @Override
  protected boolean doProcess(Tenant tenant) {
    List<Injector> injectors = injectorRepository.findAll();
    List<Injector> updatedInjectors =
        injectors.stream().filter(this::replaceParamsInInjectorCommands).toList();

    if (!updatedInjectors.isEmpty()) {
      injectorRepository.saveAllAndFlush(updatedInjectors);
      log.info("Updated server params in {} injectors.", updatedInjectors.size());
    }

    return true;
  }

  private boolean replaceParamsInInjectorCommands(Injector injector) {
    boolean isUpdated = false;

    Map<String, String> updatedExecutorCommands =
        replaceParamsInCommandMap(injector.getExecutorCommands());
    if (updatedExecutorCommands != null) {
      injector.setExecutorCommands(updatedExecutorCommands);
      isUpdated = true;
    }

    Map<String, String> updatedExecutorClearCommands =
        replaceParamsInCommandMap(injector.getExecutorClearCommands());
    if (updatedExecutorClearCommands != null) {
      injector.setExecutorClearCommands(updatedExecutorClearCommands);
      isUpdated = true;
    }

    return isUpdated;
  }

  private Map<String, String> replaceParamsInCommandMap(Map<String, String> commands) {
    if (commands == null || commands.isEmpty()) {
      return null;
    }

    Map<String, String> updatedCommands = new HashMap<>(commands);
    boolean isUpdated = false;
    for (Map.Entry<String, String> entry : updatedCommands.entrySet()) {
      String value = entry.getValue();
      if (value != null) {
        String updatedValue =
            SERVER_URL_PATTERN
                .matcher(value)
                .replaceAll(
                    mr -> Matcher.quoteReplacement(mr.group(1) + SERVER_VAR_NAME + mr.group(3)));
        updatedValue =
            IMPLANT_API_URL_PATTERN
                .matcher(updatedValue)
                .replaceAll(
                    mr -> Matcher.quoteReplacement(mr.group(1) + SERVER_VAR_NAME + mr.group(3)));
        updatedValue =
            MAX_SIZE_PATTERN
                .matcher(updatedValue)
                .replaceAll(
                    mr -> Matcher.quoteReplacement(mr.group(1) + MAX_SIZE_VAR_NAME + mr.group(3)));
        updatedValue =
            UNSECURED_CERTIFICATE_PATTERN
                .matcher(updatedValue)
                .replaceAll(
                    mr ->
                        Matcher.quoteReplacement(
                            mr.group(1) + UNSECURED_CERTIFICATE_VAR_NAME + mr.group(3)));
        updatedValue =
            WITH_PROXY_PATTERN
                .matcher(updatedValue)
                .replaceAll(
                    mr ->
                        Matcher.quoteReplacement(mr.group(1) + WITH_PROXY_VAR_NAME + mr.group(3)));

        if (!updatedValue.equals(value)) {
          entry.setValue(updatedValue);
          isUpdated = true;
        }
      }
    }
    return isUpdated ? updatedCommands : null;
  }
}
