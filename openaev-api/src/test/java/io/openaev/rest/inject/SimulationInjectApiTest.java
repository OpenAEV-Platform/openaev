package io.openaev.rest.inject;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.rest.inject.form.DirectInjectInput;
import io.openaev.service.RabbitmqService;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Simulation Inject API — Direct Execute Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SimulationInjectApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  @MockitoSpyBean private RabbitmqService rabbitmqService;

  private Exercise exercise;
  private Injector externalInjector;
  private InjectorContract injectorContract;

  @BeforeEach
  void setUp() throws Exception {
    // Arrange — Exercise
    exercise =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();

    // Arrange — External injector with a contract
    externalInjector =
        InjectorFixture.createInjector("ext-injector-id", "External Injector", "external_type");
    externalInjector.setExternal(true);

    injectorContract = InjectorContractFixture.createDefaultInjectorContract();
    injectorContract.getInjectors().clear();
    injectorContract.addInjector(externalInjector);

    injectorContractComposer.forInjectorContract(injectorContract).persist();

    // Arrange — Catalog connector + started connector instance with INJECTOR_ID config
    CatalogConnector catalogConnector =
        catalogConnectorComposer
            .forCatalogConnector(
                createDefaultCatalogConnectorManagedByXtmComposer(
                    "External Injector", ConnectorType.INJECTOR))
            .persist()
            .get();

    ConnectorInstanceConfiguration injectorIdConfig =
        createConnectorInstanceConfiguration("INJECTOR_ID", externalInjector.getId());

    ConnectorInstancePersisted connectorInstance = new ConnectorInstancePersisted();
    connectorInstance.setSource(ConnectorInstance.SOURCE.CATALOG_DEPLOYMENT);
    connectorInstance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.started);
    connectorInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);

    connectorInstanceComposer
        .forConnectorInstance(connectorInstance)
        .withCatalogConnector(catalogConnectorComposer.forCatalogConnector(catalogConnector))
        .withConnectorInstanceConfiguration(
            connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                injectorIdConfig))
        .persist();
  }

  @AfterEach
  void tearDown() {
    exerciseComposer.reset();
    injectorContractComposer.reset();
    catalogConnectorComposer.reset();
    connectorInstanceComposer.reset();
    connectorInstanceConfigurationComposer.reset();
  }

  @Test
  @DisplayName(
      "Given an external injector, direct execute should publish the inject to the RabbitMQ queue")
  void given_externalInjector_directExecute_should_publishToQueue() throws Exception {
    // Arrange
    doNothing().when(rabbitmqService).publish(any(String.class), any(String.class));

    DirectInjectInput input = new DirectInjectInput();
    input.setTitle("Test direct inject");
    input.setDescription("A direct inject for testing external execution");
    input.setInjectorContract(injectorContract.getId());
    input.setInjectorId(externalInjector.getId());
    input.setContent(new ObjectMapper().createObjectNode());
    input.setUserIds(List.of());

    MockMultipartFile inputPart =
        new MockMultipartFile(
            "input", "", MediaType.APPLICATION_JSON_VALUE, asJsonString(input).getBytes());
    MockMultipartFile filePart =
        new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

    // Act
    String response =
        mvc.perform(
                MockMvcRequestBuilders.multipart(EXERCISE_URI + "/" + exercise.getId() + "/inject")
                    .file(inputPart)
                    .file(filePart)
                    .with(csrf())
                    .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert — response contains an inject status
    assertThat(response).isNotEmpty();

    // Assert — rabbitmqService.publish was called with the injector ID as routing key
    verify(rabbitmqService).publish(eq(externalInjector.getId()), any(String.class));
  }
}
