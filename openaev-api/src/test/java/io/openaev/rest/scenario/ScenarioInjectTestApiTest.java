package io.openaev.rest.scenario;

import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.rest.inject.form.InjectBulkProcessingInput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
public class ScenarioInjectTestApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectTestStatusComposer injectTestStatusComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private VariableComposer variableComposer;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private ObjectMapper mapper;
  @Autowired private JavaMailSender mailSender;

  private ScenarioComposer.Composer scenarioWrapper;
  private InjectComposer.Composer inject1Wrapper, inject2Wrapper;
  private InjectTestStatusComposer.Composer injectTestStatus1Wrapper, injectTestStatus2Wrapper;

  @BeforeEach
  public void setup() {
    Mockito.reset(mailSender);
  }

  @Nested
  @DisplayName("Single email test send inject tests")
  public class EmailTestSendInjectTests {
    private InjectorContractComposer.Composer createEmailContract() {
      return injectorContractComposer
          .forInjectorContract(injectorContractFixture.getWellKnownSingleEmailContract())
          .withInjector(injectorFixture.getWellKnownEmailInjector(true));
    }

    @Test
    @DisplayName("Scenario variable is interpolated")
    @WithMockUser(withCapabilities = {Capability.LAUNCH_ASSESSMENT})
    public void scenarioVariableIsInterpolated() throws Exception {
      ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);
      String varKey = "var_key";
      String varValue = "var_value";
      Variable var = VariableFixture.getDefaultVariable();
      var.setKey(varKey);
      var.setValue(varValue);
      ScenarioComposer.Composer scenarioWithEmailInjectWrapper =
          scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario());

      Inject injectFixture =
          InjectFixture.getInjectForEmailContract(
              injectorContractFixture.getWellKnownSingleEmailContract());
      injectFixture.getContent().set("subject", mapper.valueToTree("test email"));
      injectFixture.getContent().set("body", mapper.valueToTree("${%s}".formatted(varKey)));

      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(injectFixture).withInjectorContract(createEmailContract());

      Scenario testScenario =
          scenarioWithEmailInjectWrapper
              .withInject(injectWrapper)
              .withVariable(variableComposer.forVariable(var))
              .persist()
              .get();

      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SCENARIO, Grant.GRANT_TYPE.PLANNER, testScenario.getId());

      entityManager.flush();
      entityManager.clear();

      when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
      Mockito.doCallRealMethod().when(mailSender).send((MimeMessage) any());
      mvc.perform(
              get(
                  SCENARIO_URI + "/{scenarioId}/injects/{injectId}/test",
                  scenarioWithEmailInjectWrapper.get().getId(),
                  scenarioWithEmailInjectWrapper.get().getInjects().getFirst().getId()))
          .andExpect(status().isOk());

      verify(mailSender).send(argument.capture());
      assertThat(
              ((MimeMultipart) argument.getAllValues().getFirst().getContent())
                  .getBodyPart(0)
                  .getContent())
          .isEqualTo("<div>%s</div>".formatted(varValue));
    }

    @Test
    @DisplayName("User variable is interpolated in scenario inject test")
    @WithMockUser(withCapabilities = {Capability.LAUNCH_ASSESSMENT})
    public void userVariableIsInterpolatedInScenarioInjectTest() throws Exception {
      User testUser = testUserHolder.get();
      ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);
      ScenarioComposer.Composer scenarioWithEmailInjectWrapper =
          scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario());

      Inject injectFixture =
          InjectFixture.getInjectForEmailContract(
              injectorContractFixture.getWellKnownSingleEmailContract());
      injectFixture.getContent().set("subject", mapper.valueToTree("test email"));
      injectFixture.getContent().set("body", mapper.valueToTree("${user.email}"));

      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(injectFixture).withInjectorContract(createEmailContract());

      scenarioWithEmailInjectWrapper.withInject(injectWrapper).persist();

      entityManager.flush();
      entityManager.clear();

      when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
      Mockito.doCallRealMethod().when(mailSender).send((MimeMessage) any());
      mvc.perform(
              get(
                  SCENARIO_URI + "/{scenarioId}/injects/{injectId}/test",
                  scenarioWithEmailInjectWrapper.get().getId(),
                  scenarioWithEmailInjectWrapper.get().getInjects().getFirst().getId()))
          .andExpect(status().isOk());

      verify(mailSender).send(argument.capture());
      assertThat(
              ((MimeMultipart) argument.getAllValues().getFirst().getContent())
                  .getBodyPart(0)
                  .getContent())
          .isEqualTo("<div>" + testUser.getEmail() + "</div>");
    }

    @Test
    @DisplayName("Simulation variable is interpolated")
    @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
    public void simulationVariableIsInterpolated() throws Exception {
      ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);
      String varKey = "var_key";
      String varValue = "var_value";
      Variable var = VariableFixture.getDefaultVariable();
      var.setKey(varKey);
      var.setValue(varValue);
      ExerciseComposer.Composer simulationWithEmailInjectWrapper =
          simulationComposer.forExercise(ExerciseFixture.createDefaultExercise());

      Inject injectFixture =
          InjectFixture.getInjectForEmailContract(
              injectorContractFixture.getWellKnownSingleEmailContract());
      injectFixture.getContent().set("subject", mapper.valueToTree("test email"));
      injectFixture.getContent().set("body", mapper.valueToTree("${%s}".formatted(varKey)));

      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(injectFixture).withInjectorContract(createEmailContract());

      simulationWithEmailInjectWrapper
          .withInject(injectWrapper)
          .withVariable(variableComposer.forVariable(var))
          .persist();

      entityManager.flush();
      entityManager.clear();

      when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
      Mockito.doCallRealMethod().when(mailSender).send((MimeMessage) any());
      mvc.perform(
              get(
                  EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                  simulationWithEmailInjectWrapper.get().getId(),
                  simulationWithEmailInjectWrapper.get().getInjects().getFirst().getId()))
          .andExpect(status().isOk());

      verify(mailSender).send(argument.capture());
      assertThat(
              ((MimeMultipart) argument.getAllValues().getFirst().getContent())
                  .getBodyPart(0)
                  .getContent())
          .isEqualTo(
              "<div style=\"text-align: center; margin-bottom: 10px;\">SIMULATION HEADER</div><div>%s</div>"
                  .formatted(varValue));
    }

    @Test
    @DisplayName("User variable is interpolated in simulation inject test")
    @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
    public void userVariableIsInterpolatedInSimulationInjectTest() throws Exception {
      User testUser = testUserHolder.get();
      ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);
      ExerciseComposer.Composer simulationWithEmailInjectWrapper =
          simulationComposer.forExercise(ExerciseFixture.createDefaultExercise());

      Inject injectFixture =
          InjectFixture.getInjectForEmailContract(
              injectorContractFixture.getWellKnownSingleEmailContract());
      injectFixture.getContent().set("subject", mapper.valueToTree("test email"));
      injectFixture.getContent().set("body", mapper.valueToTree("${user.email}"));

      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(injectFixture).withInjectorContract(createEmailContract());

      simulationWithEmailInjectWrapper.withInject(injectWrapper).persist();

      entityManager.flush();
      entityManager.clear();

      when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
      Mockito.doCallRealMethod().when(mailSender).send((MimeMessage) any());
      mvc.perform(
              get(
                  EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                  simulationWithEmailInjectWrapper.get().getId(),
                  simulationWithEmailInjectWrapper.get().getInjects().getFirst().getId()))
          .andReturn();
      // .andExpect(status().isOk());

      verify(mailSender).send(argument.capture());
      assertThat(
              ((MimeMultipart) argument.getAllValues().getFirst().getContent())
                  .getBodyPart(0)
                  .getContent())
          .isEqualTo(
              "<div style=\"text-align: center; margin-bottom: 10px;\">SIMULATION HEADER</div><div>%s</div>"
                  .formatted(testUser.getEmail()));
    }
  }

  @Nested
  @DisplayName("Multi (global) email test send inject tests")
  public class MultiEmailTestSendInjectTests {
    private InjectorContractComposer.Composer createEmailContract() {
      return injectorContractComposer
          .forInjectorContract(injectorContractFixture.getWellKnownGlobalEmailContract())
          .withInjector(injectorFixture.getWellKnownEmailInjector(true));
    }

    @Test
    @DisplayName("Scenario variable is interpolated")
    @WithMockUser(withCapabilities = {Capability.LAUNCH_ASSESSMENT})
    public void scenarioVariableIsInterpolated() throws Exception {
      ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);
      String varKey = "var_key";
      String varValue = "var_value";
      Variable var = VariableFixture.getDefaultVariable();
      var.setKey(varKey);
      var.setValue(varValue);
      ScenarioComposer.Composer scenarioWithEmailInjectWrapper =
          scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario());

      Inject injectFixture =
          InjectFixture.getInjectForEmailContract(
              injectorContractFixture.getWellKnownSingleEmailContract());
      injectFixture.getContent().set("subject", mapper.valueToTree("test email"));
      injectFixture.getContent().set("body", mapper.valueToTree("${%s}".formatted(varKey)));

      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(injectFixture).withInjectorContract(createEmailContract());

      scenarioWithEmailInjectWrapper
          .withInject(injectWrapper)
          .withVariable(variableComposer.forVariable(var))
          .persist();

      entityManager.flush();
      entityManager.clear();

      when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
      Mockito.doCallRealMethod().when(mailSender).send((MimeMessage) any());
      mvc.perform(
              get(
                  SCENARIO_URI + "/{scenarioId}/injects/{injectId}/test",
                  scenarioWithEmailInjectWrapper.get().getId(),
                  scenarioWithEmailInjectWrapper.get().getInjects().getFirst().getId()))
          .andExpect(status().isOk());

      verify(mailSender).send(argument.capture());
      assertThat(
              ((MimeMultipart) argument.getAllValues().getFirst().getContent())
                  .getBodyPart(0)
                  .getContent())
          .isEqualTo("<div>%s</div>".formatted(varValue));
    }

    @Test
    @DisplayName(
        "When a single user is used in a multi email, user variable is interpolated in scenario inject test")
    @WithMockUser(withCapabilities = {Capability.LAUNCH_ASSESSMENT})
    public void userVariableIsInterpolatedInScenarioInjectTest() throws Exception {
      User testUser = testUserHolder.get();
      ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);
      ScenarioComposer.Composer scenarioWithEmailInjectWrapper =
          scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario());

      Inject injectFixture =
          InjectFixture.getInjectForEmailContract(
              injectorContractFixture.getWellKnownSingleEmailContract());
      injectFixture.getContent().set("subject", mapper.valueToTree("test email"));
      injectFixture.getContent().set("body", mapper.valueToTree("${user.email}"));

      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(injectFixture).withInjectorContract(createEmailContract());

      scenarioWithEmailInjectWrapper.withInject(injectWrapper).persist();

      entityManager.flush();
      entityManager.clear();

      when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
      Mockito.doCallRealMethod().when(mailSender).send((MimeMessage) any());
      mvc.perform(
              get(
                  SCENARIO_URI + "/{scenarioId}/injects/{injectId}/test",
                  scenarioWithEmailInjectWrapper.get().getId(),
                  scenarioWithEmailInjectWrapper.get().getInjects().getFirst().getId()))
          .andExpect(status().isOk());

      verify(mailSender).send(argument.capture());
      assertThat(
              ((MimeMultipart) argument.getAllValues().getFirst().getContent())
                  .getBodyPart(0)
                  .getContent())
          .isEqualTo("<div>" + testUser.getEmail() + "</div>");
    }

    @Test
    @DisplayName("Simulation variable is interpolated")
    @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
    public void simulationVariableIsInterpolated() throws Exception {
      ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);
      String varKey = "var_key";
      String varValue = "var_value";
      Variable var = VariableFixture.getDefaultVariable();
      var.setKey(varKey);
      var.setValue(varValue);
      ExerciseComposer.Composer simulationWithEmailInjectWrapper =
          simulationComposer.forExercise(ExerciseFixture.createDefaultExercise());

      Inject injectFixture =
          InjectFixture.getInjectForEmailContract(
              injectorContractFixture.getWellKnownSingleEmailContract());
      injectFixture.getContent().set("subject", mapper.valueToTree("test email"));
      injectFixture.getContent().set("body", mapper.valueToTree("${%s}".formatted(varKey)));

      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(injectFixture).withInjectorContract(createEmailContract());

      simulationWithEmailInjectWrapper
          .withInject(injectWrapper)
          .withVariable(variableComposer.forVariable(var))
          .persist();

      entityManager.flush();
      entityManager.clear();

      when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
      Mockito.doCallRealMethod().when(mailSender).send((MimeMessage) any());
      mvc.perform(
              get(
                  EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                  simulationWithEmailInjectWrapper.get().getId(),
                  simulationWithEmailInjectWrapper.get().getInjects().getFirst().getId()))
          .andExpect(status().isOk());

      verify(mailSender).send(argument.capture());
      assertThat(
              ((MimeMultipart) argument.getAllValues().getFirst().getContent())
                  .getBodyPart(0)
                  .getContent())
          .isEqualTo(
              "<div style=\"text-align: center; margin-bottom: 10px;\">SIMULATION HEADER</div><div>%s</div>"
                  .formatted(varValue));
    }

    @Test
    @DisplayName(
        "When a single user is used in a multi email, user variable is interpolated in simulation inject test")
    @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
    public void userVariableIsInterpolatedInSimulationInjectTest() throws Exception {
      User testUser = testUserHolder.get();
      ArgumentCaptor<MimeMessage> argument = ArgumentCaptor.forClass(MimeMessage.class);
      ExerciseComposer.Composer simulationWithEmailInjectWrapper =
          simulationComposer.forExercise(ExerciseFixture.createDefaultExercise());

      Inject injectFixture =
          InjectFixture.getInjectForEmailContract(
              injectorContractFixture.getWellKnownSingleEmailContract());
      injectFixture.getContent().set("subject", mapper.valueToTree("test email"));
      injectFixture.getContent().set("body", mapper.valueToTree("${user.email}"));

      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(injectFixture).withInjectorContract(createEmailContract());

      simulationWithEmailInjectWrapper.withInject(injectWrapper).persist();

      entityManager.flush();
      entityManager.clear();

      when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
      Mockito.doCallRealMethod().when(mailSender).send((MimeMessage) any());
      mvc.perform(
              get(
                  EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                  simulationWithEmailInjectWrapper.get().getId(),
                  simulationWithEmailInjectWrapper.get().getInjects().getFirst().getId()))
          .andExpect(status().isOk());

      verify(mailSender).send(argument.capture());
      assertThat(
              ((MimeMultipart) argument.getAllValues().getFirst().getContent())
                  .getBodyPart(0)
                  .getContent())
          .isEqualTo(
              "<div style=\"text-align: center; margin-bottom: 10px;\">SIMULATION HEADER</div><div>%s</div>"
                  .formatted(testUser.getEmail()));
    }
  }

  @Nested
  @DisplayName("other tests")
  public class OtherTests {
    @BeforeEach
    void setupData() {
      InjectorContract injectorContract =
          injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow();

      InjectTestStatusComposer.Composer injectTestStatusComposer1 =
          injectTestStatusComposer.forInjectTestStatus(
              InjectTestStatusFixture.createSuccessInjectStatus());

      InjectTestStatusComposer.Composer injectTestStatusComposer2 =
          injectTestStatusComposer.forInjectTestStatus(
              InjectTestStatusFixture.createSuccessInjectStatus());

      InjectComposer.Composer injectComposer1 =
          injectComposer
              .forInject(InjectFixture.getInjectForEmailContract(injectorContract))
              .withInjectTestStatus(injectTestStatusComposer1);

      InjectComposer.Composer injectComposer2 =
          injectComposer
              .forInject(InjectFixture.getInjectForEmailContract(injectorContract))
              .withInjectTestStatus(injectTestStatusComposer2);

      inject1Wrapper = injectComposer1.persist();
      inject2Wrapper = injectComposer2.persist();

      injectTestStatus1Wrapper = injectTestStatusComposer1.persist();
      injectTestStatus2Wrapper = injectTestStatusComposer2.persist();

      scenarioWrapper =
          scenarioComposer
              .forScenario(ScenarioFixture.getScenario())
              .withInjects(List.of(injectComposer1, injectComposer2));
    }

    @Nested
    @DisplayName("As ScenarioPlanner")
    class ScenarioPlannerAccess {

      @Test
      @DisplayName("Should return paginated inject test results when inject tests exist")
      @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
      void should_return_paginated_results_when_inject_tests_exist() throws Exception {
        SearchPaginationInput searchPaginationInput = new SearchPaginationInput();
        String response =
            mvc.perform(
                    post(
                            SCENARIO_URI + "/{scenarioId}/injects/test/search",
                            scenarioWrapper.persist().get().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(searchPaginationInput)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response)
            .inPath("$.content[*].status_id")
            .isArray()
            .contains(injectTestStatus1Wrapper.get().getId());
      }

      @Test
      @DisplayName("Should return test status using test id")
      @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
      void should_return_test_status_by_testId() throws Exception {
        mvc.perform(
                get(
                    SCENARIO_URI + "/injects/test/{testId}",
                    injectTestStatus1Wrapper.get().getId()))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName("Should return test status when testing a specific inject")
      @WithMockUser(withCapabilities = {Capability.LAUNCH_ASSESSMENT})
      void should_return_test_status_when_testing_specific_inject() throws Exception {
        mvc.perform(
                get(
                    SCENARIO_URI + "/{scenarioId}/injects/{injectId}/test",
                    scenarioWrapper.persist().get().getId(),
                    inject1Wrapper.get().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inject_id").value(inject1Wrapper.get().getId()));
      }

      @Test
      @DisplayName("Should return test statuses when performing bulk test with inject IDs")
      @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT, Capability.MANAGE_ASSESSMENT})
      void should_return_test_statuses_when_bulk_testing_with_inject_ids() throws Exception {
        Inject testInject = inject1Wrapper.persist().get();
        Scenario testScenario = scenarioWrapper.persist().get();
        InjectBulkProcessingInput input = new InjectBulkProcessingInput();
        input.setInjectIDsToProcess(List.of(testInject.getId()));
        input.setSimulationOrScenarioId(testScenario.getId());

        mvc.perform(
                post(SCENARIO_URI + "/{scenarioId}/injects/test", testScenario.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
      }

      @Test
      @DisplayName("Should return 200 when deleting an inject test status")
      @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
      void should_return_200_when_fetching_deleting_an_inject_test_status() throws Exception {
        mvc.perform(
                delete(
                    SCENARIO_URI + "/{scenarioId}/injects/test/{testId}",
                    scenarioWrapper.persist().get().getId(),
                    injectTestStatus2Wrapper.get().getId()))
            .andExpect(status().isOk());
      }
    }

    @Nested
    @DisplayName("As Unauthorized User")
    @WithMockUser
    class UnauthorizedUserAccess {

      @Test
      @DisplayName("Should return 403 when search a paginated inject test results")
      void should_return_403_when_search_paginated_results() throws Exception {
        SearchPaginationInput searchPaginationInput = new SearchPaginationInput();
        mvc.perform(
                post(
                        SCENARIO_URI + "/{scenarioId}/injects/test/search",
                        scenarioWrapper.persist().get().getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().isForbidden());
      }

      @Test
      @DisplayName("Should return 200 when search by id")
      @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
      void should_return_200_when_search_by_testId() throws Exception {
        mvc.perform(
                get(
                    SCENARIO_URI + "/injects/test/{testId}",
                    injectTestStatus1Wrapper.get().getId()))
            .andExpect(status().isOk());
      }

      @Test
      @DisplayName("Should return 403 when testing a specific inject")
      void should_return_403_when_testing_specific_inject() throws Exception {
        mvc.perform(
                get(
                    SCENARIO_URI + "/{scenarioId}/injects/{injectId}/test",
                    scenarioWrapper.persist().get().getId(),
                    inject1Wrapper.get().getId()))
            .andExpect(status().isForbidden());
      }

      @Test
      @DisplayName("Should return 403 when performing bulk test with inject IDs")
      void should_return_403_when_bulk_testing_with_inject_ids() throws Exception {
        InjectBulkProcessingInput input = new InjectBulkProcessingInput();
        input.setInjectIDsToProcess(
            List.of(inject1Wrapper.get().getId(), inject2Wrapper.get().getId()));
        input.setSimulationOrScenarioId(scenarioWrapper.get().getId());

        mvc.perform(
                post(
                        SCENARIO_URI + "/{scenarioId}/injects/test",
                        scenarioWrapper.persist().get().getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input)))
            .andExpect(status().isForbidden());
      }
    }
  }
}
