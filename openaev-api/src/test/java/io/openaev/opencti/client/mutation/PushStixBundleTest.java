package io.openaev.opencti.client.mutation;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.opencti.client.mutations.PushStixBundle;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.types.Identifier;
import io.openaev.utils.fixtures.opencti.ConnectorFixture;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class PushStixBundleTest {
  @Autowired private ObjectMapper mapper;

  @Test
  @DisplayName(
      "When PushStixBundle mutation is passed a connector and bundle, variables are correctly interpolated")
  public void whenPushStixBundleMutationIsPassedAConnectorAndBundle_variablesAreCorrectlyLoaded()
      throws JsonProcessingException {
    ConnectorBase testConnector = ConnectorFixture.getDefaultConnector();
    Bundle bundle = new Bundle(new Identifier("bundle"), List.of());

    PushStixBundle pushStixBundle = new PushStixBundle(testConnector, bundle.toStix(mapper));

    assertThatJson(pushStixBundle.getVariables())
        .isEqualTo(
            """
          {
            "connectorId": "%s",
            "bundle": %s,
            "work_id": null
          }
          """
                .formatted(
                    testConnector.getId(),
                    mapper.valueToTree(bundle.toStix(mapper).toString()).toString()));
  }
}
