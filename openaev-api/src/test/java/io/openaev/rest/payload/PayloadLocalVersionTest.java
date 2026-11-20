package io.openaev.rest.payload;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Command;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.service.PayloadCreationService.PayloadInjectorContractCreationResult;
import io.openaev.rest.payload.service.PayloadUpdateService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,CATALOG")
@DisplayName("Saving a local payload adds a version")
class PayloadLocalVersionTest extends IntegrationTest {

  @Autowired private PayloadUpdateService payloadUpdateService;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;

  @BeforeEach
  void setUp() {
    injectorContractComposer.reset();
    payloadComposer.reset();
  }

  private Command localCommand() {
    InjectorContract action =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
            .persist()
            .get();
    flush();
    return reloaded(action.getPayload().getId());
  }

  private static PayloadUpdateInput inputFrom(Command command) {
    PayloadUpdateInput input = new PayloadUpdateInput();
    input.setName(command.getName());
    input.setDescription(command.getDescription());
    input.setPlatforms(command.getPlatforms());
    input.setExecutionArch(command.getExecutionArch());
    input.setExpectations(command.getExpectations());
    input.setExecutor(command.getExecutor());
    input.setContent(command.getContent());
    input.setArguments(new ArrayList<>(emptyIfNull(command.getArguments())));
    input.setPrerequisites(new ArrayList<>(emptyIfNull(command.getPrerequisites())));
    input.setCleanupExecutor(command.getCleanupExecutor());
    input.setCleanupCommand(command.getCleanupCommand());
    return input;
  }

  private PayloadInjectorContractCreationResult save(Command command, String content) {
    PayloadUpdateInput input = inputFrom(command);
    input.setContent(content);
    PayloadInjectorContractCreationResult result =
        payloadUpdateService.updatePayload(command.getId(), input);
    flush();
    return result;
  }

  private Command reloaded(String payloadId) {
    return (Command) payloadRepository.findById(payloadId).orElseThrow();
  }

  private void flush() {
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("A new content adds a version; the previous one and its injects stay as they were")
  void given_contentChanged_should_addVersionAndKeepInjectsOnThePreviousOne() {
    // Arrange
    Command first = localCommand();
    InjectorContract firstAction =
        injectorContractRepository.findInjectorContractByPayload(first).orElseThrow();
    Inject inject =
        injectRepository.save(InjectFixture.createInject(firstAction, "Atomic testing"));
    String firstContent = first.getContent();

    // Act
    PayloadInjectorContractCreationResult result = save(first, "procdump -ma lsass.exe");

    // Assert
    Command added = reloaded(result.payload().getId());
    Command previous = reloaded(first.getId());
    assertThat(added.getId()).isNotEqualTo(previous.getId());
    assertThat(added.getVersion()).isEqualTo("1.1.0");
    assertThat(added.getContent()).isEqualTo("procdump -ma lsass.exe");
    assertThat(added.getIdentity().getId()).isEqualTo(previous.getIdentity().getId());
    assertThat(added.isLatest()).isTrue();
    assertThat(result.injectorContract().getId()).isNotEqualTo(firstAction.getId());
    assertThat(previous.getVersion()).isEqualTo(Payload.FIRST_VERSION);
    assertThat(previous.getContent()).isEqualTo(firstContent);
    assertThat(previous.isLatest()).isFalse();
    assertThat(
            injectRepository
                .findById(inject.getId())
                .orElseThrow()
                .getInjectorContract()
                .orElseThrow()
                .getId())
        .isEqualTo(firstAction.getId());
  }

  @Test
  @DisplayName("Saving an older version numbers after the highest one")
  void given_olderVersionSaved_should_numberAfterTheHighest() {
    // Arrange
    Command first = localCommand();
    save(first, "procdump -ma lsass.exe");

    // Act
    PayloadInjectorContractCreationResult result =
        save(reloaded(first.getId()), "rundll32 comsvcs.dll MiniDump 624 lsass.dmp full");

    // Assert
    assertThat(reloaded(result.payload().getId()).getVersion()).isEqualTo("1.2.0");
  }

  @Test
  @DisplayName("Changing the arguments adds a major version")
  void given_argumentAdded_should_addMajorVersion() {
    // Arrange
    Command first = localCommand();
    PayloadUpdateInput input = inputFrom(first);
    PayloadArgument argument = new PayloadArgument();
    argument.setKey("target");
    argument.setType(PrimitiveType.Text);
    argument.setDefaultValue("lsass.exe");
    input.setArguments(new ArrayList<>(List.of(argument)));

    // Act
    PayloadInjectorContractCreationResult result =
        payloadUpdateService.updatePayload(first.getId(), input);
    flush();

    // Assert
    assertThat(reloaded(result.payload().getId()).getVersion()).isEqualTo("2.0.0");
  }

  @Test
  @DisplayName("A save that keeps the hashed content updates the payload in place")
  void given_sameContent_should_updateInPlace() {
    // Arrange
    Command first = localCommand();

    // Act
    PayloadInjectorContractCreationResult result = save(first, first.getContent());

    // Assert
    assertThat(result.payload().getId()).isEqualTo(first.getId());
    assertThat(reloaded(first.getId()).getVersion()).isEqualTo(Payload.FIRST_VERSION);
  }
}
