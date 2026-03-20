package io.openaev.injects.email;

import static io.openaev.helper.StreamHelper.fromIterable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import io.openaev.injectors.email.model.EmailContent;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.model.inject.form.Expectation;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@Transactional
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class EmailExecutorTest extends IntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private ExecutionContextService executionContextService;
  @Resource protected ObjectMapper mapper;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;

  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;

  @BeforeEach
  public void beforeEach() {
    injectComposer.reset();
    injectorContractComposer.reset();
    teamComposer.reset();
  }

  private Inject createEmailInjectHelper(List<Expectation> expectationList) {
    Inject inject = InjectFixture.getDefaultInject();
    EmailContent content = new EmailContent();
    content.setExpectations(expectationList);
    content.setSubject("Subject email");
    content.setBody("A body");
    inject.setContent(this.mapper.valueToTree(content));

    return injectComposer
        .forInject(inject)
        .withTeam(
            teamComposer
                .forTeam(TeamFixture.getDefaultContextualTeam())
                .withUser(userComposer.forUser(UserFixture.getUserWithDefaultEmail())))
        .withInjectorContract(
            injectorContractComposer.forInjectorContract(
                injectorContractFixture.getWellKnownGlobalEmailContract()))
        .persist()
        .get();
  }

  @Test
  void process() throws Exception {
    // -- PREPARE --
    EmailContent content = new EmailContent();
    content.setSubject("Subject email");
    content.setBody("A body");
    Expectation expectation =
        ExpectationFixture.createExpectation(
            InjectExpectation.EXPECTATION_TYPE.MANUAL,
            "The animation team can validate the audience reaction");
    content.setExpectations(List.of(expectation));
    Inject inject = new Inject();
    inject.setInjectorContract(injectorContractFixture.getWellKnownGlobalEmailContract());
    inject.setContent(this.mapper.valueToTree(content));
    Iterable<User> users = this.userRepository.findAll();
    List<ExecutionContext> userInjectContexts =
        fromIterable(users).stream()
            .map(
                user ->
                    this.executionContextService.executionContext(user, inject, "Direct execution"))
            .toList();
    ExecutableInject executableInject =
        new ExecutableInject(true, true, inject, userInjectContexts);
    Execution execution = new Execution(executableInject.isRuntime());

    // -- EXECUTE --
    Manager manager = new Manager(List.of(emailInjectorIntegrationFactory));
    manager.monitorIntegrations();
    io.openaev.executors.Injector emailExecutor = manager.requestEmailInjector();
    emailExecutor.process(execution, executableInject);

    // -- ASSERT --
    // No injectExpectation should be created.
    assertEquals(Collections.emptyList(), injectExpectationRepository.findAll());
  }

  @Test
  void givenExpectation_shouldComputeInjectExpectationAndInjectExpectationResult()
      throws Exception {
    // -- PREPARE --

    String expectationAName = "Expectation A";
    Expectation expectationA =
        ExpectationFixture.createExpectation(
            InjectExpectation.EXPECTATION_TYPE.MANUAL, expectationAName);

    String expectationBName = "Expectation B";
    Expectation expectationB =
        ExpectationFixture.createExpectation(
            InjectExpectation.EXPECTATION_TYPE.MANUAL, expectationBName);

    Inject inject = createEmailInjectHelper(List.of(expectationA, expectationB));
    Injection injection = mock(Injection.class);
    when(injection.getInject()).thenReturn(inject);
    List<ExecutionContext> userInjectContexts =
        fromIterable(inject.getTeams().getFirst().getUsers()).stream()
            .map(
                user ->
                    this.executionContextService.executionContext(user, inject, "Direct execution"))
            .toList();
    ExecutableInject executableInject =
        new ExecutableInject(
            false,
            false,
            injection,
            inject.getTeams(),
            inject.getAssets(),
            inject.getAssetGroups(),
            userInjectContexts);
    Execution execution = new Execution(executableInject.isRuntime());

    // -- EXECUTE --
    Manager manager = new Manager(List.of(emailInjectorIntegrationFactory));
    manager.monitorIntegrations();
    io.openaev.executors.Injector emailExecutor = manager.requestEmailInjector();
    emailExecutor.process(execution, executableInject);

    // -- ASSERT --
    // Should have 4 inject expectations - 1 for team - 2 for the user (expectation A and
    // expectation B)
    List<InjectExpectation> injectExpectationList =
        injectExpectationRepository.findAllByInjectId(inject.getId());
    assertEquals(4, injectExpectationList.size());
    List<InjectExpectation> teamExpectations =
        injectExpectationList.stream()
            .filter(ie -> ie.getUser() == null && ie.getTeam() != null)
            .toList();
    assertEquals(2, teamExpectations.size());
    assertEquals(
        1, teamExpectations.stream().filter(ie -> expectationAName.equals(ie.getName())).count());
    assertEquals(
        1, teamExpectations.stream().filter(ie -> expectationBName.equals(ie.getName())).count());
    List<InjectExpectation> userExpectations =
        injectExpectationList.stream()
            .filter(ie -> ie.getUser() != null && ie.getTeam() != null)
            .toList();
    assertEquals(2, userExpectations.size());
    assertEquals(
        1, userExpectations.stream().filter(ie -> expectationAName.equals(ie.getName())).count());
    assertEquals(
        1, userExpectations.stream().filter(ie -> expectationBName.equals(ie.getName())).count());

    // InjectExpectation.results.result should be set to null for all user expectation
    List<InjectExpectationResult> teamResults =
        teamExpectations.stream().flatMap(ie -> ie.getResults().stream()).toList();
    assertEquals(0, teamResults.size());
    List<InjectExpectationResult> userResults =
        userExpectations.stream().flatMap(ie -> ie.getResults().stream()).toList();
    assertTrue(userResults.stream().allMatch(r -> r.getResult() == null));
  }
}
