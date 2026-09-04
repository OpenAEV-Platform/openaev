package io.openaev.ocsf;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.openaev.ocsf.schema.v190.OcsfClassUid;
import io.openaev.ocsf.schema.v190.OcsfFilter;
import io.openaev.ocsf.schema.v190.classes.OcsfClassDetectionFinding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OcsfFilterTest {
  private ObjectMapper mapper = new ObjectMapper();

  private ArrayNode getTrace() throws IOException {
    String rawJson =
        new String(
            Objects.requireNonNull(
                    getClass().getResourceAsStream("/ocsf/prowler-security-finding.json"))
                .readAllBytes(),
            StandardCharsets.UTF_8);
    JsonNode rawNode = mapper.readTree(rawJson);
    return (ArrayNode) rawNode;
  }

  @Test
  @DisplayName(
      "Given detection findings from prowler output, then the parser correctly creates objects from it")
  void given_detectionFindingsFromProwlerOutput_then_parserCorrectlyCreatesObjectsFromIt()
      throws IOException {
    OcsfFilter filter = new OcsfFilter();
    ArrayNode trace = getTrace();

    List<OcsfClassDetectionFinding> sf = filter.filterOcsfClassDetectionFindings(trace);

    assertThat(sf)
        .hasSize(3)
        .allSatisfy(
            finding ->
                assertThat(finding)
                    .satisfies(
                        f -> assertThat(f.getMetadataField().getVersionField()).isEqualTo("1.5.0"))
                    .satisfies(
                        f ->
                            assertThat(f.getClassUidField().getValue().toString())
                                .isEqualTo(OcsfClassUid.DETECTION_FINDING.getValue())));
  }
}
