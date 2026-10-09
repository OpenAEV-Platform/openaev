package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.openaev.database.model.*;
import io.openaev.rest.document.DocumentService;
import io.openaev.service.FileService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayloadContentHashServiceTest {

  @Mock private FileService fileService;
  @Mock private DocumentService documentService;
  @InjectMocks private PayloadContentHashService service;

  private static Command command() {
    Command command = new Command();
    command.setId(UUID.randomUUID().toString());
    command.setName("Whoami");
    command.setPlatforms(
        new Endpoint.PLATFORM_TYPE[] {
          Endpoint.PLATFORM_TYPE.Windows, Endpoint.PLATFORM_TYPE.Linux
        });
    command.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
    command.setExecutor("sh");
    command.setContent("whoami");
    command.setArguments(new ArrayList<>());
    return command;
  }

  private static AttackPattern attackPattern(String externalId) {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setId(UUID.randomUUID().toString());
    attackPattern.setExternalId(externalId);
    return attackPattern;
  }

  private static PayloadArgument argument(String key) {
    PayloadArgument argument = new PayloadArgument();
    argument.setType(PrimitiveType.Text);
    argument.setKey(key);
    argument.setDefaultValue("value");
    return argument;
  }

  @Nested
  @DisplayName("Canonical form (Appendix B)")
  class CanonicalForm {

    @Test
    void given_commandPayload_should_matchTestVector() throws Exception {
      // Arrange
      Command command = command();

      // Act
      String canonical =
          new JsonCanonicalizer(
                  service.canonicalTree(command, List.of(attackPattern("T1033"))).toString())
              .getEncodedString();

      // Assert
      assertThat(canonical)
          .isEqualTo(
              "{\"command_content\":\"whoami\",\"command_executor\":\"sh\","
                  + "\"payload_attack_patterns\":[\"T1033\"],"
                  + "\"payload_elevation_required\":false,"
                  + "\"payload_execution_arch\":\"ALL_ARCHITECTURES\","
                  + "\"payload_name\":\"Whoami\","
                  + "\"payload_platforms\":[\"Linux\",\"Windows\"],"
                  + "\"payload_type\":\"Command\"}");
    }

    @Test
    void given_platformFields_should_notChangeHash() {
      // Arrange
      Command first = command();
      Command second = command();
      second.setExternalId("external-id");
      second.setVersion("1.0.0");
      second.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
      second.setSource(Payload.PAYLOAD_SOURCE.FILIGRAN);
      second.setCreatedAt(Instant.EPOCH);
      second.setTenant(new Tenant(UUID.randomUUID().toString()));

      // Act & Assert
      assertThat(service.hash(first, List.of())).isEqualTo(service.hash(second, List.of()));
    }

    @Test
    void given_setsInAnotherOrder_should_keepHash() {
      // Arrange
      Command first = command();
      Command second = command();
      second.setPlatforms(
          new Endpoint.PLATFORM_TYPE[] {
            Endpoint.PLATFORM_TYPE.Linux, Endpoint.PLATFORM_TYPE.Windows
          });

      // Act & Assert
      assertThat(service.hash(first, List.of(attackPattern("T1033"), attackPattern("T1059"))))
          .isEqualTo(service.hash(second, List.of(attackPattern("T1059"), attackPattern("T1033"))));
    }

    @Test
    void given_argumentsInAnotherOrder_should_changeHash() {
      // Arrange
      Command first = command();
      first.setArguments(new ArrayList<>(List.of(argument("a"), argument("b"))));
      Command second = command();
      second.setArguments(new ArrayList<>(List.of(argument("b"), argument("a"))));

      // Act & Assert
      assertThat(service.hash(first, List.of())).isNotEqualTo(service.hash(second, List.of()));
    }

    @Test
    void given_emptyDescription_should_hashLikeNoDescription() {
      // Arrange
      Command first = command();
      Command second = command();
      second.setDescription("");

      // Act & Assert
      assertThat(service.hash(first, List.of())).isEqualTo(service.hash(second, List.of()));
    }
  }

  @Nested
  @DisplayName("Attached files")
  class AttachedFiles {

    private Executable executable(String target) {
      Document document = new Document();
      document.setId(UUID.randomUUID().toString());
      document.setTarget(target);
      Executable executable = new Executable();
      executable.setName("Tool");
      executable.setExecutableFile(document);
      return executable;
    }

    @Test
    void given_differentFileBytes_should_changeHash() {
      // Arrange
      when(fileService.getFile(any(), any()))
          .thenReturn(Optional.of(new ByteArrayInputStream("v1".getBytes(StandardCharsets.UTF_8))))
          .thenReturn(Optional.of(new ByteArrayInputStream("v2".getBytes(StandardCharsets.UTF_8))));

      // Act & Assert
      assertThat(service.hash(executable("a.exe"), List.of()))
          .isNotEqualTo(service.hash(executable("b.exe"), List.of()));
    }

    @Test
    void given_unreadableFile_should_giveNoHash() {
      // Arrange
      when(fileService.getFile(any(), any())).thenReturn(Optional.empty());

      // Act & Assert
      assertThat(service.hash(executable("missing.exe"), List.of())).isNull();
    }

    @Test
    void given_missingArgumentDocument_should_giveNoHash() {
      // Arrange
      Command command = command();
      PayloadArgument argument = argument("file");
      argument.setType(PrimitiveType.Document);
      command.setArguments(new ArrayList<>(List.of(argument)));
      when(documentService.document(anyString()))
          .thenThrow(new io.openaev.rest.exception.ElementNotFoundException("Document not found"));

      // Act & Assert
      assertThat(service.hash(command, List.of())).isNull();
    }
  }
}
