package io.openaev.ocsf;

import static java.util.Spliterators.spliteratorUnknownSize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.openaev.ocsf.schema.v190.OcsfClassUid;
import io.openaev.ocsf.schema.v190.OcsfConverter;
import io.openaev.ocsf.schema.v190.OcsfFilter;
import io.openaev.ocsf.schema.v190.classes.OcsfClassDetectionFinding;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.jupiter.api.Test;

public class OcsfMapperTest {
  private ObjectMapper mapper = new ObjectMapper();
  private OcsfConverter converter = new OcsfConverter();

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
  void test() throws URISyntaxException, IOException {
    OcsfFilter filter = new OcsfFilter();
    ArrayNode trace = getTrace();

    List<OcsfClassDetectionFinding> sf = filter.filterOcsfClassDetectionFindings(trace);
  }
}
