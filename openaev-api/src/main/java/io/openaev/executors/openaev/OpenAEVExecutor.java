package io.openaev.executors.openaev;

import io.openaev.database.model.Endpoint;
import io.openaev.executors.ExecutorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class OpenAEVExecutor {

  private final ExecutorService executorService;
  public static final String OPENAEV_EXECUTOR_ID = "2f9a0936-c327-4e95-b406-d161d32a2501";
  public static final String OPENAEV_EXECUTOR_TYPE = "openaev_agent";
  public static final String OPENAEV_EXECUTOR_NAME = "OpenAEV Agent";
  public static final String OPENAEV_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/usage/openaev-agent/";
  private static final String OPENAEV_EXECUTOR_BACKGROUND_COLOR = "#001BDB";

  @PostConstruct
  public void init() {
    try {
      executorService.register(
          OPENAEV_EXECUTOR_ID,
          OPENAEV_EXECUTOR_TYPE,
          OPENAEV_EXECUTOR_NAME,
          OPENAEV_EXECUTOR_DOCUMENTATION_LINK,
          OPENAEV_EXECUTOR_BACKGROUND_COLOR,
          getClass().getResourceAsStream("/img/icon-openaev.png"),
          getClass().getResourceAsStream("/img/banner-openaev.png"),
          new String[] {
            Endpoint.PLATFORM_TYPE.Windows.name(),
            Endpoint.PLATFORM_TYPE.Linux.name(),
            Endpoint.PLATFORM_TYPE.MacOS.name()
          });
    } catch (Exception e) {
      log.error(String.format("Error creating OpenAEV executor: %s", e), e);
    }
  }
}
