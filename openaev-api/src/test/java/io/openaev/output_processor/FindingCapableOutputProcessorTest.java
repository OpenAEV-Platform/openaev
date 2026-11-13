package io.openaev.output_processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.finding.identity.LocationIdentity;
import io.openaev.finding.identity.LocationIdentity.AssetLocation;
import io.openaev.finding.identity.LocationIdentity.UnknownLocation;
import io.openaev.rest.finding.FindingService;
import io.openaev.service.InjectExpectationService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("FindingCapableOutputProcessor.toFindingLocations")
class FindingCapableOutputProcessorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Any finding processor: the reading of asset_id is the base class's
  private final CVEOutputProcessor processor =
      new CVEOutputProcessor(mock(FindingService.class), mock(InjectExpectationService.class));

  static Stream<Arguments> outputShapes() {
    return Stream.of(
        Arguments.of("no asset_id", "{}", List.of(UnknownLocation.INSTANCE)),
        Arguments.of(
            "an id as text", "{\"asset_id\": \"a1\"}", List.of(new AssetLocation("a1"))),
        Arguments.of(
            "an array of ids",
            "{\"asset_id\": [\"a1\", \"a2\"]}",
            List.of(new AssetLocation("a1"), new AssetLocation("a2"))),
        Arguments.of(
            "a blank id (nuclei, host outside its asset map)",
            "{\"asset_id\": [\"\"]}",
            List.of(UnknownLocation.INSTANCE)),
        Arguments.of(
            "a JSON null (nmap in manual mode)",
            "{\"asset_id\": null}",
            List.of(UnknownLocation.INSTANCE)),
        Arguments.of(
            "the text null", "{\"asset_id\": \"null\"}", List.of(UnknownLocation.INSTANCE)));
  }

  @Nested
  @DisplayName("When reading the location of a finding")
  class WhenReadingTheLocation {

    @ParameterizedTest(name = "given {0}")
    @MethodSource("io.openaev.output_processor.FindingCapableOutputProcessorTest#outputShapes")
    @DisplayName("should locate the finding on its assets, or nowhere known")
    void given_anOutputShape_should_locateTheFinding(
        String shape, String output, List<LocationIdentity> expected) throws Exception {
      // -- ACT --
      List<LocationIdentity> locations = processor.toFindingLocations(MAPPER.readTree(output));

      // -- ASSERT --
      assertThat(locations).isEqualTo(expected);
    }
  }
}
