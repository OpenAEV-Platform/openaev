package io.openaev.service;

import static io.openaev.database.model.SettingKeys.*;
import static io.openaev.executors.caldera.config.CalderaExecutorConfig.EXECUTOR_CALDERA_PUBLIC_URL;
import static io.openaev.integration.impl.executors.caldera.CalderaExecutorIntegration.CALDERA_EXECUTOR_TYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.executors.ExecutorService;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.settings.form.*;
import io.openaev.rest.settings.response.CalderaSettings;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CalderaSettingsService {
  private final ExecutorService executorService;
  private final ConnectorInstanceService connectorInstanceService;

  @Resource protected ObjectMapper mapper;

  /**
   * Get Caldera settings
   *
   * @return caldera settings
   */
  public List<CalderaSettings> getCalderaSettings() {
    return StreamSupport.stream(executorService.executors().spliterator(), false)
        .filter(executor -> CALDERA_EXECUTOR_TYPE.equals(executor.getType()))
        .map(
            executor -> {
              // Get the connector ids to get the instance
              ConnectorIds connectorIds = executorService.getExecutorRelationsId(executor.getId());

              // Getting the instance
              ConnectorInstance connectorInstance =
                  connectorInstanceService.connectorInstanceById(
                      connectorIds.getConnectorInstanceId());

              // Getting the url in the configurations of the instance
              String calderaPublicUrl =
                  connectorInstance.getConfigurations().stream()
                      .filter(
                          configuration ->
                              EXECUTOR_CALDERA_PUBLIC_URL.equals(configuration.getKey()))
                      .findFirst()
                      .orElse(
                          ConnectorInstanceConfiguration.builder()
                              .value(mapper.createObjectNode())
                              .build())
                      .getValue()
                      .asText();

              return CalderaSettings.builder()
                  .instanceId(connectorInstance.getId())
                  .executorCalderaEnable(
                      ConnectorInstance.CURRENT_STATUS_TYPE.started.equals(
                          connectorInstance.getCurrentStatus()))
                  .executorCalderaPublicUrl(calderaPublicUrl)
                  .build();
            })
        .toList();
  }
}
