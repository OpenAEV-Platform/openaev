package io.openaev.rest.payload;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Command;
import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.OutputParser;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.rest.payload.contract_output_element.ContractOutputElementInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import io.openaev.rest.payload.service.PayloadCreationService.PayloadInjectorContractCreationResult;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.payload.service.PayloadUpdateService;
import io.openaev.utils.fixtures.CollectorTypeFixture;
import io.openaev.utils.fixtures.DetectionRemediationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.CollectorTypeComposer;
import io.openaev.utils.fixtures.composers.DetectionRemediationComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.payload_fixture.ContractOutputElementInputFixture;
import io.openaev.utils.fixtures.payload_fixture.OutputParserInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
  @Autowired private PayloadService payloadService;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private CollectorTypeComposer collectorTypeComposer;
  @Autowired private DetectionRemediationComposer detectionRemediationComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;

  @BeforeEach
  void setUp() {
    injectorContractComposer.reset();
    payloadComposer.reset();
    collectorTypeComposer.reset();
    detectionRemediationComposer.reset();
    securityPlatformComposer.reset();
  }

  private Command localCommand() {
    return persisted(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()));
  }

  private Command persisted(PayloadComposer.Composer payload) {
    InjectorContract action =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withPayload(payload)
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
    return save(command, input);
  }

  private PayloadInjectorContractCreationResult save(Command command, PayloadUpdateInput input) {
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

  @Nested
  @DisplayName("A content change adds a version")
  class NewVersion {

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
      PayloadInjectorContractCreationResult result = save(first, input);

      // Assert
      assertThat(reloaded(result.payload().getId()).getVersion()).isEqualTo("2.0.0");
    }

    @Test
    @DisplayName("The new version gets its own output parsers; the previous one keeps its own")
    void given_outputParsersSentWithTheirIds_should_giveTheNewVersionItsOwn() {
      // Arrange
      Command first = localCommand();
      PayloadUpdateInput withParser = inputFrom(first);
      withParser.setContent("procdump -ma lsass.exe");
      OutputParserInput parser = OutputParserInputFixture.createDefaultOutputParseInput();
      parser.setContractOutputElements(
          Set.of(ContractOutputElementInputFixture.createDefaultContractOutputElementInputIPV6()));
      withParser.setOutputParsers(Set.of(parser));
      Command previous = reloaded(save(first, withParser).payload().getId());
      PayloadUpdateInput input = inputFrom(previous);
      input.setContent("rundll32 comsvcs.dll MiniDump 624 lsass.dmp full");
      input.setOutputParsers(
          previous.getOutputParsers().stream()
              .map(PayloadLocalVersionTest::inputWithIds)
              .collect(Collectors.toSet()));

      // Act
      PayloadInjectorContractCreationResult result = save(previous, input);

      // Assert
      Command added = reloaded(result.payload().getId());
      Set<String> previousIds = outputParserIds(reloaded(previous.getId()));
      assertThat(previousIds).hasSize(2);
      assertThat(outputParserIds(added)).hasSize(2).doesNotContainAnyElementsOf(previousIds);
    }

    @Test
    @DisplayName("Detection rules not sent, without the licence, are kept on the new version")
    void given_detectionRulesNotSent_should_keepThemOnTheNewVersion() {
      // Arrange
      Command first =
          persisted(
              payloadComposer
                  .forPayload(PayloadFixture.createDefaultCommand())
                  .withDetectionRemediation(
                      detectionRemediationComposer
                          .forDetectionRemediation(
                              DetectionRemediationFixture.createDefaultDetectionRemediation())
                          .withSecurityPlatform(
                              securityPlatformComposer.forSecurityPlatform(
                                  SecurityPlatformFixture.createDefault(
                                      "CrowdStrike Falcon", "EDR")))));
      PayloadUpdateInput input = inputFrom(first);
      input.setContent("procdump -ma lsass.exe");
      input.setDetectionRemediations(null);

      // Act
      PayloadInjectorContractCreationResult result = save(first, input);

      // Assert
      List<DetectionRemediation> kept =
          reloaded(result.payload().getId()).getDetectionRemediations();
      List<DetectionRemediation> previous = reloaded(first.getId()).getDetectionRemediations();
      assertThat(kept)
          .extracting(DetectionRemediation::getValues)
          .containsExactlyElementsOf(
              previous.stream().map(DetectionRemediation::getValues).toList());
      assertThat(kept)
          .extracting(DetectionRemediation::getId)
          .doesNotContainAnyElementsOf(previous.stream().map(DetectionRemediation::getId).toList());
    }
  }

  @Nested
  @DisplayName("A save that adds no version updates the payload in place")
  class InPlace {

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

    @Test
    @DisplayName("A collector payload stays one version, which its collector finds by external id")
    void given_collectorPayload_should_updateInPlace() {
      // Arrange
      Payload collected = PayloadFixture.createDefaultCommand();
      collected.setCollectorType(
          collectorTypeComposer
              .forCollectorType(CollectorTypeFixture.createDefaultCollectorType())
              .persist()
              .get());
      Command first = persisted(payloadComposer.forPayload(collected));

      // Act
      PayloadInjectorContractCreationResult result = save(first, "procdump -ma lsass.exe");

      // Assert
      assertThat(result.payload().getId()).isEqualTo(first.getId());
      Command saved = reloaded(first.getId());
      assertThat(saved.getContent()).isEqualTo("procdump -ma lsass.exe");
      assertThat(saved.getVersion()).isEqualTo(Payload.FIRST_VERSION);
      assertThat(payloadRepository.findAllByIdentityId(saved.getIdentity().getId())).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Deleting a version")
  class DeleteVersion {

    @Test
    @DisplayName("Deleting the latest version shows the highest version left")
    void given_latestVersionDeleted_should_showTheHighestVersionLeft() {
      // Arrange
      Command first = localCommand();
      String middleId = save(first, "procdump -ma lsass.exe").payload().getId();
      String latestId =
          save(reloaded(first.getId()), "rundll32 comsvcs.dll MiniDump 624 lsass.dmp full")
              .payload()
              .getId();

      // Act
      payloadService.delete(latestId);
      flush();

      // Assert
      assertThat(reloaded(middleId).isLatest()).isTrue();
      assertThat(reloaded(first.getId()).isLatest()).isFalse();
    }

    @Test
    @DisplayName("Deleting an older version keeps the latest one")
    void given_olderVersionDeleted_should_keepTheLatest() {
      // Arrange
      Command first = localCommand();
      String latestId = save(first, "procdump -ma lsass.exe").payload().getId();

      // Act
      payloadService.delete(first.getId());
      flush();

      // Assert
      assertThat(reloaded(latestId).isLatest()).isTrue();
    }
  }

  private static OutputParserInput inputWithIds(OutputParser parser) {
    OutputParserInput input = OutputParserInputFixture.createDefaultOutputParseInput();
    input.setId(parser.getId());
    input.setContractOutputElements(
        parser.getContractOutputElements().stream()
            .map(PayloadLocalVersionTest::inputWithId)
            .collect(Collectors.toSet()));
    return input;
  }

  private static ContractOutputElementInput inputWithId(ContractOutputElement element) {
    ContractOutputElementInput input =
        ContractOutputElementInputFixture.createDefaultContractOutputElementInputIPV6();
    input.setId(element.getId());
    return input;
  }

  private static Set<String> outputParserIds(Payload payload) {
    return payload.getOutputParsers().stream()
        .flatMap(
            parser ->
                Stream.concat(
                    Stream.of(parser.getId()),
                    parser.getContractOutputElements().stream().map(ContractOutputElement::getId)))
        .collect(Collectors.toSet());
  }
}
