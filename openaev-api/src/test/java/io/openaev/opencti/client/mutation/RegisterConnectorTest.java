package io.openaev.opencti.client.mutation;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.opencti.client.mutations.RegisterConnector;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.utils.fixtures.opencti.ConnectorFixture;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class RegisterConnectorTest {
  @Test
  @DisplayName(
      "When RegisterConnector mutation is passed a connector, variables are correctly interpolated")
  public void whenRegisterConnectorMutationIsPassedAConnector_variablesAreCorrectlyLoaded()
      throws JsonProcessingException {
    ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();

    RegisterConnector registerConnector = new RegisterConnector(testConnector);

    // most data here is static anyway
    // but the connector ID must be interpolated
    assertThatJson(registerConnector.getVariables())
        .isEqualTo(
            """
          {
            "input": {
              "id": "%s",
              "name": "%s",
              "type": "%s",
              "scope": [%s],
              "auto": %b,
              "auto_update": %b,
              "only_contextual": %b,
              "playbook_compatible": %b,
              "listen_callback_uri": "%s"
            }
          }
          """
                .formatted(
                    testConnector.getId(),
                    testConnector.getName(),
                    testConnector.getType().toString(),
                    testConnector.getScope().stream()
                        .map("\"%s\""::formatted)
                        .collect(Collectors.joining(",")),
                    testConnector.isAuto(),
                    testConnector.isAutoUpdate(),
                    testConnector.isOnlyContextual(),
                    testConnector.isPlaybookCompatible(),
                    testConnector.getListenCallbackURI()));
  }
}
