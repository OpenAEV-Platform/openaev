package io.openaev.injects.email;

import static io.openaev.helper.StreamHelper.fromIterable;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Execution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import io.openaev.injectors.email.model.EmailContent;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.model.inject.form.Expectation;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

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

  @Test
  void process() throws Exception {
    // -- PREPARE --
    EmailContent content = new EmailContent();
    content.setSubject("Subject email");
    content.setBody("A body");
    Expectation expectation = new Expectation();
    expectation.setName("The animation team can validate the audience reaction");
    expectation.setScore(10.0);
    expectation.setType(InjectExpectation.EXPECTATION_TYPE.MANUAL);
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
}
