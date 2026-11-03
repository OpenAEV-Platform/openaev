package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PrimitiveType;
import io.openaev.rest.document.DocumentService;
import io.openaev.service.FileService;
import io.openaev.service.payload.PayloadVersionNumbering.Change;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Version numbers of local payloads (RFC 0005 4.1)")
class PayloadVersionNumberingTest {

  @Mock private FileService fileService;
  @Mock private DocumentService documentService;
  private PayloadVersionNumbering numbering;

  @BeforeEach
  void setUp() {
    numbering =
        new PayloadVersionNumbering(new PayloadContentHashService(fileService, documentService));
  }

  private static Command command() {
    Command command = new Command();
    command.setName("Dump LSASS");
    command.setDescription("Reads the LSASS memory");
    command.setExecutor("psh");
    command.setContent("rundll32 comsvcs.dll MiniDump");
    command.setPlatforms(new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows});
    command.setArguments(new ArrayList<>());
    return command;
  }

  private Change changeTo(Command after) {
    return numbering.change(command(), List.of(), after, List.of()).orElseThrow();
  }

  @Nested
  @DisplayName("Kind of change")
  class Kind {

    @Test
    void given_sameContent_should_createNoVersion() {
      assertThat(numbering.change(command(), List.of(), command(), List.of())).isEmpty();
    }

    @Test
    void given_textOnly_should_bePatch() {
      Command after = command();
      after.setDescription("Reads the LSASS memory to a file");
      assertThat(changeTo(after)).isEqualTo(Change.PATCH);
    }

    @Test
    void given_commandChanged_should_beMinor() {
      Command after = command();
      after.setContent("procdump -ma lsass.exe");
      assertThat(changeTo(after)).isEqualTo(Change.MINOR);
    }

    @Test
    void given_argumentsChanged_should_beMajor() {
      Command after = command();
      PayloadArgument argument = new PayloadArgument();
      argument.setKey("output");
      argument.setType(PrimitiveType.Text);
      argument.setDefaultValue("C:\\\\temp\\\\lsass.dmp");
      after.setArguments(new ArrayList<>(List.of(argument)));
      assertThat(changeTo(after)).isEqualTo(Change.MAJOR);
    }

    @Test
    void given_executorChanged_should_beMajor() {
      Command after = command();
      after.setExecutor("cmd");
      assertThat(changeTo(after)).isEqualTo(Change.MAJOR);
    }

    @Test
    void given_platformsChanged_should_beMajor() {
      Command after = command();
      after.setPlatforms(
          new Endpoint.PLATFORM_TYPE[] {
            Endpoint.PLATFORM_TYPE.Windows, Endpoint.PLATFORM_TYPE.Linux
          });
      assertThat(changeTo(after)).isEqualTo(Change.MAJOR);
    }
  }

  @Nested
  @DisplayName("Next number")
  class Bump {

    @Test
    void given_eachKind_should_bumpItsPart() {
      assertThat(PayloadVersionNumbering.bump("1.2.3", Change.MAJOR)).isEqualTo("2.0.0");
      assertThat(PayloadVersionNumbering.bump("1.2.3", Change.MINOR)).isEqualTo("1.3.0");
      assertThat(PayloadVersionNumbering.bump("1.2.3", Change.PATCH)).isEqualTo("1.2.4");
    }
  }
}
