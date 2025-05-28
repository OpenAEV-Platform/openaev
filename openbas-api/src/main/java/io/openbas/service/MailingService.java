package io.openbas.service;

import static io.openbas.config.OpenBASAnonymous.ANONYMOUS;
import static io.openbas.config.SessionHelper.currentUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.Exercise;
import io.openbas.database.model.Inject;
import io.openbas.database.model.User;
import io.openbas.database.repository.InjectorContractRepository;
import io.openbas.database.repository.UserRepository;
import io.openbas.execution.ExecutableInject;
import io.openbas.execution.ExecutionContext;
import io.openbas.execution.ExecutionContextService;
import io.openbas.executors.Injector;
import io.openbas.injectors.email.EmailContract;
import io.openbas.injectors.email.model.EmailContent;
import io.openbas.rest.exception.ElementNotFoundException;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class MailingService {

  @Resource protected ObjectMapper mapper;

  private ApplicationContext context;

  private UserRepository userRepository;

  private InjectorContractRepository injectorContractRepository;

  private ExecutionContextService executionContextService;

  @Autowired
  public void setUserRepository(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Autowired
  public void setInjectorContractRepository(InjectorContractRepository injectorContractRepository) {
    this.injectorContractRepository = injectorContractRepository;
  }

  @Autowired
  public void setContext(ApplicationContext context) {
    this.context = context;
  }

  @Autowired
  public void setExecutionContextService(
      @NotNull final ExecutionContextService executionContextService) {
    this.executionContextService = executionContextService;
  }

  public void sendEmail(
      String subject, String body, List<User> users, Optional<Exercise> exercise) {
    EmailContent emailContent = new EmailContent();
    emailContent.setSubject(subject);
    emailContent.setBody(body);

    Inject inject = new Inject();
    inject.setInjectorContract(
        this.injectorContractRepository
            .findById(EmailContract.EMAIL_DEFAULT)
            .orElseThrow(ElementNotFoundException::new));

    inject
        .getInjectorContract()
        .ifPresent(
            injectorContract -> {
              inject.setContent(this.mapper.valueToTree(emailContent));

              // When resetting the password, the user is not logged in (anonymous),
              // so there's no need to add the user to the inject.
              if (!ANONYMOUS.equals(currentUser().getId())) {
                inject.setUser(
                    this.userRepository
                        .findById(currentUser().getId())
                        .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
              }

              exercise.ifPresent(inject::setExercise);

              List<ExecutionContext> userInjectContexts =
                  users.stream()
                      .distinct()
                      .map(
                          user ->
                              this.executionContextService.executionContext(
                                  user, inject, "Direct execution"))
                      .toList();
              ExecutableInject injection =
                  new ExecutableInject(false, true, inject, null, userInjectContexts); //TODO POC
              Injector executor =
                  this.context.getBean(injectorContract.getInjector().getType(), Injector.class);
              executor.executeInjection(injection);
            });
  }

  public void sendEmail(String subject, String body, List<User> users) {
    sendEmail(subject, body, users, Optional.empty());
  }
}
