package io.openaev.integration.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.IntNode;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstanceInMemory;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseIntegrationConfigurationTest {

  private static class TestConfiguration extends BaseIntegrationConfiguration {
    @IntegrationConfigKey(key = "threshold_minutes")
    private Integer thresholdMinutes = 30;

    @IntegrationConfigKey(key = "name")
    private String name = "default-name";
  }

  @Test
  @DisplayName("When a key is absent from the stored instance, the Java default is preserved")
  void whenKeyAbsent_javaDefaultIsPreserved() throws Exception {
    ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
    instance.setConfigurations(Set.of());
    TestConfiguration configuration = new TestConfiguration();

    configuration.fromConnectorInstanceConfigurationSet(instance, TestConfiguration.class);

    assertThat(configuration.thresholdMinutes).isEqualTo(30);
    assertThat(configuration.name).isEqualTo("default-name");
  }

  @Test
  @DisplayName("When a key is stored, its value overrides the Java default")
  void whenKeyPresent_storedValueOverridesDefault() throws Exception {
    ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
    instance.setConfigurations(
        Set.of(
            ConnectorInstanceConfiguration.builder()
                .key("threshold_minutes")
                .value(IntNode.valueOf(5))
                .build()));
    TestConfiguration configuration = new TestConfiguration();

    configuration.fromConnectorInstanceConfigurationSet(instance, TestConfiguration.class);

    assertThat(configuration.thresholdMinutes).isEqualTo(5);
    assertThat(configuration.name).isEqualTo("default-name");
  }
}
