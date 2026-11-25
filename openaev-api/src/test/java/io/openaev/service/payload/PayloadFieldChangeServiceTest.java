package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.service.FileService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayloadFieldChangeServiceTest {

  @Mock private FileService fileService;
  @Mock private DocumentService documentService;
  @Mock private InjectorContractRepository injectorContractRepository;

  private PayloadContentHashService hashService;
  private PayloadFieldChangeService service;

  @BeforeEach
  void setUp() {
    hashService = new PayloadContentHashService(fileService, documentService);
    service = new PayloadFieldChangeService(hashService, injectorContractRepository);
  }

  private static Command command() {
    Command command = new Command();
    command.setId(UUID.randomUUID().toString());
    command.setName("Whoami");
    command.setDescription("Prints the current user");
    command.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux});
    command.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
    command.setExecutor("sh");
    command.setContent("whoami");
    command.setExpectations(new BaseInjectExpectation.EXPECTATION_TYPE[] {});
    command.setArguments(new ArrayList<>());
    return command;
  }

  private static AttackPattern attackPattern(String externalId, String name) {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setId(UUID.randomUUID().toString());
    attackPattern.setExternalId(externalId);
    attackPattern.setName(name);
    return attackPattern;
  }

  private static List<String> fields(ArrayNode changes) {
    return StreamSupport.stream(changes.spliterator(), false)
        .map(change -> change.get("field").asText())
        .toList();
  }

  private static JsonNode change(ArrayNode changes, String field) {
    return StreamSupport.stream(changes.spliterator(), false)
        .filter(change -> field.equals(change.get("field").asText()))
        .findFirst()
        .orElseThrow();
  }

  @Nested
  @DisplayName("Fields that make a new version")
  class FieldsThatMakeAVersion {

    @Test
    @DisplayName("A name change alone is a change, with its text before and after")
    void given_nameOnlyChange_should_listName() {
      // Arrange
      Command before = command();
      Command after = command();
      after.setName("Whoami as root");

      // Act
      ArrayNode changes = service.between(before, List.of(), after, List.of());

      // Assert
      assertThat(fields(changes)).containsExactly("payload_name");
      assertThat(change(changes, "payload_name").get("before").asText()).isEqualTo("Whoami");
      assertThat(change(changes, "payload_name").get("after").asText()).isEqualTo("Whoami as root");
    }

    @Test
    @DisplayName("A description change alone is a change")
    void given_descriptionOnlyChange_should_listDescription() {
      // Arrange
      Command before = command();
      Command after = command();
      after.setDescription("Prints who runs the agent");

      // Act
      ArrayNode changes = service.between(before, List.of(), after, List.of());

      // Assert
      assertThat(fields(changes)).containsExactly("payload_description");
    }

    @Test
    @DisplayName("Attack patterns show by external id and name")
    void given_attackPatternChange_should_listItByIdAndName() {
      // Arrange
      Command before = command();
      Command after = command();

      // Act
      ArrayNode changes =
          service.between(
              before,
              List.of(attackPattern("T1033", "System Owner/User Discovery")),
              after,
              List.of(attackPattern("T1059", "Command and Scripting Interpreter")));

      // Assert
      assertThat(fields(changes)).containsExactly("payload_attack_patterns");
      assertThat(change(changes, "payload_attack_patterns").get("before").get(0).asText())
          .isEqualTo("T1033 System Owner/User Discovery");
      assertThat(change(changes, "payload_attack_patterns").get("after").get(0).asText())
          .isEqualTo("T1059 Command and Scripting Interpreter");
    }

    @Test
    @DisplayName("Expectations are a change")
    void given_expectationChange_should_listExpectations() {
      // Arrange
      Command before = command();
      Command after = command();
      after.setExpectations(
          new BaseInjectExpectation.EXPECTATION_TYPE[] {
            BaseInjectExpectation.EXPECTATION_TYPE.DETECTION
          });

      // Act
      ArrayNode changes = service.between(before, List.of(), after, List.of());

      // Assert
      assertThat(fields(changes)).containsExactly("payload_expectations");
      assertThat(change(changes, "payload_expectations").get("before").isNull()).isTrue();
    }

    @Test
    @DisplayName("Elevation required is a change")
    void given_elevationChange_should_listElevation() {
      // Arrange
      Command before = command();
      Command after = command();
      after.setElevationRequired(true);

      // Act
      ArrayNode changes = service.between(before, List.of(), after, List.of());

      // Assert
      assertThat(fields(changes)).containsExactly("payload_elevation_required");
    }

    @Test
    @DisplayName("Tags are not part of a version: no change")
    void given_sameContent_should_listNothing() {
      // Act
      ArrayNode changes = service.between(command(), List.of(), command(), List.of());

      // Assert
      assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("The compared fields are exactly those of the content hash")
    void given_anyChange_should_followTheContentHash() {
      // Arrange
      Command before = command();
      Command after = command();
      after.setName("Other");
      after.setContent("id");
      List<AttackPattern> patterns = List.of(attackPattern("T1033", "Discovery"));

      // Act
      ArrayNode changes = service.between(null, null, after, patterns);

      // Assert
      List<String> canonical = new ArrayList<>();
      hashService.canonicalTree(after, patterns).fieldNames().forEachRemaining(canonical::add);
      assertThat(fields(changes)).containsExactlyInAnyOrderElementsOf(canonical);
      assertThat(fields(service.between(before, patterns, after, patterns)))
          .containsExactlyInAnyOrder("payload_name", "command_content");
    }
  }

  @Nested
  @DisplayName("Files")
  class Files {

    @Test
    @DisplayName("A file shows by its name")
    void given_fileDropChange_should_showFileNames() {
      // Arrange
      FileDrop before = new FileDrop();
      before.setId(UUID.randomUUID().toString());
      before.setName("Drop");
      before.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
      before.setFileDropFile(document("old.txt"));
      FileDrop after = new FileDrop();
      after.setId(UUID.randomUUID().toString());
      after.setName("Drop");
      after.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
      after.setFileDropFile(document("new.txt"));

      // Act (the files cannot be read here: names are compared)
      ArrayNode changes = service.between(before, List.of(), after, List.of());

      // Assert
      assertThat(fields(changes)).containsExactly("file_drop_file");
      assertThat(change(changes, "file_drop_file").get("before").asText()).isEqualTo("old.txt");
      assertThat(change(changes, "file_drop_file").get("after").asText()).isEqualTo("new.txt");
    }

    private Document document(String name) {
      Document document = new Document();
      document.setId(UUID.randomUUID().toString());
      document.setName(name);
      document.setTarget(UUID.randomUUID().toString());
      return document;
    }
  }
}
