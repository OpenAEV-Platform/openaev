package io.openaev.rest.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.PayloadVersionChangeInput;
import io.openaev.api.threat_arsenal.PayloadVersionChangeOutput;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.ContentHistoryRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.rest.payload.service.PayloadUpdateService;
import io.openaev.rest.payload.service.PayloadUpdateService.PayloadSaveResult;
import io.openaev.service.payload.PayloadVersionChangeService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Saving a local payload creates a version (ADR-011, point 4)")
class PayloadLocalVersionTest extends IntegrationTest {

  @Autowired private PayloadUpdateService payloadUpdateService;
  @Autowired private PayloadVersionChangeService payloadVersionChangeService;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ContentHistoryRepository contentHistoryRepository;
  @Autowired private PayloadCreationService payloadCreationService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private ManagerFactory managerFactory;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void registerPayloadInjector() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    managerFactory.getManager(TenantContext.getCurrentTenant()).monitorIntegrations();
  }

  private InjectorContract localCommand() {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());
    input.setName("Dump LSASS " + UUID.randomUUID());
    return payloadCreationService
        .createPayload(TxCtx.forTenant(TenantContext.getCurrentTenant()), input)
        .injectorContract();
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
    input.setArguments(new ArrayList<>(command.getArguments()));
    input.setPrerequisites(new ArrayList<>(command.getPrerequisites()));
    input.setCleanupExecutor(command.getCleanupExecutor());
    input.setCleanupCommand(command.getCleanupCommand());
    input.setAttackPatternsIds(List.of());
    input.setTagIds(List.of());
    input.setDomainIds(List.of());
    return input;
  }

  private Inject atomicTestingOn(InjectorContract contract) {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setInjectorContract(contract);
    return injectComposer.forInject(inject).persist().get();
  }

  private void flush() {
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("Saving an unused payload adds 1.1.0, nobody to move")
  @WithMockUser(isAdmin = true)
  void given_unusedPayload_when_contentChanges_should_addMinorVersion() {
    // Arrange
    InjectorContract contract = localCommand();
    Command v1 = (Command) contract.getPayload();
    String v1Content = v1.getContent();
    String v1Hash = v1.getContentHash();
    PayloadUpdateInput input = inputFrom(v1);
    input.setContent("procdump -ma lsass.exe");

    // Act
    PayloadSaveResult result = payloadUpdateService.updatePayload(v1.getId(), input);
    flush();

    // Assert
    assertThat(result.newVersion()).isTrue();
    assertThat(result.previousUsages()).isZero();
    Payload v2 = payloadRepository.findById(result.payload().getId()).orElseThrow();
    assertThat(v2.getId()).isNotEqualTo(v1.getId());
    assertThat(v2.getVersion()).isEqualTo("1.1.0");
    assertThat(v2.isLatest()).isTrue();
    Command previous = (Command) payloadRepository.findById(v1.getId()).orElseThrow();
    assertThat(previous.getContent()).isEqualTo(v1Content);
    assertThat(previous.getContentHash()).isEqualTo(v1Hash);
    assertThat(previous.getVersion()).isEqualTo(Payload.FIRST_VERSION);
    assertThat(previous.isLatest()).isFalse();
  }

  @Test
  @DisplayName("Saving a used payload tells how many usages stay on the previous version")
  @WithMockUser(isAdmin = true)
  void given_usedPayload_when_contentChanges_should_reportUsagesOnPreviousVersion() {
    // Arrange
    InjectorContract contract = localCommand();
    Inject inject = atomicTestingOn(contract);
    PayloadUpdateInput input = inputFrom((Command) contract.getPayload());
    input.setContent("procdump -ma lsass.exe");

    // Act
    PayloadSaveResult result =
        payloadUpdateService.updatePayload(contract.getPayload().getId(), input);
    flush();

    // Assert
    assertThat(result.previousUsages()).isEqualTo(1);
    assertThat(
            injectRepository
                .findById(inject.getId())
                .orElseThrow()
                .getInjectorContract()
                .orElseThrow()
                .getId())
        .isEqualTo(contract.getId());
  }

  @Test
  @DisplayName("Changing the arguments is a major version")
  @WithMockUser(isAdmin = true)
  void given_argumentsChange_should_addMajorVersion() {
    // Arrange
    InjectorContract contract = localCommand();
    PayloadUpdateInput input = inputFrom((Command) contract.getPayload());
    PayloadArgument argument = new PayloadArgument();
    argument.setKey("target");
    argument.setType(PrimitiveType.Text);
    argument.setDefaultValue("lsass.exe");
    input.setArguments(new ArrayList<>(List.of(argument)));

    // Act
    PayloadSaveResult result =
        payloadUpdateService.updatePayload(contract.getPayload().getId(), input);

    // Assert
    assertThat(result.payload().getVersion()).isEqualTo("2.0.0");
  }

  @Test
  @DisplayName("A save that keeps the hashed content adds no version")
  @WithMockUser(isAdmin = true)
  void given_sameContent_should_keepVersion() {
    // Arrange
    InjectorContract contract = localCommand();
    PayloadUpdateInput input = inputFrom((Command) contract.getPayload());

    // Act
    PayloadSaveResult result =
        payloadUpdateService.updatePayload(contract.getPayload().getId(), input);

    // Assert
    assertThat(result.newVersion()).isFalse();
    assertThat(result.payload().getId()).isEqualTo(contract.getPayload().getId());
  }

  @Test
  @DisplayName("The history of a saved version lists each changed field, before and after")
  @WithMockUser(isAdmin = true)
  void given_newVersion_should_recordChangedFields() {
    // Arrange
    InjectorContract contract = localCommand();
    Command v1 = (Command) contract.getPayload();
    String before = v1.getContent();
    PayloadUpdateInput input = inputFrom(v1);
    input.setContent("whoami /all");

    // Act
    PayloadSaveResult result = payloadUpdateService.updatePayload(v1.getId(), input);
    flush();

    // Assert
    ContentHistory update =
        contentHistoryRepository
            .findAllByTypeAndEntityIdInOrderByCreatedAtDesc(
                ContentType.PAYLOAD, List.of(result.payload().getId()), PageRequest.of(0, 10))
            .getContent()
            .getFirst();
    assertThat(update.getAction()).isEqualTo(ContentHistory.ACTION.UPDATE);
    assertThat(update.getDetails().path("changes"))
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.path("field").asText()).isEqualTo("command_content");
              assertThat(change.path("before").asText()).isEqualTo(before);
              assertThat(change.path("after").asText()).isEqualTo("whoami /all");
            });
  }

  @Test
  @DisplayName("Injects go back to the previous version, and the history says who saved what")
  @WithMockUser(isAdmin = true)
  void given_newVersion_should_rollBackInjectsAndRecordHistory() {
    // Arrange
    InjectorContract contract = localCommand();
    Payload v1 = contract.getPayload();
    PayloadUpdateInput input = inputFrom((Command) v1);
    input.setContent("procdump -ma lsass.exe");
    PayloadSaveResult result = payloadUpdateService.updatePayload(v1.getId(), input);
    Inject inject = atomicTestingOn(result.injectorContract());

    // Act
    PayloadVersionChangeOutput rollback =
        payloadVersionChangeService.changeVersion(
            result.injectorContract().getId(),
            new PayloadVersionChangeInput(v1.getId(), List.of(inject.getId()), List.of()));
    flush();

    // Assert
    assertThat(rollback.movedIds()).containsExactly(inject.getId());
    assertThat(
            injectRepository
                .findById(inject.getId())
                .orElseThrow()
                .getInjectorContract()
                .orElseThrow()
                .getId())
        .isEqualTo(contract.getId());
    List<ContentHistory> history =
        contentHistoryRepository
            .findAllByTypeAndEntityIdInOrderByCreatedAtDesc(
                ContentType.PAYLOAD,
                List.of(v1.getId(), result.payload().getId()),
                PageRequest.of(0, 10))
            .getContent();
    assertThat(history)
        .anySatisfy(
            entry -> {
              assertThat(entry.getAction()).isEqualTo(ContentHistory.ACTION.UPDATE);
              assertThat(entry.getFromVersion()).isEqualTo("1.0.0");
              assertThat(entry.getVersion()).isEqualTo("1.1.0");
            });
  }
}
