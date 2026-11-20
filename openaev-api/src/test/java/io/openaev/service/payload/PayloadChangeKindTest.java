package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.openaev.database.model.*;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.hash.Hashed.Change;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.service.hash.CanonicalContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Kind of change of a new payload content")
class PayloadChangeKindTest {

  @Mock private DocumentService documentService;
  @Mock private PayloadRepository payloadRepository;
  @Mock private InjectorContractRepository injectorContractRepository;
  private PayloadChangeKind changeKind;

  @BeforeEach
  void setUp() {
    changeKind =
        new PayloadChangeKind(
            new PayloadContentHashService(
                new CanonicalContent(documentService),
                payloadRepository,
                injectorContractRepository));
  }

  private static Command command() {
    Command command = new Command();
    command.setId(UUID.randomUUID().toString());
    command.setName("Dump LSASS");
    command.setDescription("Reads the LSASS memory");
    command.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows});
    command.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
    command.setExecutor("psh");
    command.setContent("rundll32 comsvcs.dll MiniDump");
    command.setArguments(new ArrayList<>());
    return command;
  }

  private static AttackPattern attackPattern(String externalId) {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setId(UUID.randomUUID().toString());
    attackPattern.setExternalId(externalId);
    return attackPattern;
  }

  private static DetectionRemediation remediation(String values) {
    DetectionRemediation remediation = new DetectionRemediation();
    remediation.setValues(values);
    return remediation;
  }

  private Optional<Change> changeTo(Payload after) {
    return changeKind.of(command(), after, List.of());
  }

  @Test
  @DisplayName("Nothing hashed changed: no new version")
  void given_sameContent_should_beEmpty() {
    // Arrange
    Command after = command();
    after.setStatus(Payload.PAYLOAD_STATUS.DEPRECATED);

    // Act & Assert
    assertThat(changeTo(after)).isEmpty();
  }

  @Nested
  @DisplayName("Patch: text and descriptive metadata")
  class Patch {

    @Test
    void given_nameChanged_should_bePatch() {
      // Arrange
      Command after = command();
      after.setName("Dump LSASS memory");

      // Act & Assert
      assertThat(changeTo(after)).contains(Change.PATCH);
    }

    @Test
    void given_descriptionChanged_should_bePatch() {
      // Arrange
      Command after = command();
      after.setDescription("Reads the LSASS memory to a file");

      // Act & Assert
      assertThat(changeTo(after)).contains(Change.PATCH);
    }

    @Test
    void given_techniqueAdded_should_bePatch() {
      // Arrange
      Command before = command();
      InjectorContract contract = new InjectorContract();
      contract.setAttackPatterns(new ArrayList<>(List.of(attackPattern("T1003"))));
      when(injectorContractRepository.findInjectorContractByPayload(before))
          .thenReturn(Optional.of(contract));

      // Act
      Optional<Change> change =
          changeKind.of(
              before, command(), List.of(attackPattern("T1003"), attackPattern("T1003.001")));

      // Assert
      assertThat(change).contains(Change.PATCH);
    }
  }

  @Nested
  @DisplayName("Minor: what it runs or checks")
  class Minor {

    @Test
    void given_commandChanged_should_beMinor() {
      // Arrange
      Command after = command();
      after.setContent("procdump -ma lsass.exe");

      // Act & Assert
      assertThat(changeTo(after)).contains(Change.MINOR);
    }

    @Test
    void given_objectEditedInList_should_beMinor() {
      // Arrange
      Command before = command();
      before.setDetectionRemediations(List.of(remediation("rule one")));
      Command after = command();
      after.setDetectionRemediations(List.of(remediation("rule one, tuned")));

      // Act & Assert
      assertThat(changeKind.of(before, after, List.of())).contains(Change.MINOR);
    }
  }

  @Nested
  @DisplayName("Major: what it needs or offers to run, and anything removed")
  class Major {

    @Test
    void given_argumentAdded_should_beMajor() {
      // Arrange
      Command after = command();
      PayloadArgument argument = new PayloadArgument();
      argument.setKey("output");
      argument.setType(PrimitiveType.Text);
      argument.setDefaultValue("lsass.dmp");
      after.setArguments(new ArrayList<>(List.of(argument)));

      // Act & Assert
      assertThat(changeTo(after)).contains(Change.MAJOR);
    }

    @Test
    void given_platformAdded_should_beMajor() {
      // Arrange
      Command after = command();
      after.setPlatforms(
          new Endpoint.PLATFORM_TYPE[] {
            Endpoint.PLATFORM_TYPE.Windows, Endpoint.PLATFORM_TYPE.Linux
          });

      // Act & Assert
      assertThat(changeTo(after)).contains(Change.MAJOR);
    }

    @Test
    void given_architectureChanged_should_beMajor() {
      // Arrange
      Command after = command();
      after.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.x86_64);

      // Act & Assert
      assertThat(changeTo(after)).contains(Change.MAJOR);
    }

    @Test
    void given_executorChanged_should_beMajor() {
      // Arrange
      Command after = command();
      after.setExecutor("cmd");

      // Act & Assert
      assertThat(changeTo(after)).contains(Change.MAJOR);
    }

    @Test
    void given_prerequisiteAdded_should_beMajor() {
      // Arrange
      Command after = command();
      PayloadPrerequisite prerequisite = new PayloadPrerequisite();
      prerequisite.setExecutor("psh");
      prerequisite.setGetCommand("Install-Module procdump");
      after.setPrerequisites(new ArrayList<>(List.of(prerequisite)));

      // Act & Assert
      assertThat(changeTo(after)).contains(Change.MAJOR);
    }

    @Test
    void given_typeChanged_should_beMajor() {
      // Arrange
      DnsResolution after = new DnsResolution();
      after.setName("Dump LSASS");
      after.setDescription("Reads the LSASS memory");
      after.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows});
      after.setHostname("lsass.local");

      // Act & Assert
      assertThat(changeTo(after)).contains(Change.MAJOR);
    }

    @Test
    void given_valueRemovedFromList_should_beMajor() {
      // Arrange
      Command before = command();
      before.setExpectations(
          new EXPECTATION_TYPE[] {EXPECTATION_TYPE.PREVENTION, EXPECTATION_TYPE.DETECTION});
      Command after = command();
      after.setExpectations(new EXPECTATION_TYPE[] {EXPECTATION_TYPE.PREVENTION});

      // Act & Assert
      assertThat(changeKind.of(before, after, List.of())).contains(Change.MAJOR);
    }

    @Test
    void given_objectRemovedFromList_should_beMajor() {
      // Arrange
      Command before = command();
      before.setDetectionRemediations(List.of(remediation("rule one"), remediation("rule two")));
      Command after = command();
      after.setDetectionRemediations(List.of(remediation("rule one")));

      // Act & Assert
      assertThat(changeKind.of(before, after, List.of())).contains(Change.MAJOR);
    }

    @Test
    void given_techniqueRemoved_should_beMajor() {
      // Arrange
      Command before = command();
      InjectorContract contract = new InjectorContract();
      contract.setAttackPatterns(new ArrayList<>(List.of(attackPattern("T1003"))));
      when(injectorContractRepository.findInjectorContractByPayload(before))
          .thenReturn(Optional.of(contract));

      // Act & Assert
      assertThat(changeKind.of(before, command(), List.of())).contains(Change.MAJOR);
    }
  }

  @Nested
  @DisplayName("Attached file")
  class AttachedFile {

    private static Document document() {
      Document document = new Document();
      document.setId(UUID.randomUUID().toString());
      document.setTarget("procdump.exe");
      return document;
    }

    private static Executable executable(Document file) {
      Executable executable = new Executable();
      executable.setName("Procdump");
      executable.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows});
      executable.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.x86_64);
      executable.setExecutableFile(file);
      return executable;
    }

    @Test
    void given_otherFileContent_should_beMinor() {
      // Arrange
      when(documentService.contentHash(any()))
          .thenReturn(Optional.of("first-hash"))
          .thenReturn(Optional.of("second-hash"));

      // Act & Assert
      assertThat(changeKind.of(executable(document()), executable(document()), List.of()))
          .contains(Change.MINOR);
    }

    @Test
    void given_unreadableSameFile_should_compareByIdAndBeEmpty() {
      // Arrange
      Document file = document();
      when(documentService.contentHash(any())).thenReturn(Optional.empty());

      // Act & Assert
      assertThat(changeKind.of(executable(file), executable(file), List.of())).isEmpty();
    }

    @Test
    void given_unreadableOtherFile_should_compareByIdAndBeMinor() {
      // Arrange
      when(documentService.contentHash(any())).thenReturn(Optional.empty());

      // Act & Assert
      assertThat(changeKind.of(executable(document()), executable(document()), List.of()))
          .contains(Change.MINOR);
    }
  }
}
