package io.openaev.integration.local_fixtures.regular;

import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.configuration.IntegrationConfigKey;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class TestIntegrationConfiguration extends BaseIntegrationConfiguration {
  public static String TEST_INTEGRATION_ID = "e7ca035e-b3d7-4a39-ac8d-9ec982a6df38";

  @Override
  public boolean isEnable() {
    return true;
  }

  @IntegrationConfigKey(key = "TEST_INTEGRATION_ID")
  private final String id = TEST_INTEGRATION_ID;
}
