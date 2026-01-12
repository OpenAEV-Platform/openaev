package io.openaev.integration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.integration.local_fixtures.TestIntegration;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utilstest.RabbitMQTestListener;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class IntegrationConnectorTest {

  @Autowired private ComponentRequestEngine componentRequestEngine;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private ObjectMapper mapper;

  @Test
  @DisplayName("When stop is requested on a running integration, it is stopped")
  public void whenStopIsRequestedOnRunningIntegration_itStops() throws Exception {
    ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
    instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.started);
    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    TestIntegration integration =
        new TestIntegration(componentRequestEngine, instance, connectorInstanceService);

    integration.currentStatus = ConnectorInstance.CURRENT_STATUS_TYPE.started;
    integration.initialise();

    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
  }

  @Test
  @DisplayName("When start is requested on a stopping integration, it is started")
  public void whenStartIsRequestedOnStoppingIntegration_itStarts() throws Exception {
    ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
    instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    TestIntegration integration =
        new TestIntegration(componentRequestEngine, instance, connectorInstanceService);

    integration.currentStatus = ConnectorInstance.CURRENT_STATUS_TYPE.stopped;
    integration.initialise();

    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);
  }

  @Test
  @DisplayName("When integration is running and hash changes, it is restarted")
  public void whenRunningAndHashChanged_itRestarts() throws Exception {
    ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
    instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.started);
    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);

    ConnectorInstanceConfiguration config = new ConnectorInstanceConfiguration();
    config.setKey("TEST_KEY");
    config.setValue(mapper.readTree("1"));

    instance.getConfigurations().add(config);

    TestIntegration integration =
        new TestIntegration(componentRequestEngine, instance, connectorInstanceService);

    integration.initialise();
    String appliedHashBefore = readAppliedHash(integration);
    assertThat(appliedHashBefore).isNotNull();

    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);

    config.setKey("TEST_KEY");
    config.setValue(mapper.readTree("2"));

    integration.initialise();

    String appliedHashAfter = readAppliedHash(integration);
    assertThat(appliedHashAfter).isNotNull();

    assertThat(appliedHashAfter).isNotEqualTo(appliedHashBefore);
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);
  }

  private String readAppliedHash(Integration integration) throws Exception {
    Field f = Integration.class.getDeclaredField("appliedHash");
    f.setAccessible(true);
    return (String) f.get(integration);
  }
}
