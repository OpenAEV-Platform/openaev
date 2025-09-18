package io.openbas.rest;

import static io.openbas.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static io.openbas.utils.fixtures.AgentFixture.createAgent;
import static io.openbas.utils.fixtures.EndpointFixture.*;
import static io.openbas.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openbas.utils.fixtures.TagFixture.getTag;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.repository.EndpointRepository;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.repository.TagRepository;
import io.openbas.rest.asset.endpoint.form.EndpointInput;
import io.openbas.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openbas.rest.exercise.service.ExerciseService;
import io.openbas.service.EndpointService;
import io.openbas.utils.fixtures.ExerciseFixture;
import io.openbas.utils.mapper.EndpointMapper;
import io.openbas.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class EndpointApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TagRepository tagRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseService exerciseService;

  @SpyBean private EndpointService endpointService;

  @DisplayName("Given valid input, should create an endpoint agentless successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validInput_should_createEndpointAgentlessSuccessfully() throws Exception {
    // --PREPARE--
    Endpoint endpointInput = createEndpoint();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/agentless")
                    .content(asJsonString(endpointInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT
    assertThatJson(response).node("asset_name").isEqualTo(endpointInput.getName());
    assertThatJson(response).node("asset_description").isEqualTo(endpointInput.getDescription());
    assertThatJson(response).node("endpoint_hostname").isEqualTo(endpointInput.getHostname());
    assertThatJson(response).node("endpoint_platform").isEqualTo(endpointInput.getPlatform());
    assertThatJson(response).node("endpoint_arch").isEqualTo(endpointInput.getArch());
    assertThatJson(response).node("endpoint_ips").isEqualTo(endpointInput.getIps());
    assertThatJson(response).node("endpoint_ips").isEqualTo(endpointInput.getIps());
    assertThatJson(response).node("asset_tags").isEqualTo(endpointInput.getTags());
    assertThatJson(response).node("asset_agents").isEqualTo(endpointInput.getAgents());
  }

  @DisplayName("Given wrong input, can't create an endpoint agentless successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_wrongInput_cant_createEndpointAgentlessSuccessfully() throws Exception {
    // --PREPARE--
    Endpoint endpointInput = new Endpoint();
    endpointInput.setHostname("Missing attributes for this endpoint");

    // --EXECUTE--
    mvc.perform(
            post(ENDPOINT_URI + "/agentless")
                .content(asJsonString(endpointInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError());
  }

  @DisplayName("Given valid endpoint input, should upsert an endpoint successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validEndpointInput_should_upsertEndpointSuccessfully() throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTag());
    String externalReference = "external01";
    EndpointRegisterInput registerInput =
        createWindowsEndpointRegisterInput(List.of(tag.getId()), externalReference);
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(registerInput);
    endpoint.setIps(EndpointMapper.setIps(registerInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(registerInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(
        new ArrayList<>() {
          {
            add(agent);
          }
        });
    endpointRepository.save(endpoint);

    String newName = "New hostname";
    registerInput.setHostname(newName);

    Mockito.doReturn("command")
        .when(endpointService)
        .generateUpgradeCommand(String.valueOf(Endpoint.PLATFORM_TYPE.Windows), null, null, null);

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/register")
                    .content(asJsonString(registerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(newName.toLowerCase(), JsonPath.read(response, "$.endpoint_hostname"));
  }

  @DisplayName(
      "Given valid input for a non-existing endpoint, should create and upsert successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validInputForNonExistingEndpoint_should_createAndUpsertSuccessfully()
      throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTag());
    String externalReference = "external01";
    EndpointRegisterInput registerInput =
        createWindowsEndpointRegisterInput(List.of(tag.getId()), externalReference);
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(registerInput);
    endpoint.setIps(EndpointMapper.setIps(registerInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(registerInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(List.of(agent));

    Mockito.doReturn("command")
        .when(endpointService)
        .generateUpgradeCommand(String.valueOf(Endpoint.PLATFORM_TYPE.Windows), null, null, null);

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/register")
                    .content(asJsonString(registerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(WINDOWS_ASSET_NAME_INPUT, JsonPath.read(response, "$.asset_name"));
  }

  @DisplayName("Given valid input, should update an endpoint successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validInput_should_updateEndpointSuccessfully() throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTag());
    String externalReference = "external01";
    EndpointInput endpointInput = createWindowsEndpointInput(List.of(tag.getId()));
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(endpointInput);
    endpoint.setIps(EndpointMapper.setIps(endpointInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(endpointInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(
        new ArrayList<>() {
          {
            add(agent);
          }
        });
    Endpoint endpointCreated = endpointRepository.save(endpoint);

    EndpointInput updateInput = new EndpointInput();
    String newName = "New hostname";
    updateInput.setName(newName);
    updateInput.setHostname(newName);
    updateInput.setIps(endpointInput.getIps());
    updateInput.setPlatform(endpointInput.getPlatform());
    updateInput.setArch(endpointInput.getArch());

    // --EXECUTE--
    String response =
        mvc.perform(
                put(ENDPOINT_URI + "/" + endpointCreated.getId())
                    .content(asJsonString(updateInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT
    assertThatJson(response).node("asset_name").isEqualTo(newName);
    assertThatJson(response).node("endpoint_hostname").isEqualTo(newName.toLowerCase());
    assertThatJson(response).node("endpoint_platform").isEqualTo(endpointCreated.getPlatform());
    assertThatJson(response).node("endpoint_ips").isEqualTo(endpointCreated.getIps());
  }

  @DisplayName("Given valid input, should delete an endpoint successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validInput_should_deleteEndpointSuccessfully() throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTag());
    String externalReference = "external01";
    EndpointInput endpointInput = createWindowsEndpointInput(List.of(tag.getId()));
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(endpointInput);
    endpoint.setIps(EndpointMapper.setIps(endpointInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(endpointInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(
        new ArrayList<>() {
          {
            add(agent);
          }
        });
    Endpoint endpointCreated = endpointRepository.save(endpoint);

    // -- EXECUTE --
    mvc.perform(
            delete(ENDPOINT_URI + "/" + endpointCreated.getId()).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    // -- ASSERT --
    mvc.perform(
            get(ENDPOINT_URI + "/" + endpointCreated.getId()).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError());
  }

  // Options endpoint tests

  private Inject prepareOptionsEndpointTestData() {
    // Teams
    Endpoint e1input = createEndpoint();
    e1input.setName(WINDOWS_ASSET_NAME_INPUT + "1");
    Endpoint endpoint1 = this.endpointRepository.save(e1input);
    Endpoint e2input = createEndpoint();
    e2input.setName(WINDOWS_ASSET_NAME_INPUT + "2");
    Endpoint endpoint2 = this.endpointRepository.save(e2input);
    Endpoint e3input = createEndpoint();
    e3input.setName(WINDOWS_ASSET_NAME_INPUT + "3");
    Endpoint endpoint3 = this.endpointRepository.save(e3input);
    Endpoint e4input = createEndpoint();
    e4input.setName(WINDOWS_ASSET_NAME_INPUT + "4");
    Endpoint endpoint4 = this.endpointRepository.save(e4input);
    Exercise exInput = ExerciseFixture.getExercise();
    Exercise exercise = this.exerciseService.createExercise(exInput);
    // Inject
    Inject inject = getDefaultInject();
    inject.setExercise(exercise);
    inject.setAssets(
        new ArrayList<>() {
          {
            add(endpoint1);
            add(endpoint2);
            add(endpoint3);
            add(endpoint4);
          }
        });
    return this.injectRepository.save(inject);
  }

  Stream<Arguments> optionsByNameTestParameters() {
    return Stream.of(
        Arguments.of(
            null, false, 0), // Case 1: searchText is null and simulationOrScenarioId is null
        Arguments.of(
            WINDOWS_ASSET_NAME_INPUT,
            false,
            0), // Case 2: searchText is valid and simulationOrScenarioId is null
        Arguments.of(
            WINDOWS_ASSET_NAME_INPUT + "2",
            false,
            0), // Case 2: searchText is valid and simulationOrScenarioId is null
        Arguments.of(
            null, true, 4), // Case 3: searchText is null and simulationOrScenarioId is valid
        Arguments.of(
            WINDOWS_ASSET_NAME_INPUT,
            true,
            4), // Case 4: searchText is valid and simulationOrScenarioId is valid
        Arguments.of(
            WINDOWS_ASSET_NAME_INPUT + "2",
            true,
            1) // Case 5: searchText is valid and simulationOrScenarioId is valid
        );
  }

  @DisplayName("Test optionsByName")
  @ParameterizedTest
  @MethodSource("optionsByNameTestParameters")
  @WithMockUser(isAdmin = true)
  void optionsByNameTest(
      String searchText, Boolean simulationOrScenarioId, Integer expectedNumberOfResults)
      throws Exception {
    // --PREPARE--
    Inject i = prepareOptionsEndpointTestData();
    Exercise exercise = i.getExercise();

    // --EXECUTE--;
    String response =
        mvc.perform(
                get(ENDPOINT_URI + "/options")
                    .queryParam("searchText", searchText)
                    .queryParam(
                        "simulationOrScenarioId", simulationOrScenarioId ? exercise.getId() : null)
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }

  Stream<Arguments> optionsByIdTestParameters() {
    return Stream.of(
        Arguments.of(0, 0), // Case 1: 0 ID given
        Arguments.of(1, 1), // Case 1: 1 ID given
        Arguments.of(2, 2) // Case 2: 2 IDs given
        );
  }

  @DisplayName("Test optionsById")
  @ParameterizedTest
  @MethodSource("optionsByIdTestParameters")
  @WithMockUser(isAdmin = true)
  void optionsByIdTest(Integer numberOfAssetToProvide, Integer expectedNumberOfResults)
      throws Exception {
    // --PREPARE--
    Inject inject = prepareOptionsEndpointTestData();
    List<Asset> assets = inject.getAssets();

    List<String> idsToSearch = new ArrayList<>();
    for (int i = 0; i < numberOfAssetToProvide; i++) {
      idsToSearch.add(assets.get(i).getId());
    }

    // --EXECUTE--;
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/options")
                    .content(asJsonString(idsToSearch))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }
}
