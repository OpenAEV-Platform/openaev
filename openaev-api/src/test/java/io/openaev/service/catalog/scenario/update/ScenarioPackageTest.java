package io.openaev.service.catalog.scenario.update;

import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.archive;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.exception.BadRequestException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Scenario package")
class ScenarioPackageTest {

  static Stream<Arguments> archivesWithoutScenario() {
    return Stream.of(
        Arguments.of("a folder", archive("scenario/", new byte[0])),
        Arguments.of("a file other than JSON", archive("readme.txt", "{}".getBytes(UTF_8))),
        Arguments.of("the JSON file of a payload", archive("nmap.json", payloadFile(PAYLOAD_ID))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("archivesWithoutScenario")
  @DisplayName("refuses an archive holding no scenario")
  void given_archiveWithoutScenario_should_refuse(String holding, byte[] archive) {
    // Act & Assert
    assertThatThrownBy(() -> ScenarioPackage.read(archive, new ObjectMapper()))
        .isInstanceOf(BadRequestException.class);
  }
}
