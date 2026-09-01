package io.openaev.ocsf;

import static java.util.Spliterators.spliteratorUnknownSize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ocsf.parsing.OcsfFilter;
import io.openaev.ocsf.schema.v190.OcsfClassUid;
import io.openaev.ocsf.schema.v190.OcsfConverter;
import io.openaev.ocsf.schema.v190.classes.OcsfClassDetectionFinding;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

public class OcsfMapperTest {
  private ObjectMapper mapper = new ObjectMapper();
  private OcsfConverter converter = new OcsfConverter();

  private Stream<JsonNode> getTrace() throws URISyntaxException, IOException {
    String rawJson =
        new String(
            Objects.requireNonNull(
                    getClass().getResourceAsStream("/ocsf/prowler-security-finding.json"))
                .readAllBytes(),
            StandardCharsets.UTF_8);
    Iterator<JsonNode> elements = mapper.readTree(rawJson).elements();
    Spliterator<JsonNode> elementsSplit = spliteratorUnknownSize(elements, Spliterator.ORDERED);
    return StreamSupport.stream(elementsSplit, false);
  }

  @Test
  void test() throws URISyntaxException, IOException {
    OcsfFilter filter = new OcsfFilter();
    Stream<JsonNode> trace = getTrace();

    List<JsonNode> filtered =
        filter.getClassObjectsByUid(OcsfClassUid.DETECTION_FINDING, trace.toList());

    List<OcsfClassDetectionFinding> sf = new ArrayList<>();
    for (JsonNode node : filtered) {
      sf.add(converter.toOcsfClassDetectionFinding(node));
    }
  }
}
